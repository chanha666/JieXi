package com.yunx.desktop.download

import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.ChunkResult
import com.yunx.app.data.download.HlsDownloader
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.model.DownloadLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
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
    private val provisionalLock = Any()
    private val provisionalOutputs = mutableMapOf<Long, ProvisionalOutput>()
    private val discardedTasks = mutableSetOf<Long>()

    /**
     * A completed payload is provisional until the controller has durably
     * persisted COMPLETED. Keeping the exact source/destination pair lets a
     * cancelled task move only its own output back to its private resume file.
     */
    private data class ProvisionalOutput(
        val destination: File,
        val temporary: File,
        val targetDirectory: File
    )

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
        val workToken = java.lang.Long.toUnsignedString(taskId, 16)
        val temporary = File(targetDirectory, ".$safeName.$workToken.yunx-downloading")
        val partsDir = File(targetDirectory, ".$safeName.$workToken.yunx-parts")
        claimLegacyWorkFile(File(targetDirectory, ".$safeName.yunx-downloading"), temporary)
        claimLegacyWorkFile(File(targetDirectory, ".$safeName.yunx-parts"), partsDir)
        if (link.isHls) {
            val meter = ProgressMeter(link.size, onProgress, speedLimitBytes = speedLimitBytes)
            val success = HlsDownloader.download(link.downloadUrl, headers, temporary) { meter.add(it) }
            if (!success) throw IllegalStateException("HLS 下载失败")
            currentCoroutineContext().ensureActive()
            val destination = publishProvisional(taskId, temporary, targetDirectory, safeName)
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

        // A cancellation can arrive after merge and before the controller
        // commits COMPLETED. rollbackProvisional() restores that exact full
        // file here, so resume can republish without downloading it again.
        val reusableComplete = total > 0 && temporary.isFile && temporary.length() == total
        if (reusableComplete) {
            onProgress(DownloadProgress(total, total, 0, "恢复已完成文件"))
        } else if (total <= 0 || effectiveThreads == 1) {
            val meter = ProgressMeter(total, onProgress, speedLimitBytes = speedLimitBytes)
            val success = chunks.downloadFull(taskId, link.downloadUrl, temporary, headers, total) { meter.add(it) }
            if (!success) throw IllegalStateException("下载失败，服务器拒绝请求或链接已经过期")
        } else {
            partsDir.mkdirs()
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
        currentCoroutineContext().ensureActive()
        val destination = publishProvisional(taskId, temporary, targetDirectory, safeName)
        onProgress(DownloadProgress(destination.length(), destination.length(), 0, "下载完成"))
        destination
    }

    fun cancel(taskId: Long) = chunks.cancelCalls(taskId)

    /** Prevent a remove racing the Swing completion block from committing. */
    fun beginDiscard(taskId: Long) {
        synchronized(provisionalLock) { discardedTasks += taskId }
    }

    /**
     * Commit only the exact output returned by download(). A task for which
     * removal has already begun can no longer commit its provisional file.
     */
    fun commit(taskId: Long, output: File): Boolean = synchronized(provisionalLock) {
        val provisional = provisionalOutputs[taskId] ?: return@synchronized false
        require(samePath(provisional.destination, output)) { "提交文件与任务临时成品不匹配" }
        if (taskId in discardedTasks) return@synchronized false
        provisionalOutputs.remove(taskId)
        true
    }

    /**
     * Pause/failure path: restore only this task's exact, uncommitted public
     * file to its hidden resume path. A pre-existing public file is never
     * selected by name and is therefore never touched.
     */
    fun rollbackProvisional(taskId: Long): Boolean = synchronized(provisionalLock) {
        val provisional = provisionalOutputs[taskId] ?: return@synchronized true
        validateProvisional(provisional)
        val destination = provisional.destination
        val temporary = provisional.temporary
        try {
            if (destination.exists()) {
                if (temporary.exists()) {
                    // Preserve the older private partial; the exact newly
                    // created provisional is disposable and never overwrites it.
                    Files.deleteIfExists(destination.toPath())
                } else {
                    Files.move(destination.toPath(), temporary.toPath())
                }
            }
            provisionalOutputs.remove(taskId)
            true
        } catch (_: IOException) {
            // Keep the ownership record so remove() can retry exact cleanup
            // after every worker/file handle has stopped.
            false
        }
    }

    /** Delete only this task's private partials after its worker has stopped. */
    fun discard(taskId: Long, targetDirectory: File, fileName: String) {
        synchronized(provisionalLock) {
            val canonicalTarget = targetDirectory.canonicalFile
            provisionalOutputs[taskId]?.let { provisional ->
                validateProvisional(provisional)
                require(provisional.targetDirectory == canonicalTarget) { "拒绝清理其他目录的临时成品" }
                // Exact registered path only: no glob and no derivation from a
                // public filename, so an older same-name user file is untouched.
                Files.deleteIfExists(provisional.destination.toPath())
                provisionalOutputs.remove(taskId)
            }
            val safeName = safeFileName(fileName.ifBlank { "download.bin" })
            val workToken = java.lang.Long.toUnsignedString(taskId, 16)
            val temporary = File(targetDirectory, ".$safeName.$workToken.yunx-downloading")
            val parts = File(targetDirectory, ".$safeName.$workToken.yunx-parts")
            deleteTemporaryFile(temporary, targetDirectory)
            if (parts.exists()) deletePartsDirectory(parts, targetDirectory)
            discardedTasks.remove(taskId)
        }
    }

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

    internal suspend fun mergeParts(parts: List<File>, destination: File) {
        currentCoroutineContext().ensureActive()
        FileOutputStream(destination, false).channel.use { output ->
            parts.forEach { part ->
                currentCoroutineContext().ensureActive()
                FileInputStream(part).channel.use { input ->
                    var position = 0L
                    while (position < input.size()) {
                        currentCoroutineContext().ensureActive()
                        // Bound each zero-copy call so a multi-GB part cannot
                        // hide cancellation inside one long native transfer.
                        val transferSize = min(input.size() - position, 8L * 1024 * 1024)
                        val transferred = input.transferTo(position, transferSize, output)
                        if (transferred <= 0L) throw IOException("合并分片时未能继续写入")
                        position += transferred
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
    }

    private fun deletePartsDirectory(partsDirectory: File, targetDirectory: File) {
        val canonicalTarget = targetDirectory.canonicalFile
        val canonicalParts = partsDirectory.canonicalFile
        require(canonicalParts.parentFile == canonicalTarget && canonicalParts.name.endsWith(".yunx-parts")) {
            "拒绝清理不安全的分片目录"
        }
        canonicalParts.deleteRecursively()
    }

    private fun deleteTemporaryFile(temporary: File, targetDirectory: File) {
        if (!temporary.exists()) return
        val canonicalTarget = targetDirectory.canonicalFile
        val canonicalTemporary = temporary.canonicalFile
        require(canonicalTemporary.parentFile == canonicalTarget && canonicalTemporary.name.endsWith(".yunx-downloading")) {
            "拒绝清理不安全的临时文件"
        }
        canonicalTemporary.delete()
    }

    internal fun moveCompletedWithoutOverwrite(source: File, directory: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 0
        while (true) {
            val candidateName = if (index == 0) name else "$base ($index)$extension"
            val candidate = File(directory, candidateName)
            try {
                Files.move(source.toPath(), candidate.toPath())
                return candidate
            } catch (_: FileAlreadyExistsException) {
                index++
            }
        }
    }

    private fun publishProvisional(taskId: Long, source: File, directory: File, name: String): File =
        synchronized(provisionalLock) {
            if (taskId in discardedTasks) throw CancellationException("任务已删除")
            check(provisionalOutputs[taskId] == null) { "任务仍有未处理的临时成品" }
            val canonicalDirectory = directory.canonicalFile
            val canonicalSource = source.canonicalFile
            require(canonicalSource.parentFile == canonicalDirectory && canonicalSource.name.endsWith(".yunx-downloading")) {
                "拒绝发布不安全的临时文件"
            }
            val destination = moveCompletedWithoutOverwrite(canonicalSource, canonicalDirectory, name).canonicalFile
            val provisional = ProvisionalOutput(destination, canonicalSource, canonicalDirectory)
            validateProvisional(provisional)
            provisionalOutputs[taskId] = provisional
            destination
        }

    private fun validateProvisional(provisional: ProvisionalOutput) {
        val target = provisional.targetDirectory.canonicalFile
        val destination = provisional.destination.canonicalFile
        val temporary = provisional.temporary.canonicalFile
        require(destination.parentFile == target) { "拒绝操作下载目录外的临时成品" }
        require(temporary.parentFile == target && temporary.name.endsWith(".yunx-downloading")) {
            "拒绝操作不安全的断点文件"
        }
    }

    private fun samePath(first: File, second: File): Boolean =
        first.toPath().toAbsolutePath().normalize() == second.toPath().toAbsolutePath().normalize()

    private fun claimLegacyWorkFile(legacy: File, isolated: File) {
        if (!legacy.exists() || isolated.exists()) return
        // A no-replace rename lets at most one concurrent task claim an old 3.x
        // partial. Other tasks keep their isolated work files.
        runCatching { Files.move(legacy.toPath(), isolated.toPath()) }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(180).ifBlank { "download.bin" }

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
