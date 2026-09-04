package com.yunx.desktop.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.UnifiedLinkClassifier
import com.sun.jna.platform.win32.Crypt32Util
import com.yunx.desktop.settings.DesktopSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class DesktopMediaController(
    private val settings: DesktopSettings,
    val engine: DesktopMediaEngine = DesktopMediaEngine(),
    private val storeFile: File = File(
        System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
        "解析/media-tasks-v1.json"
    )
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = AtomicBoolean(false)
    private val processes = ConcurrentHashMap<String, Process>()
    private val requestedActions = ConcurrentHashMap<String, String>()
    private val removals = DesktopMediaRemovalRegistry()
    private var legacyPlaintextStoreLoaded = false

    var inputText by mutableStateOf("")
    var analyzing by mutableStateOf(false)
    var queueing by mutableStateOf(false)
    var preview by mutableStateOf<DesktopMediaPreview?>(null)
    var selectedFormat by mutableStateOf<MediaFormatChoice?>(null)
    var analysisError by mutableStateOf<String?>(null)
    var embedSubtitles by mutableStateOf(false)
    var coreStatus by mutableStateOf<MediaToolStatus?>(null)
    var coreChecking by mutableStateOf(false)
    var toolBusy by mutableStateOf(false)
    var toolMessage by mutableStateOf<String?>(null)
    val tasks = mutableStateListOf<DesktopMediaTask>()
    val hasActiveTasks: Boolean get() = tasks.any { it.state in activeStates }
    var onTaskNotification: ((String, String) -> Unit)? = null
    var onActivityChanged: (() -> Unit)? = null

    init {
        tasks += restoreTasks()
        // Migrate the former plaintext JSON immediately. The replacement is
        // encrypted for the current Windows user and omits signed CDN URLs.
        if (legacyPlaintextStoreLoaded) persist()
        refreshCoreStatus()
    }

    fun acceptInput(value: String, autoAnalyze: Boolean = false) {
        inputText = value
        preview = null
        selectedFormat = null
        analysisError = null
        if (autoAnalyze) analyze()
    }

    fun analyze() {
        if (analyzing || inputText.isBlank()) return
        analyzing = true
        analysisError = null
        preview = null
        scope.launch {
            runCatching { engine.analyze(inputText) }
                .onSuccess { value -> withContext(Dispatchers.Swing) {
                    preview = value
                    selectedFormat = value.formats.firstOrNull()
                    analyzing = false
                } }
                .onFailure { error -> withContext(Dispatchers.Swing) {
                    analysisError = friendlyError(error)
                    analyzing = false
                } }
        }
    }

    fun enqueueAll() {
        if (queueing) return
        val links = UnifiedLinkClassifier.classifyText(inputText)
            .filter { it.kind != LinkKind.CLOUD_SHARE }
        if (links.isEmpty()) {
            analysisError = "没有检测到可交给媒体引擎的网址。"
            return
        }
        queueing = true
        analysisError = null
        val preferred = selectedFormat
        scope.launch {
            var added = 0
            var failed = 0
            links.forEach { link ->
                if (tasks.any { it.sourceUrl == link.originalUrl && it.state in activeStates }) return@forEach
                runCatching {
                    preview?.takeIf { it.sourceUrl == link.originalUrl } ?: engine.analyze(link.originalUrl)
                }.onSuccess { info ->
                    val choice = preferred?.takeIf { selected -> info.formats.any { it.selector == selected.selector } }
                        ?: info.formats.firstOrNull()
                        ?: DesktopMediaEngine.defaultFormats().first()
                    val task = DesktopMediaTask(
                        sourceUrl = info.sourceUrl,
                        downloadUrl = info.downloadUrl,
                        title = info.title,
                        platform = info.platform,
                        formatSelector = choice.selector,
                        formatLabel = choice.label,
                        outputFormat = if (choice.selector == "bestaudio/best") "mp3" else "mp4",
                        embedSubtitles = embedSubtitles
                    )
                    withContext(Dispatchers.Swing) { tasks.add(0, task); added += 1; persist() }
                }.onFailure { failed += 1 }
            }
            withContext(Dispatchers.Swing) {
                queueing = false
                if (added == 0) analysisError = if (failed > 0) "这些链接暂时无法解析。" else "这些链接已经在任务中。"
                else if (failed > 0) analysisError = "已加入 $added 个任务，另有 $failed 个链接解析失败。"
                schedule()
            }
        }
    }

    @Synchronized
    fun schedule() {
        if (!processing.compareAndSet(false, true)) return
        onActivityChanged?.invoke()
        scope.launch {
            try {
                while (true) {
                    val task = withContext(Dispatchers.Swing) { tasks.lastOrNull { it.state == MediaTaskState.WAITING } } ?: break
                    runTask(task)
                }
            } finally {
                processing.set(false)
                onActivityChanged?.invoke()
                if (tasks.any { it.state == MediaTaskState.WAITING }) schedule()
            }
        }
    }

    fun pause(task: DesktopMediaTask) {
        if (task.state == MediaTaskState.WAITING) {
            task.state = MediaTaskState.PAUSED
            task.stage = "已暂停"
            persist()
            return
        }
        requestedActions[task.id] = "pause"
        task.stage = "正在暂停…"
        stopProcess(task.id)
    }

    fun resume(task: DesktopMediaTask) {
        if (task.state == MediaTaskState.COMPLETED) return
        task.state = MediaTaskState.WAITING
        task.stage = "等待中"
        task.error = null
        requestedActions.remove(task.id)
        persist()
        schedule()
    }

    fun cancel(task: DesktopMediaTask) {
        requestedActions[task.id] = "cancel"
        if (task.state in setOf(MediaTaskState.WAITING, MediaTaskState.PAUSED, MediaTaskState.INTERRUPTED, MediaTaskState.FAILED)) {
            task.state = MediaTaskState.CANCELLED
            task.stage = "已取消"
            requestedActions.remove(task.id)
            persist()
        } else {
            task.stage = "正在取消…"
            stopProcess(task.id)
        }
    }

    fun remove(task: DesktopMediaTask) {
        removals.markRemoved(task.id)
        val mayBeRunning = task.state in setOf(MediaTaskState.ANALYZING, MediaTaskState.DOWNLOADING) ||
            processes.containsKey(task.id)
        if (mayBeRunning) {
            stopProcess(task.id)
        } else {
            requestedActions.remove(task.id)
            val downloadRoot = task.workspaceRoot?.let(DesktopMediaWorkspace::normalizeRoot)
                ?: DesktopMediaWorkspace.normalizeRoot(settings.downloadDirectory)
            scope.launch {
                deleteWorkspace(task, downloadRoot)
                task.workspaceRoot = null
                removals.finish(task.id)
            }
        }
        tasks.remove(task)
        persist()
    }

    fun pauseAll() = tasks.filter { it.state in activeStates }.forEach(::pause)

    fun resumeAll() {
        tasks.filter { it.state in setOf(MediaTaskState.PAUSED, MediaTaskState.INTERRUPTED, MediaTaskState.FAILED) }
            .forEach { it.state = MediaTaskState.WAITING; it.stage = "等待中"; it.error = null }
        persist()
        schedule()
    }

    fun clearFinished() {
        tasks.filter { it.state in setOf(MediaTaskState.COMPLETED, MediaTaskState.CANCELLED) }
            .forEach(::remove)
    }

    fun refreshCoreStatus() {
        if (coreChecking) return
        coreChecking = true
        scope.launch {
            val status = runCatching(engine::coreStatus).getOrElse {
                MediaToolStatus("未知", "未知", "未知", false, false, friendlyError(it))
            }
            withContext(Dispatchers.Swing) { coreStatus = status; coreChecking = false }
        }
    }

    fun updateCore() {
        if (coreChecking) return
        coreChecking = true
        toolMessage = "正在更新媒体核心…"
        scope.launch {
            runCatching(engine::updateYtDlp)
                .onSuccess { withContext(Dispatchers.Swing) { toolMessage = "媒体核心已更新" } }
                .onFailure { withContext(Dispatchers.Swing) { toolMessage = friendlyError(it) } }
            val status = runCatching(engine::coreStatus).getOrNull()
            withContext(Dispatchers.Swing) { if (status != null) coreStatus = status; coreChecking = false }
        }
    }

    fun extractAudio(input: File) = runTool("提取音频") {
        val output = uniqueOutput("${input.nameWithoutExtension}-音频", "mp3")
        engine.runFfmpeg(listOf("-i", input.absolutePath, "-vn", "-c:a", "libmp3lame", "-q:a", "0", output.absolutePath))
        output
    }

    fun captureFrame(input: File, second: Double) = runTool("视频截图") {
        val output = uniqueOutput("${input.nameWithoutExtension}-截图", "jpg")
        engine.runFfmpeg(listOf("-ss", second.coerceIn(0.0, 86_400.0).toString(), "-i", input.absolutePath, "-frames:v", "1", "-q:v", "2", output.absolutePath))
        output
    }

    fun exportMetadata(input: File) = runTool("导出媒体信息") {
        val output = uniqueOutput("${input.nameWithoutExtension}-媒体信息", "json")
        val json = engine.runFfprobe(listOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", input.absolutePath))
        output.writeText(json, Charsets.UTF_8)
        output
    }

    private fun runTool(label: String, operation: () -> File) {
        if (toolBusy) return
        toolBusy = true
        toolMessage = "$label 处理中…"
        scope.launch {
            runCatching(operation)
                .onSuccess { file -> withContext(Dispatchers.Swing) { toolMessage = "$label 已完成：${file.name}" } }
                .onFailure { error -> withContext(Dispatchers.Swing) { toolMessage = friendlyError(error) } }
            withContext(Dispatchers.Swing) { toolBusy = false }
        }
    }

    private suspend fun runTask(task: DesktopMediaTask) {
        removals.begin(task.id)
        val shouldRun = withContext(Dispatchers.Swing) {
            if (!tasks.contains(task) || removals.isRemoved(task.id)) return@withContext false
            DesktopMediaWorkspace.bindTaskRoot(task, settings.downloadDirectory)
            task.state = MediaTaskState.DOWNLOADING
            task.stage = "正在连接"
            task.error = null
            task.updatedAt = System.currentTimeMillis()
            persist()
            true
        }
        if (!shouldRun) {
            removals.finish(task.id)
            return
        }
        val downloadRoot = task.workspaceRoot?.let(DesktopMediaWorkspace::normalizeRoot)
            ?: error("媒体任务没有绑定下载目录。")
        val lines = ArrayDeque<String>()
        var finishedPath = ""
        var process: Process? = null
        var deleteWorkspaceAfterExit = false
        val workDirectory = DesktopMediaWorkspace.taskDirectory(downloadRoot, task.id)
        try {
            process = engine.startDownload(task, workDirectory, settings.threadCount.coerceIn(1, 64))
            processes[task.id] = process
            if (requestedActions.containsKey(task.id) || removals.isRemoved(task.id)) terminateProcess(process)
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { output ->
                output.forEach { raw ->
                    val line = raw.trim()
                    if (line.isNotBlank()) {
                        if (lines.size >= 80) lines.removeFirst()
                        lines.addLast(line)
                    }
                    when {
                        line.startsWith("[progress]") -> {
                            val fields = line.removePrefix("[progress]").trim().split('|')
                            val percent = Regex("(\\d+(?:\\.\\d+)?)%").find(fields.getOrElse(0) { "" })
                                ?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.roundToInt()?.coerceIn(0, 99) ?: 0
                            val speed = fields.getOrElse(1) { "" }.trim().takeUnless { it == "NA" }.orEmpty()
                            val eta = fields.getOrElse(2) { "" }.trim().takeUnless { it == "NA" }.orEmpty()
                            javax.swing.SwingUtilities.invokeLater {
                                task.progress = percent
                                task.speed = speed
                                task.eta = eta
                                task.stage = if (percent > 0) "正在下载" else "正在获取媒体流"
                                task.updatedAt = System.currentTimeMillis()
                                persistThrottled()
                            }
                        }
                        line.startsWith("[finished]") -> finishedPath = line.removePrefix("[finished]").trim()
                    }
                }
            }
            val exitCode = process.waitFor()
            val action = requestedActions.remove(task.id)
            deleteWorkspaceAfterExit = removals.isRemoved(task.id)
            val workspaceOutput = if (action == null && exitCode == 0 && !deleteWorkspaceAfterExit) {
                finishedPath.takeIf(String::isNotBlank)?.let(::File)
                    ?.let { DesktopMediaWorkspace.requireOwnedOutput(workDirectory, it) }
                    ?: DesktopMediaWorkspace.newestOwnedOutput(workDirectory, task.createdAt)
                    ?: error("下载结束但没有找到成品文件。")
            } else {
                null
            }
            withContext(Dispatchers.Swing) {
                when {
                    removals.isRemoved(task.id) -> {
                        deleteWorkspaceAfterExit = true
                        task.state = MediaTaskState.CANCELLED
                        task.stage = "已取消"
                        task.speed = ""
                    }
                    action == "pause" -> {
                        task.state = MediaTaskState.PAUSED
                        task.stage = "已暂停"
                        task.speed = ""
                    }
                    action == "cancel" -> {
                        task.state = MediaTaskState.CANCELLED
                        task.stage = "已取消"
                        task.speed = ""
                    }
                    exitCode == 0 -> {
                        val committed = removals.commitIfActive(task.id) {
                            task.outputFile = DesktopMediaWorkspace.moveCompletedWithoutOverwrite(
                                source = workspaceOutput ?: error("下载结束但没有找到成品文件。"),
                                taskDirectory = workDirectory,
                                downloadDirectory = downloadRoot
                            )
                            task.progress = 100
                            task.state = MediaTaskState.COMPLETED
                            task.stage = "已完成"
                            task.speed = ""
                            task.error = null
                            deleteWorkspaceAfterExit = true
                        }
                        if (committed) {
                            onTaskNotification?.invoke("视频下载完成", task.title)
                        } else {
                            deleteWorkspaceAfterExit = true
                            task.state = MediaTaskState.CANCELLED
                            task.stage = "已取消"
                            task.speed = ""
                        }
                    }
                    else -> {
                        task.state = MediaTaskState.FAILED
                        task.stage = "失败"
                        task.speed = ""
                        task.error = DesktopMediaEngine.cleanToolError(lines.joinToString("\n"))
                        onTaskNotification?.invoke("视频下载未完成", task.title)
                    }
                }
                task.updatedAt = System.currentTimeMillis()
                persist()
            }
        } catch (_: CancellationException) {
            // User-selected state is applied by pause/cancel/remove.
        } catch (error: Throwable) {
            val action = requestedActions.remove(task.id)
            deleteWorkspaceAfterExit = removals.isRemoved(task.id)
            withContext(Dispatchers.Swing) {
                when {
                    removals.isRemoved(task.id) -> { task.state = MediaTaskState.CANCELLED; task.stage = "已取消" }
                    action == "pause" -> { task.state = MediaTaskState.PAUSED; task.stage = "已暂停" }
                    action == "cancel" -> { task.state = MediaTaskState.CANCELLED; task.stage = "已取消" }
                    else -> { task.state = MediaTaskState.FAILED; task.stage = "失败"; task.error = friendlyError(error) }
                }
                task.speed = ""
                persist()
            }
        } finally {
            process?.let { processes.remove(task.id, it) }
            requestedActions.remove(task.id)
            if (removals.isRemoved(task.id)) deleteWorkspaceAfterExit = true
            if (deleteWorkspaceAfterExit) {
                deleteWorkspace(task, downloadRoot)
                withContext(Dispatchers.Swing) {
                    task.workspaceRoot = null
                    persist()
                }
            }
            removals.finish(task.id)
        }
    }

    private fun stopProcess(id: String) {
        val process = processes[id] ?: return
        // UI actions call this method from Swing. Process shutdown can take up
        // to two seconds, so it must never run on the event-dispatch thread.
        scope.launch { terminateProcess(process) }
    }

    private fun terminateProcess(process: Process) {
        runCatching { process.descendants().forEach { it.destroy() } }
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            runCatching { process.descendants().forEach { it.destroyForcibly() } }
            process.destroyForcibly()
        }
    }

    private fun uniqueOutput(baseName: String, extension: String): File {
        val directory = File(settings.downloadDirectory, "媒体工具")
        directory.mkdirs()
        val safe = DesktopMediaEngine.sanitizeFileName(baseName).ifBlank { "解析成品" }
        var output = File(directory, "$safe.$extension")
        var index = 2
        while (output.exists()) output = File(directory, "$safe ($index).$extension").also { index += 1 }
        return output
    }

    private fun deleteWorkspace(task: DesktopMediaTask, downloadRoot: File) {
        runCatching { DesktopMediaWorkspace.deleteTaskDirectory(downloadRoot, task.id) }
    }

    private fun restoreTasks(): List<DesktopMediaTask> = runCatching {
        if (!storeFile.isFile) return emptyList()
        val decoded = DesktopMediaTaskStoreCodec.decode(storeFile.readText(Charsets.UTF_8))
        legacyPlaintextStoreLoaded = decoded.wasPlaintext
        val array = JSONArray(decoded.json)
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index) }.map { json ->
            DesktopMediaTask(
                sourceUrl = json.optString("sourceUrl"),
                // Never restore a short-lived bearer/signed media URL.
                downloadUrl = "",
                title = json.optString("title", "未命名视频"),
                platform = json.optString("platform", "媒体"),
                formatSelector = json.optString("formatSelector", "bestvideo*+bestaudio/best"),
                formatLabel = json.optString("formatLabel", "公开最高画质"),
                outputFormat = json.optString("outputFormat", "mp4"),
                embedSubtitles = json.optBoolean("embedSubtitles"),
                id = json.optString("id"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            ).apply {
                state = runCatching { MediaTaskState.valueOf(json.optString("state")) }.getOrDefault(MediaTaskState.INTERRUPTED)
                    .let { if (it in setOf(MediaTaskState.DOWNLOADING, MediaTaskState.ANALYZING, MediaTaskState.WAITING)) MediaTaskState.INTERRUPTED else it }
                progress = json.optInt("progress").coerceIn(0, 100)
                stage = if (state == MediaTaskState.INTERRUPTED) "上次退出时中断" else json.optString("stage", state.name)
                error = json.optString("error").takeIf(String::isNotBlank)
                outputFile = json.optString("outputPath").takeIf(String::isNotBlank)?.let(::File)
                workspaceRoot = json.optString("workspaceRoot").takeIf(String::isNotBlank)
                    ?.let(::File)?.let(DesktopMediaWorkspace::normalizeRoot)
                updatedAt = json.optLong("updatedAt", createdAt)
            }
        }.takeLast(2000)
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist() {
        val array = JSONArray(tasks.take(2000).map { task ->
            JSONObject()
                .put("id", task.id)
                .put("sourceUrl", task.sourceUrl)
                .put("title", task.title)
                .put("platform", task.platform)
                .put("formatSelector", task.formatSelector)
                .put("formatLabel", task.formatLabel)
                .put("outputFormat", task.outputFormat)
                .put("embedSubtitles", task.embedSubtitles)
                .put("state", task.state.name)
                .put("progress", task.progress)
                .put("stage", task.stage)
                .put("error", task.error.orEmpty())
                .put("outputPath", task.outputFile?.absolutePath.orEmpty())
                .put("workspaceRoot", task.workspaceRoot?.absolutePath.orEmpty())
                .put("createdAt", task.createdAt)
                .put("updatedAt", task.updatedAt)
        })
        storeFile.parentFile?.mkdirs()
        val temporary = File(storeFile.parentFile, "${storeFile.name}.tmp")
        temporary.writeText(DesktopMediaTaskStoreCodec.encode(array.toString()), Charsets.UTF_8)
        runCatching {
            Files.move(temporary.toPath(), storeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary.toPath(), storeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private var lastPersist = 0L
    private fun persistThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPersist >= 1000L) { lastPersist = now; persist() }
    }

    private fun friendlyError(error: Throwable): String {
        val message = generateSequence(error) { it.cause }.mapNotNull(Throwable::message).firstOrNull(String::isNotBlank).orEmpty()
        return if (message.isBlank()) error.javaClass.simpleName else DesktopMediaEngine.cleanToolError(message)
    }

    companion object {
        private val activeStates = setOf(MediaTaskState.WAITING, MediaTaskState.ANALYZING, MediaTaskState.DOWNLOADING)
    }
}

internal data class DecodedMediaTaskStore(val json: String, val wasPlaintext: Boolean)

/** DPAPI envelope for persisted source links and task metadata. */
internal object DesktopMediaTaskStoreCodec {
    fun encode(json: String): String {
        val encrypted = Crypt32Util.cryptProtectData(json.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decode(value: String): DecodedMediaTaskStore {
        val trimmed = value.trim()
        if (trimmed.startsWith("[")) return DecodedMediaTaskStore(trimmed, wasPlaintext = true)
        val encrypted = Base64.getDecoder().decode(trimmed)
        val json = String(Crypt32Util.cryptUnprotectData(encrypted), StandardCharsets.UTF_8)
        return DecodedMediaTaskStore(json, wasPlaintext = false)
    }
}
