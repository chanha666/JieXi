package com.yunx.desktop.download

import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.ChunkResult
import com.yunx.app.data.download.HlsDownloader
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.model.DownloadLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import kotlin.math.min

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val bytesPerSecond: Long,
    val message: String
) {
    val fraction: Float get() = if (total > 0) (downloaded.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
}

class DesktopDownloader(
    clientProvider: () -> OkHttpClient = { HttpClients.downloadClient() },
    private val allowHttpForTesting: Boolean = false
) {
    private val chunks = ChunkDownloader(clientProvider)

    suspend fun download(
        link: DownloadLink,
        headers: Map<String, String>,
        targetDirectory: File,
        threadCount: Int,
        taskId: Long = System.nanoTime(),
        speedLimitBytes: Long = 0L,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(link.downloadUrl.startsWith("https://") ||
            (allowHttpForTesting && link.downloadUrl.startsWith("http://"))) { "拒绝非 HTTPS 下载地址" }
        targetDirectory.mkdirs()
        require(targetDirectory.isDirectory && targetDirectory.canWrite()) { "下载目录不可写" }

        val safeName = safeFileName(link.filename.ifBlank { "download.bin" })
        val destination = uniqueDestination(targetDirectory, safeName)
        val temporary = File(targetDirectory, ".${destination.name}.yunx-downloading")
        if (link.isHls) {
            val meter = ProgressMeter(link.size, onProgress, speedLimitBytes = speedLimitBytes)
            val success = HlsDownloader.download(link.downloadUrl, headers, temporary) { meter.add(it) }
            if (!success) throw IllegalStateException("HLS 下载失败")
            moveCompleted(temporary, destination)
            onProgress(DownloadProgress(destination.length(), destination.length(), 0, "下载完成"))
            return@withContext destination
        }

        val total = link.size.takeIf { it > 0 }
            ?: chunks.getTotalSize(link.downloadUrl, headers)
            ?: -1L
        // A fixed thread count is wasteful for small files and leaves bandwidth idle on
        // large files. Keep each range large enough for CDN efficiency, then scale up to
        // the user's ceiling for large downloads.
        val effectiveThreads = optimalThreadCount(total, threadCount)

        if (total <= 0 || effectiveThreads == 1) {
            val meter = ProgressMeter(total, onProgress, speedLimitBytes = speedLimitBytes)
            val success = chunks.downloadFull(taskId, link.downloadUrl, temporary, headers, total) { meter.add(it) }
            if (!success) throw IllegalStateException("下载失败，服务器拒绝请求或链接已经过期")
        } else {
            val partsDir = File(targetDirectory, ".${destination.name}.yunx-parts").apply { mkdirs() }
            val ranges = splitRanges(total, effectiveThreads)
            val partFiles = ranges.indices.map { File(partsDir, "part-$it") }
            val resumed = partFiles.sumOf { it.length() }
            val meter = ProgressMeter(total, onProgress, resumed, speedLimitBytes)

            val results = coroutineScope {
                ranges.mapIndexed { index, range ->
                    async(Dispatchers.IO) {
                        chunks.downloadChunk(
                            taskId = taskId,
                            url = link.downloadUrl,
                            start = range.first,
                            end = range.last,
                            partFile = partFiles[index],
                            headers = headers
                        ) { meter.add(it) }
                    }
                }.awaitAll()
            }

            if (results.all { it == ChunkResult.OK }) {
                mergeParts(partFiles, temporary)
                deletePartsDirectory(partsDir, targetDirectory)
            } else if (results.any { it == ChunkResult.RANGE_IGNORED }) {
                chunks.cancelCalls(taskId)
                deletePartsDirectory(partsDir, targetDirectory)
                val fallbackMeter = ProgressMeter(total, onProgress, speedLimitBytes = speedLimitBytes)
                val success = chunks.downloadFull(taskId, link.downloadUrl, temporary, headers, total) {
                    fallbackMeter.add(it)
                }
                if (!success) throw IllegalStateException("服务器不支持分片，单线程回退下载失败")
            } else {
                throw IllegalStateException("部分下载分片失败，可重新开始以断点续传")
            }
        }

        if (total > 0 && temporary.length() != total) {
            throw IllegalStateException("文件大小校验失败：期望 $total，实际 ${temporary.length()}")
        }
        moveCompleted(temporary, destination)
        onProgress(DownloadProgress(destination.length(), destination.length(), 0, "下载完成"))
        destination
    }

    fun cancel(taskId: Long) = chunks.cancelCalls(taskId)

    private fun splitRanges(total: Long, requested: Int): List<LongRange> {
        val count = min(requested.toLong(), total).toInt().coerceAtLeast(1)
        val base = total / count
        val remainder = total % count
        var start = 0L
        return List(count) { index ->
            val size = base + if (index < remainder) 1 else 0
            val range = start..(start + size - 1)
            start += size
            range
        }
    }

    internal fun optimalThreadCount(total: Long, requested: Int): Int {
        if (total <= 0) return 1
        val ceiling = requested.coerceIn(1, 64)
        val minimumChunkSize = 4L * 1024 * 1024
        val usefulRanges = ((total + minimumChunkSize - 1) / minimumChunkSize)
            .coerceIn(1, 64)
            .toInt()
        return min(ceiling, usefulRanges)
    }

    private fun mergeParts(parts: List<File>, destination: File) {
        FileOutputStream(destination, false).channel.use { output ->
            parts.forEach { part ->
                FileInputStream(part).channel.use { input ->
                    var position = 0L
                    while (position < input.size()) {
                        position += input.transferTo(position, input.size() - position, output)
                    }
                }
            }
        }
    }

    private fun deletePartsDirectory(partsDirectory: File, targetDirectory: File) {
        val canonicalTarget = targetDirectory.canonicalFile
        val canonicalParts = partsDirectory.canonicalFile
        require(canonicalParts.parentFile == canonicalTarget && canonicalParts.name.endsWith(".yunx-parts")) {
            "拒绝清理不安全的分片目录"
        }
        canonicalParts.deleteRecursively()
    }

    private fun moveCompleted(source: File, destination: File) {
        runCatching {
            Files.move(
                source.toPath(), destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(180).ifBlank { "download.bin" }

    private fun uniqueDestination(directory: File, name: String): File {
        val first = File(directory, name)
        if (!first.exists()) return first
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 1
        while (true) {
            val candidate = File(directory, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private class ProgressMeter(
        private val total: Long,
        private val callback: (DownloadProgress) -> Unit,
        initial: Long = 0L,
        private val speedLimitBytes: Long = 0L
    ) {
        private val downloaded = AtomicLong(initial)
        private val startedAt = System.nanoTime()
        private val lastEmit = AtomicLong(0)

        suspend fun add(bytes: Long) {
            val nowValue = downloaded.addAndGet(bytes)
            if (speedLimitBytes > 0) {
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                val expectedMs = (nowValue * 1_000L / speedLimitBytes).coerceAtMost(30_000L)
                if (expectedMs > elapsedMs) delay((expectedMs - elapsedMs).coerceAtMost(1_000L))
            }
            val now = System.nanoTime()
            val previous = lastEmit.get()
            if (nowValue == total || now - previous >= 150_000_000L) {
                if (lastEmit.compareAndSet(previous, now)) {
                    val seconds = ((now - startedAt).coerceAtLeast(1L)) / 1_000_000_000.0
                    callback(DownloadProgress(nowValue, total, (nowValue / seconds).toLong(), "下载中"))
                }
            }
        }
    }
}
