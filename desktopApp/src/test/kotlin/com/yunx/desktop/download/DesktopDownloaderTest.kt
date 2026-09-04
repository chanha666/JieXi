package com.yunx.desktop.download

import com.yunx.app.data.network.model.DownloadLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopDownloaderTest {
    @Test
    fun selectsUsefulAdaptiveConcurrency() {
        val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
        assertEquals(1, downloader.optimalThreadCount(1024 * 1024, 64))
        assertEquals(8, downloader.optimalThreadCount(32L * 1024 * 1024, 64))
        assertEquals(32, downloader.optimalThreadCount(2L * 1024 * 1024 * 1024, 32))
        assertEquals(64, downloader.optimalThreadCount(2L * 1024 * 1024 * 1024, 64))
    }

    @Test
    fun downloadsAndMergesRangeChunks() = runBlocking {
        val content = ByteArray(512 * 1024) { (it % 251).toByte() }
        val server = rangeServer(content)
        val directory = Files.createTempDirectory("yunx-desktop-range").toFile()
        try {
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val file = downloader.download(
                DownloadLink("1", "range.bin", server.url("/file").toString(), content.size.toLong()),
                emptyMap(), directory, 8
            ) { }
            assertArrayEquals(content, file.readBytes())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun fallsBackWhenServerIgnoresRange() = runBlocking {
        val content = ByteArray(128 * 1024) { (it % 199).toByte() }
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setResponseCode(200).setBody(Buffer().write(content))
            }
            start()
        }
        val directory = Files.createTempDirectory("yunx-desktop-fallback").toFile()
        try {
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val file = downloader.download(
                DownloadLink("2", "fallback.bin", server.url("/file").toString(), content.size.toLong()),
                emptyMap(), directory, 4
            ) { }
            assertEquals(content.size.toLong(), file.length())
            assertArrayEquals(content, file.readBytes())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentSameNameDownloadsUseIsolatedWorkFilesAndDistinctOutputs() = runBlocking {
        val first = ByteArray(512 * 1024) { 0x31 }
        val second = ByteArray(512 * 1024) { 0x62 }
        val requestsReady = CountDownLatch(2)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requestsReady.countDown()
                    check(requestsReady.await(5, TimeUnit.SECONDS)) { "两项下载未并发开始" }
                    val content = if (request.path == "/first") first else second
                    return MockResponse().setResponseCode(200).setBody(Buffer().write(content))
                }
            }
            start()
        }
        val directory = Files.createTempDirectory("yunx-desktop-same-name").toFile()
        try {
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val outputs = coroutineScope {
                listOf(
                    async {
                        downloader.download(
                            DownloadLink("same-1", "same.bin", server.url("/first").toString(), first.size.toLong()),
                            emptyMap(), directory, 1, taskId = 101L
                        ) { }
                    },
                    async {
                        downloader.download(
                            DownloadLink("same-2", "same.bin", server.url("/second").toString(), second.size.toLong()),
                            emptyMap(), directory, 1, taskId = 202L
                        ) { }
                    }
                ).awaitAll()
            }
            assertEquals(2, outputs.map { it.canonicalPath }.distinct().size)
            val payloads = outputs.map { it.readBytes() }
            check(payloads.any { it.contentEquals(first) })
            check(payloads.any { it.contentEquals(second) })
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun completedMoveNeverOverwritesAnExistingFile() {
        val directory = Files.createTempDirectory("yunx-desktop-no-overwrite").toFile()
        try {
            val existing = directory.resolve("video.mp4").apply { writeText("existing") }
            val source = directory.resolve(".source.partial").apply { writeText("new") }
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)

            val result = downloader.moveCompletedWithoutOverwrite(source, directory, "video.mp4")

            assertEquals("existing", existing.readText())
            assertEquals("video (1).mp4", result.name)
            assertEquals("new", result.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun discardRemovesOnlyTheSelectedTasksPrivatePartials() {
        val directory = Files.createTempDirectory("yunx-desktop-discard").toFile()
        try {
            val selectedTemp = directory.resolve(".same.bin.65.yunx-downloading").apply { writeText("partial") }
            val selectedParts = directory.resolve(".same.bin.65.yunx-parts").apply { mkdirs() }
            selectedParts.resolve("part-0").writeText("partial")
            val otherTemp = directory.resolve(".same.bin.ca.yunx-downloading").apply { writeText("keep") }
            val completed = directory.resolve("same.bin").apply { writeText("keep") }
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)

            downloader.discard(101L, directory, "same.bin")

            check(!selectedTemp.exists())
            check(!selectedParts.exists())
            check(otherTemp.exists())
            check(completed.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rollbackRestoresExactHiddenFileAndResumeDoesNotRedownload() = runBlocking {
        val content = ByteArray(256 * 1024) { (it % 181).toByte() }
        val server = rangeServer(content)
        val directory = Files.createTempDirectory("yunx-desktop-rollback").toFile()
        val taskId = 301L
        try {
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val link = DownloadLink("rollback", "resume.bin", server.url("/file").toString(), content.size.toLong())

            val first = downloader.download(link, emptyMap(), directory, 1, taskId = taskId) { }
            assertTrue(first.exists())
            assertTrue(downloader.rollbackProvisional(taskId))

            val hidden = directory.resolve(".resume.bin.${java.lang.Long.toUnsignedString(taskId, 16)}.yunx-downloading")
            assertFalse(first.exists())
            assertTrue(hidden.exists())
            assertArrayEquals(content, hidden.readBytes())
            assertEquals(1, server.requestCount)

            val resumed = downloader.download(link, emptyMap(), directory, 1, taskId = taskId) { }
            assertEquals(1, server.requestCount)
            assertArrayEquals(content, resumed.readBytes())
            assertTrue(downloader.commit(taskId, resumed))
            assertFalse(hidden.exists())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun removeDeletesOnlyItsExactUncommittedOutputAndNeverExistingFiles() = runBlocking {
        val content = ByteArray(64 * 1024) { 0x55.toByte() }
        val server = rangeServer(content)
        val directory = Files.createTempDirectory("yunx-desktop-provisional-discard").toFile()
        val taskId = 404L
        try {
            val existing = directory.resolve("same.bin").apply { writeText("existing") }
            val unrelated = directory.resolve("same (2).bin").apply { writeText("unrelated") }
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val output = downloader.download(
                DownloadLink("discard", "same.bin", server.url("/file").toString(), content.size.toLong()),
                emptyMap(), directory, 1, taskId = taskId
            ) { }

            assertEquals("same (1).bin", output.name)
            downloader.beginDiscard(taskId)
            assertFalse(downloader.commit(taskId, output))
            downloader.discard(taskId, directory, "same.bin")

            assertFalse(output.exists())
            assertEquals("existing", existing.readText())
            assertEquals("unrelated", unrelated.readText())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun committedOutputIsNotRemovedByTaskDiscard() = runBlocking {
        val content = ByteArray(32 * 1024) { 0x2a.toByte() }
        val server = rangeServer(content)
        val directory = Files.createTempDirectory("yunx-desktop-committed-discard").toFile()
        val taskId = 505L
        try {
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)
            val output = downloader.download(
                DownloadLink("commit", "kept.bin", server.url("/file").toString(), content.size.toLong()),
                emptyMap(), directory, 1, taskId = taskId
            ) { }

            assertTrue(downloader.commit(taskId, output))
            downloader.beginDiscard(taskId)
            downloader.discard(taskId, directory, "kept.bin")

            assertTrue(output.exists())
            assertArrayEquals(content, output.readBytes())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun mergeStopsAtCancellationCheckpointBetweenParts() {
        val directory = Files.createTempDirectory("yunx-desktop-merge-cancel").toFile()
        try {
            val first = directory.resolve("part-0").apply { writeBytes(ByteArray(4096) { 1 }) }
            val second = directory.resolve("part-1").apply { writeBytes(ByteArray(4096) { 2 }) }
            val destination = directory.resolve("merged.partial")
            val cancellation = Job()
            val parts = object : AbstractList<File>() {
                override val size: Int = 2
                override fun get(index: Int): File {
                    if (index == 1) cancellation.cancel()
                    return if (index == 0) first else second
                }
            }
            val downloader = DesktopDownloader({ OkHttpClient() }, allowHttpForTesting = true)

            val failure = runCatching {
                runBlocking(cancellation) { downloader.mergeParts(parts, destination) }
            }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertEquals(first.length(), destination.length())
            assertArrayEquals(first.readBytes(), destination.readBytes())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun rangeServer(content: ByteArray): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                    ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(content))
                val match = Regex("bytes=(\\d+)-(\\d*)").matchEntire(range)
                    ?: return MockResponse().setResponseCode(416)
                val start = match.groupValues[1].toInt()
                val requestedEnd = match.groupValues[2].toIntOrNull() ?: content.lastIndex
                val end = requestedEnd.coerceAtMost(content.lastIndex)
                if (start > end) return MockResponse().setResponseCode(416)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${content.size}")
                    .setBody(Buffer().write(content, start, end - start + 1))
            }
        }
        start()
    }
}
