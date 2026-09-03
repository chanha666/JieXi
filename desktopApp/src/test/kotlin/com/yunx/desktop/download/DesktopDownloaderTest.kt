package com.yunx.desktop.download

import com.yunx.app.data.network.model.DownloadLink
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

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
