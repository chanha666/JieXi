package com.yunx.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.ShareFile
import com.yunx.desktop.browser.ChromiumCookieImporter
import com.yunx.desktop.download.DesktopDownloader
import com.yunx.desktop.download.DownloadProgress
import com.yunx.desktop.security.CredentialStore
import com.yunx.desktop.security.CredentialKey
import com.yunx.desktop.settings.DesktopSettings
import com.yunx.desktop.settings.DesktopPreset
import com.yunx.desktop.update.DesktopRelease
import com.yunx.desktop.update.DesktopUpdateService
import com.yunx.desktop.util.DesktopLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class AppPage { RESOLVE, DOWNLOADS, LIBRARY, STATUS, ACCOUNTS, SPONSOR, SETTINGS }
enum class TaskState { WAITING, PREPARING, DOWNLOADING, PAUSED, RETRY_WAIT, INTERRUPTED, NEEDS_REAUTH, NEEDS_INPUT, VERIFYING, COMPLETED, FAILED, CANCELLED }

class DesktopDownloadTask(
    val fileName: String,
    val id: String = UUID.randomUUID().toString(),
    val sourceLink: String = "",
    val platform: String = "",
    val fileId: String = "",
    val fileSize: Long = -1,
    val parentId: String = "",
    val fidToken: String = "",
    val modifyTime: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    var state by mutableStateOf(TaskState.WAITING)
    var progress by mutableStateOf(DownloadProgress(0, fileSize, 0, "等待中"))
    var error by mutableStateOf<String?>(null)
    var errorCode by mutableStateOf("")
    var retryCount by mutableStateOf(0)
    var outputFile by mutableStateOf<File?>(null)
    var priority by mutableStateOf(0)
    var updatedAt by mutableStateOf(System.currentTimeMillis())
    val downloadKey: Long get() = id.hashCode().toLong() and 0xffffffffL

    fun persisted() = PersistedTask(
        id, fileName, sourceLink, platform, fileId, fileSize, parentId, fidToken, modifyTime,
        state.name, priority, progress.downloaded, progress.total, outputFile?.absolutePath.orEmpty(),
        errorCode, error.orEmpty(), retryCount, createdAt, updatedAt
    )

    companion object {
        fun restore(value: PersistedTask) = DesktopDownloadTask(
            value.fileName, value.id, value.sourceLink, value.platform, value.fileId, value.fileSize,
            value.parentId, value.fidToken, value.modifyTime, value.createdAt
        ).apply {
            state = TaskState.entries.firstOrNull { it.name == value.state }
                ?.takeUnless { it in setOf(TaskState.PREPARING, TaskState.DOWNLOADING, TaskState.VERIFYING, TaskState.RETRY_WAIT) }
                ?: TaskState.INTERRUPTED
            priority = value.priority
            progress = DownloadProgress(value.downloaded, value.total, 0, if (state == TaskState.INTERRUPTED) "等待恢复" else state.name)
            outputFile = value.outputPath.takeIf(String::isNotBlank)?.let(::File)
            errorCode = value.errorCode
            error = value.errorMessage.takeIf(String::isNotBlank)
            retryCount = value.retryCount
            updatedAt = value.updatedAt
        }
    }
}

data class PlatformDiagnostic(val name: String, val support: String, val account: String, val endpoint: String, val result: String = "未检测")

class DesktopAppController(
    val credentialStore: CredentialStore = CredentialStore(),
    val settings: DesktopSettings = DesktopSettings(),
    private val stateStore: DesktopStateStore = DesktopStateStore()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolver = DesktopResolver(credentialStore)
    private val downloader = DesktopDownloader()
    private val updater = DesktopUpdateService()
    val cookieImporter = ChromiumCookieImporter()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val taskContexts = ConcurrentHashMap<String, Pair<ResolvedShare, ShareFile>>()

    var page by mutableStateOf(AppPage.RESOLVE)
    var linkText by mutableStateOf("")
    var password by mutableStateOf("")
    var isResolving by mutableStateOf(false)
    var resolveError by mutableStateOf<String?>(null)
    var resolved by mutableStateOf<ResolvedShare?>(null)
    var currentFiles by mutableStateOf<List<ShareFile>>(emptyList())
    var path by mutableStateOf<List<Pair<String, String>>>(emptyList())
    val downloads = mutableStateListOf<DesktopDownloadTask>()
    val history = mutableStateListOf<DesktopHistory>()
    val favorites = mutableStateListOf<DesktopFavorite>()
    val diagnostics = mutableStateListOf<PlatformDiagnostic>()
    var diagnosticRunning by mutableStateOf(false)
    var downloadDirectory by mutableStateOf(settings.downloadDirectory.absolutePath)
    var threadCount by mutableStateOf(settings.threadCount)
    var updateChecking by mutableStateOf(false)
    var updateRelease by mutableStateOf<DesktopRelease?>(null)
    var updateMessage by mutableStateOf<String?>(null)
    var updateDownloading by mutableStateOf(false)
    var downloadedUpdate by mutableStateOf<File?>(null)
    val updateConfigured: Boolean get() = updater.configured

    init {
        DesktopLog.d("App", "解析桌面版启动")
        val saved = stateStore.load()
        downloads += saved.tasks.map(DesktopDownloadTask::restore)
        val cutoff = System.currentTimeMillis() - settings.historyRetentionDays * 86_400_000L
        history += saved.history.filter { settings.historyRetentionDays > 0 && it.createdAt >= cutoff }
        favorites += saved.favorites
        diagnostics += defaultDiagnostics()
        persist()
    }

    fun resolve() {
        if (linkText.isBlank() || isResolving) return
        isResolving = true
        resolveError = null
        scope.launch {
            runCatching { resolver.resolve(linkText, password.takeIf(String::isNotBlank)) }
                .onSuccess { result -> withContext(Dispatchers.Swing) {
                    resolved = result; currentFiles = result.files; path = emptyList(); isResolving = false
                    history.add(0, DesktopHistory(UUID.randomUUID().toString(), publicLink(linkText), result.session.title, result.platform.name, System.currentTimeMillis()))
                    trimHistory(); persist()
                }}
                .onFailure { error -> withContext(Dispatchers.Swing) { resolveError = friendlyError(error); isResolving = false } }
        }
    }

    fun clearResolution() { resolved = null; currentFiles = emptyList(); path = emptyList(); resolveError = null }

    fun openFolder(file: ShareFile) {
        val current = resolved ?: return
        if (!file.isdir || isResolving) return
        isResolving = true; resolveError = null
        scope.launch {
            runCatching { resolver.listDirectory(current, file.fid) }
                .onSuccess { files -> withContext(Dispatchers.Swing) { path = path + (file.fname to file.fid); currentFiles = files; isResolving = false } }
                .onFailure { error -> withContext(Dispatchers.Swing) { resolveError = friendlyError(error); isResolving = false } }
        }
    }

    fun goToPath(index: Int) {
        val current = resolved ?: return
        if (isResolving) return
        val nextPath = if (index < 0) emptyList() else path.take(index + 1)
        val directoryId = nextPath.lastOrNull()?.second ?: if (current.platform == SharePlatform.BAIDU) "" else "0"
        isResolving = true
        scope.launch {
            runCatching { resolver.listDirectory(current, directoryId) }
                .onSuccess { files -> withContext(Dispatchers.Swing) { path = nextPath; currentFiles = files; isResolving = false } }
                .onFailure { error -> withContext(Dispatchers.Swing) { resolveError = friendlyError(error); isResolving = false } }
        }
    }

    fun download(file: ShareFile) {
        val current = resolved ?: return
        if (file.isdir) return
        val task = DesktopDownloadTask(file.fname, sourceLink = linkText, platform = current.platform.name, fileId = file.fid,
            fileSize = file.fsize, parentId = file.pdirFid, fidToken = file.fidToken, modifyTime = file.modifyTime)
        taskContexts[task.id] = current to file
        downloads.add(0, task); page = AppPage.DOWNLOADS; persist(); schedule()
    }

    @Synchronized
    private fun schedule() {
        val capacity = settings.maxConcurrentTasks - downloadJobs.size
        if (capacity <= 0) return
        downloads.filter { it.state == TaskState.WAITING }
            .sortedWith(compareByDescending<DesktopDownloadTask> { it.priority }.thenBy { it.createdAt })
            .take(capacity).forEach(::launchTask)
    }

    private fun launchTask(task: DesktopDownloadTask) {
        if (downloadJobs.containsKey(task.id)) return
        task.state = TaskState.PREPARING; task.updatedAt = System.currentTimeMillis(); persist()
        val job = scope.launch {
            var context: ResolvedShare? = null
            var direct: com.yunx.app.data.network.model.DownloadLink? = null
            try {
                val pair = taskContexts[task.id]
                context = pair?.first ?: resolver.resolve(task.sourceLink, password.takeIf { publicLink(task.sourceLink) == publicLink(linkText) && it.isNotBlank() })
                val file = pair?.second ?: ShareFile(task.fileId, task.fileName, task.fileSize, false, task.parentId, task.fidToken, task.modifyTime)
                direct = resolver.getDownloadLink(context, file)
                withContext(Dispatchers.Swing) { task.state = TaskState.DOWNLOADING; task.error = null; task.errorCode = ""; persist() }
                val output = downloader.download(direct, resolver.downloadHeaders(context.platform, context.credential), File(downloadDirectory), threadCount,
                    task.downloadKey, settings.speedLimitBytes) { progress ->
                    javax.swing.SwingUtilities.invokeLater { task.progress = progress; task.updatedAt = System.currentTimeMillis(); persistThrottled() }
                }
                withContext(Dispatchers.Swing) { task.outputFile = output; task.state = TaskState.COMPLETED; task.progress = DownloadProgress(output.length(), output.length(), 0, "下载完成"); persist() }
            } catch (_: CancellationException) {
                // pause/remove already persisted the final user-selected state
            } catch (error: Throwable) {
                val failure = DesktopFailureClassifier.classify(error)
                withContext(Dispatchers.Swing) {
                    task.error = friendlyError(error); task.errorCode = failure.first
                    if (failure.second && task.retryCount < 3) {
                        task.retryCount += 1
                        task.state = TaskState.RETRY_WAIT
                    } else {
                        task.state = if (failure.first == "AUTH_EXPIRED") TaskState.NEEDS_REAUTH else if (failure.first == "PASSCODE_REQUIRED") TaskState.NEEDS_INPUT else TaskState.FAILED
                    }
                    persist()
                }
                if (task.state == TaskState.RETRY_WAIT) {
                    delay((1_000L shl task.retryCount.coerceIn(0, 5)).coerceAtMost(30_000L))
                    withContext(Dispatchers.Swing) { task.state = TaskState.WAITING; persist() }
                }
            } finally {
                direct?.let { link -> context?.let { runCatching { resolver.cleanup(it, link) } } }
                taskContexts.remove(task.id); downloadJobs.remove(task.id)
                withContext(Dispatchers.Swing) { schedule() }
            }
        }
        downloadJobs[task.id] = job
    }

    fun pauseDownload(task: DesktopDownloadTask) {
        downloader.cancel(task.downloadKey); downloadJobs.remove(task.id)?.cancel(); task.state = TaskState.PAUSED; persist(); schedule()
    }
    fun resumeDownload(task: DesktopDownloadTask) { if (task.state != TaskState.COMPLETED) { task.state = TaskState.WAITING; task.error = null; task.retryCount = 0; persist(); schedule() } }
    fun retryDownload(task: DesktopDownloadTask) = resumeDownload(task)
    fun pauseAll() = downloads.filter { it.state in setOf(TaskState.WAITING, TaskState.PREPARING, TaskState.DOWNLOADING, TaskState.RETRY_WAIT) }.forEach(::pauseDownload)
    fun resumeAll() { downloads.filter { it.state in setOf(TaskState.PAUSED, TaskState.INTERRUPTED, TaskState.FAILED, TaskState.RETRY_WAIT) }.forEach { it.state = TaskState.WAITING }; persist(); schedule() }
    fun setPriority(task: DesktopDownloadTask, high: Boolean) { task.priority = if (high) 50 else 0; persist(); schedule() }

    fun removeDownload(task: DesktopDownloadTask) {
        downloader.cancel(task.downloadKey); downloadJobs.remove(task.id)?.cancel(CancellationException("任务已删除")); downloads.remove(task); taskContexts.remove(task.id); persist(); schedule()
    }

    fun addFavoriteCurrent() {
        val item = resolved ?: return
        if (favorites.none { it.link == publicLink(linkText) }) {
            favorites.add(0, DesktopFavorite(UUID.randomUUID().toString(), publicLink(linkText), item.session.title, item.platform.name, System.currentTimeMillis())); persist()
        }
    }
    fun removeFavorite(item: DesktopFavorite) { favorites.remove(item); persist() }
    fun clearHistory() { history.clear(); persist() }
    fun resolveSaved(link: String) { linkText = link; password = ""; page = AppPage.RESOLVE; resolve() }

    fun runDiagnostics() {
        if (diagnosticRunning) return
        diagnosticRunning = true
        DesktopLog.d("Diagnostics", "开始平台连通性诊断")
        diagnostics.indices.forEach { i -> diagnostics[i] = diagnostics[i].copy(result = "检测中") }
        scope.launch {
            diagnostics.toList().forEachIndexed { index, item ->
                val result = runCatching {
                    val connection = URI(item.endpoint).toURL().openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }
                    connection.connect(); "网络正常"
                }.getOrElse { "网络不可达" }
                withContext(Dispatchers.Swing) { diagnostics[index] = item.copy(result = result) }
            }
            withContext(Dispatchers.Swing) { diagnosticRunning = false }
            DesktopLog.d("Diagnostics", "平台连通性诊断完成")
        }
    }

    fun checkForUpdates() {
        if (updateChecking) return
        if (!updater.configured) {
            updateMessage = "安全更新通道未配置"
            return
        }
        updateChecking = true
        updateMessage = null
        scope.launch {
            runCatching { updater.check() }
                .onSuccess { release -> withContext(Dispatchers.Swing) {
                    updateChecking = false
                    if (release == null) updateMessage = "未获取到更新信息"
                    else if (DesktopUpdateService.compareVersions(release.version, APP_VERSION) > 0) updateRelease = release
                    else updateMessage = "当前已是最新版本"
                }}
                .onFailure { error ->
                    DesktopLog.w("Update", "检查更新失败：${error.javaClass.simpleName}")
                    withContext(Dispatchers.Swing) { updateChecking = false; updateMessage = friendlyError(error) }
                }
        }
    }

    fun downloadUpdate(release: DesktopRelease) {
        if (updateDownloading) return
        val asset = DesktopUpdateService.windowsInstaller(release)
        if (asset == null) { updateMessage = "发布中没有 Windows 安装包"; return }
        updateDownloading = true
        updateMessage = "正在下载并校验更新包…"
        scope.launch {
            runCatching { updater.download(asset, File(settings.downloadDirectory, "更新")) }
                .onSuccess { file -> withContext(Dispatchers.Swing) {
                    updateDownloading = false; downloadedUpdate = file
                    updateMessage = "更新包已通过 SHA-256 校验"
                }}
                .onFailure { error ->
                    DesktopLog.w("Update", "下载更新失败：${error.javaClass.simpleName}")
                    withContext(Dispatchers.Swing) { updateDownloading = false; updateMessage = friendlyError(error) }
                }
        }
    }

    fun dismissUpdate() { updateRelease = null }
    fun saveGitHubRepository(url: String) { settings.githubRepositoryUrl = url }
    fun openGitHubFeedback(): Boolean {
        val url = settings.githubIssuesUrl() ?: updater.defaultFeedbackUrl ?: return false
        openUrl(url)
        return true
    }
    fun openDiagnosticLogs() {
        DesktopLog.directory.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(DesktopLog.directory)
    }

    fun saveSettings(directory: String, threads: Int) {
        val dir = File(directory).absoluteFile; dir.mkdirs(); require(dir.isDirectory && dir.canWrite()) { "下载目录不可写" }
        downloadDirectory = dir.absolutePath; threadCount = threads.coerceIn(1, 64); settings.downloadDirectory = dir; settings.threadCount = threadCount
    }
    fun applyPreset(preset: DesktopPreset) {
        settings.preset = preset
        threadCount = settings.threadCount
        schedule()
    }
    fun setReduceMotion(enabled: Boolean) { settings.reduceMotion = enabled }
    fun openFile(file: File) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
    fun openFolder(file: File) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.parentFile ?: file) }
    fun openUrl(url: String) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }

    private var lastPersist = 0L
    private fun persistThrottled() { val now = System.currentTimeMillis(); if (now - lastPersist > 1000) { lastPersist = now; persist() } }
    private fun persist() = stateStore.save(DesktopStateStore.State(downloads.map { it.persisted() }, history.toList(), favorites.toList()))
    private fun trimHistory() { val max = 500; while (history.size > max) history.removeLast() }
    private fun publicLink(value: String): String = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(value)?.value?.substringBefore('?') ?: value.substringBefore('?').trim()
    private fun friendlyError(error: Throwable): String = generateSequence(error) { it.cause }.mapNotNull(Throwable::message).firstOrNull(String::isNotBlank) ?: error.javaClass.simpleName

    private fun defaultDiagnostics() = listOf(
        PlatformDiagnostic("夸克", "实验", account(CredentialKey.QUARK_COOKIE), "https://pan.quark.cn"),
        PlatformDiagnostic("UC", "实验", account(CredentialKey.UC_COOKIE), "https://drive.uc.cn"),
        PlatformDiagnostic("迅雷", "实验", account(CredentialKey.XUNLEI_ACCESS_TOKEN), "https://pan.xunlei.com"),
        PlatformDiagnostic("百度", "实验", account(CredentialKey.BAIDU_COOKIE), "https://pan.baidu.com"),
        PlatformDiagnostic("139", "实验", account(CredentialKey.C139_COOKIE), "https://yun.139.com"),
        PlatformDiagnostic("123", "实验", account(CredentialKey.PAN123_TOKEN), "https://www.123pan.com")
    )
    private fun account(key: CredentialKey) = if (credentialStore.has(key)) "待验证" else "未配置"

    companion object { const val APP_VERSION = "3.0.2" }
}

private object DesktopFailureClassifier {
    fun classify(error: Throwable): Pair<String, Boolean> {
        val value = generateSequence(error) { it.cause }.mapNotNull(Throwable::message).joinToString(" ").lowercase()
        return when {
            "401" in value || "登录" in value || "cookie" in value -> "AUTH_EXPIRED" to false
            "提取码" in value || "password" in value -> "PASSCODE_REQUIRED" to false
            "403" in value || "直链" in value -> "DIRECT_LINK_EXPIRED" to true
            "429" in value || "503" in value || "timeout" in value || "reset" in value -> "NETWORK_RETRYABLE" to true
            "空间" in value || "no space" in value -> "DISK_FULL" to false
            else -> "DOWNLOAD_FAILED" to true
        }
    }
}
