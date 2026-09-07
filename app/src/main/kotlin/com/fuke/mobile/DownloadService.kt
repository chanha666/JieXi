package com.fuke.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import com.yunx.app.R
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import com.jiexi.core.media.MediaTransferWatchdog
import com.jiexi.core.media.TransferLiveness

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = AtomicBoolean(false)
    private val queueRequested = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var destroyed = false
    @Volatile
    private var currentId: String? = null
    @Volatile
    private var stopMode = ""
    private var worker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val activeConnections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val removalRequested = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var requireWifiForCurrent = false
    private val lastNetworkCheckAt = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        createChannel()
        // Enter foreground before any disk deserialization/core preparation so
        // Android's short foreground-service deadline can never be missed.
        startForeground(NOTIFICATION_ID, notification("正在准备下载核心", 0, true))
        TaskStore.initialize(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_TASK_ID)
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (id == currentId) stopCurrent("pause") else id?.let(TaskStore::pauseIfQueued)
                stopIfIdle(startId)
            }
            ACTION_CANCEL -> {
                if (id == currentId) {
                    stopCurrent("cancel")
                } else {
                    id?.let {
                        TaskStore.update(it) { task -> task.copy(status = TaskStatus.CANCELED, stage = "已取消", speed = "") }
                        cleanupArtifactsAsync(it)
                    }
                    stopIfIdle(startId)
                }
            }
            ACTION_REMOVE -> {
                if (id != null) {
                    removalRequested.add(id)
                    if (id == currentId) {
                        stopCurrent("remove")
                    } else {
                        TaskStore.remove(id)
                        cleanupArtifactsAsync(id)
                        stopIfIdle(startId)
                    }
                } else stopIfIdle(startId)
            }
            ACTION_CLEAR_FINISHED -> {
                val removable = TaskStore.tasks.value
                    .filter { it.status in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELED) }
                    .map { it.id }
                removable.forEach {
                    TaskStore.remove(it)
                    cleanupArtifactsAsync(it)
                }
                stopIfIdle(startId)
            }
            ACTION_CONTINUE -> {
                if (id != null && TaskStore.queueForRetry(id)) processQueue() else stopIfIdle(startId)
            }
            else -> processQueue()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        currentId?.let {
            TaskStore.markInterrupted(it, "系统结束了超长后台下载，点击继续即可断点续传。")
            stopCurrent("interrupt")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onDestroy() {
        destroyed = true
        if (stopMode.isBlank()) stopMode = "interrupt"
        currentId?.let(TaskStore::markInterrupted)
        activeConnections.forEach { runCatching(it::disconnect) }
        activeConnections.clear()
        worker?.cancel()
        currentId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun processQueue() {
        queueRequested.set(true)
        if (destroyed) return
        if (!processing.compareAndSet(false, true)) return
        queueRequested.set(false)
        acquireWakeLock()
        worker = scope.launch {
            try {
                while (true) {
                    val task = TaskStore.claimNextQueued() ?: break
                    currentId = task.id
                    stopMode = ""
                    // A remove intent may race the QUEUED -> RUNNING claim. Check
                    // after publishing currentId so the claimed task can never start.
                    if (removalRequested.remove(task.id)) {
                        TaskStore.remove(task.id)
                        MediaTaskArtifacts.cleanup(this@DownloadService, task.id)
                        currentId = null
                        continue
                    }
                    if (AppPrefs.read(this@DownloadService).wifiOnly && !isOnWifi()) {
                        TaskStore.update(task.id) { it.copy(status = TaskStatus.PAUSED, stage = "等待 Wi-Fi", error = "正在等待 Wi-Fi，连接后点击继续。") }
                        currentId = null
                        break
                    }
                    val usesFastDirectDownload =
                        (task.platform in setOf("抖音", "X") || task.resolvedUrl.isNotBlank()) && task.outputFormat != "mp3"
                    // Direct public files do not need Python, yt-dlp, FFmpeg or
                    // aria2. Skipping their extraction removes several seconds
                    // from startup and avoids wasting hundreds of MB of I/O.
                    if (!usesFastDirectDownload) Engine.initializeDownloadTools(applicationContext)
                    runTask(task)
                }
            } catch (_: CancellationException) {
                currentId?.let(TaskStore::markInterrupted)
            } catch (error: Throwable) {
                Log.e(TAG, "Download queue stopped unexpectedly", error)
                val failedTask = currentId?.let(TaskStore::get) ?: TaskStore.nextQueued()
                failedTask?.let { task ->
                    TaskStore.update(task.id) {
                        it.copy(status = TaskStatus.FAILED, stage = "核心启动失败", speed = "", error = "下载核心启动失败：${cleanError(error)}")
                    }
                    runCatching { updateNotification("下载没有启动成功", task.progress, false) }
                }
            } finally {
                processing.set(false)
                currentId = null
                releaseWakeLock()
                mainHandler.post {
                    if (destroyed) return@post
                    if (processing.get()) return@post
                    if (queueRequested.get() || TaskStore.hasQueued()) {
                        processQueue()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun stopIfIdle(startId: Int) {
        if (processing.get()) return
        if (TaskStore.hasQueued()) {
            processQueue()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun runTask(task: DownloadTask) {
        currentId = task.id
        TaskStore.update(task.id) {
            it.copy(
                status = TaskStatus.RUNNING,
                progress = it.progress.coerceAtLeast(0),
                stage = "正在连接",
                speed = "",
                error = ""
            )
        }
        updateNotification(task.title, task.progress, true)

        val root = MediaTaskArtifacts.taskRoot(this, task.id)
        root.mkdirs()
        if (StatFs(root.absolutePath).availableBytes < 512L * 1024L * 1024L) {
            TaskStore.update(task.id) { it.copy(status = TaskStatus.FAILED, stage = "空间不足", error = "手机剩余空间不足 512 MB。") }
            currentId = null
            return
        }
        val settings = AppPrefs.read(this)
        requireWifiForCurrent = settings.wifiOnly
        lastNetworkCheckAt.set(0L)
        val template = when (settings.fileNameRule) {
            "title" -> "%(title).160B.%(ext)s"
            "uploader-title" -> "%(uploader|未知作者).60B - %(title).140B.%(ext)s"
            else -> "%(title).160B [%(id)s].%(ext)s"
        }
        val outputTemplate = if (task.platform in setOf("抖音", "X", "哔哩哔哩", "直链视频")) {
            MediaTaskPolicy.directOutputTemplate(task.title)
        } else template
        val freshResolvedUrl = when (task.platform) {
            "抖音" -> runCatching { DouyinFallback.analyze(task.url).downloadUrl }.getOrDefault(task.resolvedUrl.orEmpty())
            "X" -> runCatching { XFallback.analyze(task.url).downloadUrl }.getOrDefault(task.resolvedUrl.orEmpty())
            "哔哩哔哩" -> runCatching { BilibiliPublicFallback.analyze(task.url).downloadUrl }
                .getOrDefault(task.resolvedUrl.orEmpty())
            else -> task.resolvedUrl.orEmpty()
        }
        val request = YoutubeDLRequest(freshResolvedUrl.ifBlank { task.url }).apply {
            addOption("--no-mtime")
            addOption("--continue")
            addOption("--newline")
            addOption("--progress")
            addOption("--encoding", "utf-8")
            addOption("--ignore-config")
            addOption("--no-playlist")
            addOption("--retries", settings.retries)
            addOption("--fragment-retries", settings.retries)
            addOption("--retry-sleep", "exp=1:20")
            addOption("--socket-timeout", 15)
            addOption("--extractor-retries", 1)
            addOption("--concurrent-fragments", 8)
            if (freshResolvedUrl.isBlank() && task.platform != "YouTube") {
                addOption("--downloader", "libaria2c.so")
                addOption("--downloader-args", "aria2c:-x8 -s8 -k1M --file-allocation=none --summary-interval=1")
            }
            addOption("-f", task.formatSelector)
            if (task.platform == "抖音") {
                addOption("--referer", "https://www.douyin.com/")
                addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            }
            if (task.platform == "哔哩哔哩") {
                addOption("--referer", "https://www.bilibili.com/")
                addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            }
            if (task.platform == "X") addOption("--extractor-args", "twitter:api=syndication")
            if (task.platform == "X") addOption("--force-ipv4")
            addOption("-o", File(root, outputTemplate).absolutePath)
            if (task.outputFormat == "mp3") {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                addOption("--merge-output-format", "mp4")
                if (task.embedSubtitles) {
                    addOption("--write-subs")
                    addOption("--write-auto-subs")
                    addOption("--sub-langs", "zh.*,en.*")
                    addOption("--embed-subs")
                }
            }
        }
        var watchdog: MediaTransferWatchdog? = null
        try {
            if (stopMode.isNotBlank()) throw IOException("download stopped")
            val resultFile = if (freshResolvedUrl.isNotBlank() && task.outputFormat != "mp3") {
                downloadDirect(task, freshResolvedUrl, root, outputTemplate)
            } else {
                var lastUiUpdate = 0L
                var lastPercent = -1
                watchdog = MediaTransferWatchdog(root) { YoutubeDL.getInstance().destroyProcessById(task.id) }
                YoutubeDL.getInstance().execute(request, task.id) { progress, eta, line ->
                    watchdog?.observe(line)
                    ensureTransferAllowed()
                    val linePercent = Regex("(\\d+(?:\\.\\d+)?)%").find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
                    val percent = maxOf(progress.toInt(), linePercent ?: 0).coerceIn(0, 100)
                    val speed = Regex("(?:at|速度)\\s+([^\\s]+)").find(line)?.groupValues?.getOrNull(1) ?: ""
                    val now = System.currentTimeMillis()
                    if (percent != lastPercent || now - lastUiUpdate >= 350L) {
                        TaskStore.update(task.id) {
                            it.copy(
                                progress = percent.coerceAtMost(99),
                                stage = TransferLiveness.stage(line) ?: if (percent > 0) "正在下载" else "正在获取媒体流",
                                eta = if (eta > 0) "${eta}s" else "",
                                speed = speed
                            )
                        }
                        updateNotification(task.title, percent, true)
                        lastPercent = percent
                        lastUiUpdate = now
                    }
                }
                watchdog?.close()
                check(watchdog?.timedOut != true) { MediaTransferWatchdog.TIMEOUT_MESSAGE }
                MediaTaskArtifacts.completedOutput(root, task.outputFormat)
            }
            if (stopMode.isNotBlank()) throw IOException("download stopped")
            val completedFileName = resultFile.name
            TaskStore.update(task.id) {
                it.copy(
                    progress = 99,
                    downloadedBytes = resultFile.length(),
                    totalBytes = resultFile.length(),
                    stage = "正在保存成品",
                    speed = "准备写入 下载/解析"
                )
            }
            val uri = MediaFiles.publish(this, resultFile) { copied, total ->
                if (stopMode.isNotBlank()) throw IOException("download stopped")
                val savePercent = if (total > 0) ((copied * 100L) / total).coerceIn(0L, 100L) else 0L
                TaskStore.update(task.id) {
                    it.copy(stage = "正在保存成品", speed = "已写入 $savePercent%")
                }
            }
            TaskStore.update(task.id) {
                it.copy(status = TaskStatus.COMPLETED, progress = 100, stage = "已完成", speed = "", fileUri = uri.toString(), fileName = completedFileName, error = "")
            }
            resultFile.delete()
            root.deleteRecursively()
            updateNotification("$completedFileName 已保存", 100, false)
        } catch (error: Throwable) {
            when (stopMode) {
                "pause" -> TaskStore.update(task.id) { it.copy(status = TaskStatus.PAUSED, stage = "已暂停", speed = "", error = "任务已暂停，点击继续可断点续传。") }
                "interrupt" -> TaskStore.markInterrupted(task.id)
                "cancel" -> {
                    TaskStore.update(task.id) { it.copy(status = TaskStatus.CANCELED, stage = "已取消", speed = "", error = "") }
                    MediaTaskArtifacts.cleanup(this, task.id)
                }
                "remove" -> {
                    TaskStore.remove(task.id)
                    MediaTaskArtifacts.cleanup(this, task.id)
                    removalRequested.remove(task.id)
                }
                else -> TaskStore.update(task.id) { it.copy(status = TaskStatus.FAILED, stage = "失败", speed = "", error = if (watchdog?.timedOut == true) MediaTransferWatchdog.TIMEOUT_MESSAGE else cleanError(error)) }
            }
            if (error is CancellationException) throw error
        } finally {
            watchdog?.close()
            // Covers a remove click that arrived after the transfer committed
            // but before currentId was cleared: the record/artifacts must still go.
            if (removalRequested.remove(task.id)) {
                TaskStore.remove(task.id)
                MediaTaskArtifacts.cleanup(this, task.id)
            }
            currentId = null
            stopMode = ""
            requireWifiForCurrent = false
        }
    }

    private fun downloadDirect(task: DownloadTask, url: String, root: File, outputTemplate: String): File {
        val sourceExtension = runCatching {
            URI(url).path.substringAfterLast('.', "").lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
        }.getOrNull() ?: "mp4"
        val target = File(root, outputTemplate.replace("%(ext)s", sourceExtension))
        val partial = File(root, "${target.name}.part")
        val probe = openDownloadConnection(task, url, "bytes=0-0")
        val totalFromRange = try {
            val status = probe.responseCode
            if (status !in 200..299) throw IOException("视频文件请求失败（HTTP $status）。")
            val total = if (status == HttpURLConnection.HTTP_PARTIAL) {
                ContentRangePolicy.parse(probe.getHeaderField("Content-Range"))
                    ?.takeIf { it.start == 0L && it.end == 0L }
                    ?.total
            } else null
            probe.inputStream.use { input -> input.read() }
            total
        } finally {
            closeConnection(probe)
        }
        if (totalFromRange != null && totalFromRange >= SEGMENT_THRESHOLD_BYTES) {
            return downloadDirectSegmented(task, url, root, target, partial, totalFromRange)
        }
        return downloadDirectSingle(task, url, target, partial)
    }

    private fun downloadDirectSingle(task: DownloadTask, url: String, target: File, partial: File): File {
        val maxAttempts = DirectDownloadRetryPolicy.maxAttempts(AppPrefs.read(this).retries)
        var attempt = 1
        while (true) {
            try {
                return downloadDirectSingleAttempt(task, url, target, partial)
            } catch (error: Throwable) {
                if (!DirectDownloadRetryPolicy.shouldRetry(error, attempt, maxAttempts, stopMode.isNotBlank())) throw error
                publishDirectRetry(task, attempt, maxAttempts)
                Thread.sleep(DirectDownloadRetryPolicy.backoffMillis(attempt))
                attempt += 1
            }
        }
    }

    private fun downloadDirectSingleAttempt(task: DownloadTask, url: String, target: File, partial: File): File {
        val existing = partial.length()
        val connection = openDownloadConnection(task, url, if (existing > 0) "bytes=$existing-" else null)
        var expectedTotal = 0L
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("视频文件请求失败（$code）。")
            val contentRange = if (code == HttpURLConnection.HTTP_PARTIAL) {
                ContentRangePolicy.parse(connection.getHeaderField("Content-Range"))
            } else null
            if (existing > 0 && code == HttpURLConnection.HTTP_PARTIAL &&
                !ContentRangePolicy.matches(connection.getHeaderField("Content-Range"), existing, null, null)
            ) {
                // A wrong 206 cannot be appended safely. Discard the old prefix;
                // the normal retry loop immediately starts a fresh GET from byte 0.
                partial.delete()
                throw IOException("服务器返回了错误的续传区间，已从头重试。")
            }
            if (existing == 0L && code == HttpURLConnection.HTTP_PARTIAL &&
                !ContentRangePolicy.isWholeFile(connection.getHeaderField("Content-Range"))
            ) {
                partial.delete()
                throw IOException("服务器只返回了部分文件，拒绝保存不完整视频。")
            }
            val append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL
            val startingBytes = if (append) existing else 0L
            val totalBytes = contentRange?.total
                ?: ((connection.contentLengthLong.takeIf { it > 0 } ?: 0L) + startingBytes)
            expectedTotal = totalBytes
            var responseRemaining = contentRange?.length ?: Long.MAX_VALUE
            var downloaded = startingBytes
            var lastBytes = downloaded
            var lastUpdate = System.currentTimeMillis()
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).buffered(128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        ensureTransferAllowed()
                        val count = input.read(buffer)
                        if (count < 0) break
                        val accepted = minOf(count.toLong(), responseRemaining).toInt()
                        if (accepted <= 0) break
                        output.write(buffer, 0, accepted)
                        downloaded += accepted
                        if (responseRemaining != Long.MAX_VALUE) responseRemaining -= accepted
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500) {
                            val percent = if (totalBytes > 0) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99) else 0
                            val bytesPerSecond = ((downloaded - lastBytes) * 1000L / (now - lastUpdate).coerceAtLeast(1L))
                            val displaySpeed = if (totalBytes > 0) formatSpeed(bytesPerSecond) else "${formatSpeed(bytesPerSecond)} · ${formatBytes(downloaded)}"
                            TaskStore.update(task.id) {
                                it.copy(
                                    progress = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes,
                                    stage = "正在下载",
                                    speed = displaySpeed
                                )
                            }
                            updateNotification(task.title, percent, true)
                            lastBytes = downloaded
                            lastUpdate = now
                        }
                    }
                }
            }
        } finally {
            closeConnection(connection)
        }
        if (expectedTotal > 0L && partial.length() != expectedTotal) {
            throw IOException("视频文件下载不完整，已保留断点等待重试。")
        }
        if (target.exists() && !target.delete()) error("无法替换旧的临时文件。")
        if (!partial.renameTo(target)) error("无法生成下载文件。")
        return target
    }

    private fun downloadDirectSegmented(
        task: DownloadTask,
        url: String,
        root: File,
        target: File,
        partial: File,
        totalBytes: Long
    ): File {
        val segmentCount = when {
            totalBytes >= 512L * 1024L * 1024L -> 8
            totalBytes >= 128L * 1024L * 1024L -> 6
            else -> 4
        }
        val segmentSize = (totalBytes + segmentCount - 1L) / segmentCount
        val segmentFiles = (0 until segmentCount).map { File(root, "${target.name}.segment-$it.part") }
        segmentFiles.forEachIndexed { index, file ->
            val start = index * segmentSize
            val end = minOf(totalBytes - 1L, start + segmentSize - 1L)
            val expectedLength = end - start + 1L
            if (file.length() > expectedLength) file.delete()
        }
        val downloaded = AtomicLong(segmentFiles.sumOf { it.length() })
        val maxAttempts = DirectDownloadRetryPolicy.maxAttempts(AppPrefs.read(this).retries)
        val pool = Executors.newFixedThreadPool(segmentCount)
        val futures = segmentFiles.mapIndexed { index, segmentFile ->
            pool.submit {
                val start = index * segmentSize
                val end = minOf(totalBytes - 1L, start + segmentSize - 1L)
                val expectedLength = end - start + 1L
                if (segmentFile.length() > expectedLength) {
                    segmentFile.delete()
                }
                var attempt = 1
                while (true) {
                    ensureTransferAllowed()
                    val existing = segmentFile.length().coerceAtMost(expectedLength)
                    if (existing >= expectedLength) return@submit
                    var connection: HttpURLConnection? = null
                    try {
                        connection = openDownloadConnection(task, url, "bytes=${start + existing}-$end")
                        if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                            throw IOException("服务器没有接受分段下载请求。")
                        }
                        val requestedStart = start + existing
                        val header = connection.getHeaderField("Content-Range")
                        if (!ContentRangePolicy.matches(header, requestedStart, end, totalBytes)) {
                            throw IOException("服务器返回了错误的分段区间。")
                        }
                        var responseRemaining = requireNotNull(ContentRangePolicy.parse(header)).length
                        connection.inputStream.use { input ->
                            FileOutputStream(segmentFile, existing > 0).buffered(512 * 1024).use { output ->
                                val buffer = ByteArray(512 * 1024)
                                while (true) {
                                    ensureTransferAllowed()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    val accepted = minOf(count.toLong(), responseRemaining).toInt()
                                    if (accepted <= 0) break
                                    output.write(buffer, 0, accepted)
                                    downloaded.addAndGet(accepted.toLong())
                                    responseRemaining -= accepted
                                }
                            }
                        }
                        if (segmentFile.length() != expectedLength) {
                            throw IOException("第 ${index + 1} 个分段下载不完整。")
                        }
                        return@submit
                    } catch (error: Throwable) {
                        if (!DirectDownloadRetryPolicy.shouldRetry(error, attempt, maxAttempts, stopMode.isNotBlank())) throw error
                        publishDirectRetry(task, attempt, maxAttempts)
                        Thread.sleep(DirectDownloadRetryPolicy.backoffMillis(attempt))
                        attempt += 1
                    } finally {
                        connection?.let(::closeConnection)
                    }
                }
            }
        }
        var lastBytes = downloaded.get()
        var lastUpdate = System.currentTimeMillis()
        try {
            while (futures.any { !it.isDone }) {
                ensureTransferAllowed()
                Thread.sleep(350L)
                val now = System.currentTimeMillis()
                val currentBytes = downloaded.get()
                val speed = (currentBytes - lastBytes) * 1000L / (now - lastUpdate).coerceAtLeast(1L)
                val percent = ((currentBytes * 100L) / totalBytes).toInt().coerceIn(0, 99)
                TaskStore.update(task.id) {
                    it.copy(
                        progress = percent,
                        downloadedBytes = currentBytes,
                        totalBytes = totalBytes,
                        stage = "正在分段下载",
                        speed = formatSpeed(speed)
                    )
                }
                updateNotification(task.title, percent, true)
                lastBytes = currentBytes
                lastUpdate = now
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(2, TimeUnit.SECONDS)
        }
        FileOutputStream(partial, false).buffered(1024 * 1024).use { output ->
            segmentFiles.forEach { segment -> segment.inputStream().buffered(1024 * 1024).use { it.copyTo(output, 1024 * 1024) } }
        }
        check(partial.length() == totalBytes) { "分段合并后的文件大小不正确。" }
        segmentFiles.forEach { it.delete() }
        if (target.exists() && !target.delete()) error("无法替换旧的临时文件。")
        if (!partial.renameTo(target)) error("无法生成下载文件。")
        return target
    }

    private fun publishDirectRetry(task: DownloadTask, attempt: Int, maxAttempts: Int) {
        val retryNumber = attempt.coerceAtMost((maxAttempts - 1).coerceAtLeast(1))
        TaskStore.update(task.id) {
            it.copy(stage = "连接中断，正在断点重试（$retryNumber/${maxAttempts - 1}）", speed = "")
        }
    }

    private fun openDownloadConnection(task: DownloadTask, url: String, range: String?): HttpURLConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
        connection.setRequestProperty("Accept-Encoding", "identity")
        when (task.platform) {
            "抖音" -> connection.setRequestProperty("Referer", "https://www.douyin.com/")
            "X" -> connection.setRequestProperty("Referer", "https://x.com/")
            "哔哩哔哩" -> connection.setRequestProperty("Referer", "https://www.bilibili.com/")
        }
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            connection.connect()
            activeConnections.add(connection)
            connection
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }

    private fun closeConnection(connection: HttpURLConnection) {
        activeConnections.remove(connection)
        connection.disconnect()
    }

    private fun formatSpeed(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / 1048576.0)
        bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1073741824.0)
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0)
        else -> "%.0f KB".format(bytes / 1024.0)
    }

    private fun stopCurrent(mode: String) {
        stopMode = mode
        activeConnections.forEach { runCatching(it::disconnect) }
        activeConnections.clear()
        currentId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
    }

    private fun cleanupArtifactsAsync(taskId: String) {
        scope.launch {
            val result = runCatching { MediaTaskArtifacts.cleanup(this@DownloadService, taskId) }.getOrNull()
            if (result != null && (!result.deleted || result.bytesReleased > 0L)) {
                Log.i(TAG, "media task cleanup id=$taskId deleted=${result.deleted} bytes=${result.bytesReleased}")
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val manager = getSystemService(PowerManager::class.java)
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:media-download").apply {
            setReferenceCounted(false)
            acquire(10L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun isOnWifi(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun ensureTransferAllowed() {
        if (stopMode.isNotBlank()) throw IOException("download stopped")
        if (!requireWifiForCurrent) return
        val now = System.currentTimeMillis()
        val previous = lastNetworkCheckAt.get()
        if (now - previous < 1_000L || !lastNetworkCheckAt.compareAndSet(previous, now)) return
        if (!isOnWifi()) {
            stopMode = "pause"
            throw IOException("Wi-Fi disconnected")
        }
    }

    private fun cleanError(error: Throwable): String {
        return MediaErrorMessages.forDownload(error)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_description)
        })
    }

    private fun notification(title: String, progress: Int, running: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(if (running) "${currentId?.let(TaskStore::get)?.stage ?: "正在下载"} · $progress%" else "解析 · 媒体")
        .setContentIntent(PendingIntent.getActivity(this, 1, Intent(this, MediaActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOnlyAlertOnce(true)
        .setOngoing(running)
        .setProgress(100, progress, running && progress == 0)
        .apply {
            currentId?.let { id ->
                addAction(0, "暂停", serviceAction(ACTION_PAUSE, id, 2))
                addAction(0, "取消", serviceAction(ACTION_CANCEL, id, 3))
            }
        }.build()

    private fun updateNotification(title: String, progress: Int, running: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, progress, running))
    }

    private fun serviceAction(action: String, id: String, code: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_TASK_ID, id)
        return PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_ENQUEUE = "com.fuke.mobile.ENQUEUE"
        const val ACTION_PAUSE = "com.fuke.mobile.PAUSE"
        const val ACTION_CANCEL = "com.fuke.mobile.CANCEL"
        const val ACTION_CONTINUE = "com.fuke.mobile.CONTINUE"
        const val ACTION_REMOVE = "com.fuke.mobile.REMOVE"
        const val ACTION_CLEAR_FINISHED = "com.fuke.mobile.CLEAR_FINISHED"
        const val EXTRA_TASK_ID = "task_id"
        private const val CHANNEL_ID = "jiexi_media_downloads"
        private const val NOTIFICATION_ID = 6301
        private const val TAG = "JieXiMediaDownload"
        private const val SEGMENT_THRESHOLD_BYTES = 8L * 1024L * 1024L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(ACTION_ENQUEUE))
        }

        fun action(context: Context, action: String, id: String) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(action).putExtra(EXTRA_TASK_ID, id))
        }

        fun clearFinished(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(ACTION_CLEAR_FINISHED))
        }
    }
}
