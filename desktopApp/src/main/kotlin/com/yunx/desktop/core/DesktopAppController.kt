package com.yunx.desktop.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.ProxyMode
import com.yunx.app.data.network.model.ShareFile
import com.yunx.desktop.browser.ChromiumCookieImporter
import com.yunx.desktop.download.DesktopDownloader
import com.yunx.desktop.download.DownloadProgress
import com.yunx.desktop.security.CredentialStore
import com.yunx.desktop.security.CredentialKey
import com.yunx.desktop.settings.DesktopSettings
import com.yunx.desktop.settings.DesktopPreset
import com.yunx.desktop.settings.DesktopSupportLinks
import com.yunx.desktop.update.DesktopRelease
import com.yunx.desktop.update.DesktopUpdateService
import com.yunx.desktop.util.DesktopLog
import com.yunx.desktop.system.DiagnosticBundleExporter
import com.yunx.desktop.system.WindowsPowerGuard
import com.yunx.desktop.media.DesktopMediaController
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.UnifiedLinkClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import com.yunx.desktop.system.WindowsWifiConnection
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class AppPage {
    RESOLVE, MEDIA, DOWNLOADS, MINE, TOOLS, LIBRARY, STATUS, ACCOUNTS, CLOUD, APPEARANCE, BACKUP, SPONSOR, SETTINGS;
    val primaryPage: AppPage get() = when (this) {
        RESOLVE, MEDIA -> RESOLVE
        DOWNLOADS -> DOWNLOADS
        else -> MINE
    }
    companion object { val primary = listOf(RESOLVE, DOWNLOADS, MINE) }
}
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
    val downloadKey: Long get() = runCatching { UUID.fromString(id) }
        .map { it.mostSignificantBits xor it.leastSignificantBits }
        .getOrElse { id.hashCode().toLong() and 0xffffffffL }

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
    private val stateStore: DesktopStateStore = DesktopStateStore(),
    private val mediaStoreFile: File = DesktopDataPaths.mediaTasksFile()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configuredNetwork = HttpClients.configure(settings.networkProxyConfig())
    private val resolver = DesktopResolver(credentialStore)
    private val downloader = DesktopDownloader()
    private val updater = DesktopUpdateService(proxyConfig = settings::networkProxyConfig)
    val media = DesktopMediaController(settings, storeFile = mediaStoreFile)
    val cookieImporter = ChromiumCookieImporter()
    private val downloadJobs = ActiveDownloadJobs<String>()
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
    var onTaskNotification: ((String, String) -> Unit)? = null
        set(value) {
            field = value
            media.onTaskNotification = value
        }

    init {
        DesktopLog.d("App", "解析桌面版启动")
        val saved = stateStore.load()
        downloads += saved.tasks.map(DesktopDownloadTask::restore)
        val cutoff = System.currentTimeMillis() - settings.historyRetentionDays * 86_400_000L
        history += saved.history.filter { settings.historyRetentionDays > 0 && it.createdAt >= cutoff }
        favorites += saved.favorites
        diagnostics += defaultDiagnostics()
        media.onActivityChanged = ::updatePowerGuard
        scope.launch {
            while(isActive) {
                delay(5000)
                if(settings.wifiOnly && !WindowsWifiConnection.isConnected()) withContext(Dispatchers.Swing) {
                    downloads.filter { it.state in setOf(TaskState.DOWNLOADING, TaskState.PREPARING, TaskState.WAITING) }.forEach {
                        pauseDownload(it); it.error = "等待 Wi-Fi，连接后点击继续"
                    }
                    media.pauseForWifi()
                }
            }
        }
        persist()
    }

    fun resolve() {
        if (linkText.isBlank() || isResolving) return
        val classified = UnifiedLinkClassifier.classifyText(linkText).firstOrNull()
        if (classified != null && classified.kind != LinkKind.CLOUD_SHARE) {
            media.acceptInput(linkText, autoAnalyze = true)
            page = AppPage.MEDIA
            return
        }
        if (password.isBlank()) password = classified?.passcode.orEmpty()
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

    fun downloadShareSelection(selection: List<ShareFile>) {
        val current = resolved ?: return
        if(isResolving) return
        val source = linkText
        isResolving = true; resolveError = null
        scope.launch {
            try {
                val pending = java.util.ArrayDeque(selection)
                val files = mutableListOf<ShareFile>()
                val seen = mutableSetOf<String>()
                while(pending.isNotEmpty()) {
                    val file = pending.removeFirst()
                    if(!seen.add(file.fid)) continue
                    require(seen.size <= 10000) { "单次最多展开 10000 项，请分批选择" }
                    if(file.isdir) pending.addAll(resolver.listDirectory(current, file.fid))
                    else files += file
                }
                withContext(Dispatchers.Swing) {
                    files.forEach { file ->
                        val task = DesktopDownloadTask(file.fname,sourceLink = source,platform = current.platform.name,
                            fileId = file.fid,fileSize = file.fsize,parentId = file.pdirFid,fidToken = file.fidToken,modifyTime = file.modifyTime)
                        taskContexts[task.id] = current to file; downloads.add(0,task)
                    }
                    persist(); schedule(); page = AppPage.DOWNLOADS
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { withContext(Dispatchers.Swing) { resolveError = friendlyError(e) } }
            finally { withContext(Dispatchers.Swing) { isResolving = false } }
        }
    }

    suspend fun copyTaskLink(task: DesktopDownloadTask): Pair<String, suspend () -> Unit> {
        if(task.sourceLink.startsWith("jiexi-direct:")) return task.sourceLink.removePrefix("jiexi-direct:") to {}
        val file = ShareFile(task.fileId,task.fileName,task.fileSize,false,task.parentId,task.fidToken,task.modifyTime)
        if(task.sourceLink == "jiexi-cloud:" + task.platform) return cloud.download(SharePlatform.valueOf(task.platform),file).downloadUrl to {}
        val current = taskContexts[task.id]?.first ?: resolver.resolve(task.sourceLink,null)
        val link = resolver.getDownloadLink(current,file)
        return link.downloadUrl to { resolver.cleanup(current,link) }
    }

    val cloud = DesktopCloudService(credentialStore)
    var appearanceRevision by mutableStateOf(0)
        private set
    fun applyAppearance(mode: String, accent: String, scale: Float) {
        settings.themeMode = mode; settings.accentHex = accent; settings.fontScale = scale
        appearanceRevision++
    }

    fun downloadCloud(platform: SharePlatform, files: List<ShareFile>) {
        files.filterNot { it.isdir }.forEach { file ->
            downloads.add(0, DesktopDownloadTask(file.fname, sourceLink = "jiexi-cloud:" + platform.name,
                platform = platform.name, fileId = file.fid, fileSize = file.fsize,
                parentId = file.pdirFid, fidToken = file.fidToken, modifyTime = file.modifyTime))
        }
        page = AppPage.DOWNLOADS; persist(); schedule()
    }

    fun addDirectDownload(url: String, name: String) {
        val uri = URI(url.trim())
        require(uri.scheme in setOf("http","https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "请输入有效 HTTP/HTTPS 文件直链" }
        val filename = name.trim().ifBlank { uri.path.substringAfterLast('/').ifBlank { "download.bin" } }
        downloads.add(0, DesktopDownloadTask(filename,sourceLink = "jiexi-direct:" + uri.toASCIIString()))
        page = AppPage.DOWNLOADS; persist(); schedule()
    }

    @Synchronized
    private fun schedule() {
        if(settings.wifiOnly && !WindowsWifiConnection.isConnected()) {
            downloads.filter { it.state == TaskState.WAITING }.forEach { it.state = TaskState.PAUSED; it.error = "等待 Wi-Fi，连接后点击继续" }
            persist()
            return
        }
        val capacity = settings.maxConcurrentTasks - downloadJobs.size
        if (capacity <= 0) return
        downloads.filter { it.state == TaskState.WAITING }
            .sortedWith(compareByDescending<DesktopDownloadTask> { it.priority }.thenBy { it.createdAt })
            .take(capacity).forEach(::launchTask)
    }

    private fun launchTask(task: DesktopDownloadTask) {
        if (downloadJobs.contains(task.id)) return
        task.state = TaskState.PREPARING; task.updatedAt = System.currentTimeMillis(); persist()
        updatePowerGuard()
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            var context: ResolvedShare? = null
            var direct: com.yunx.app.data.network.model.DownloadLink? = null
            try {
                val pair = taskContexts[task.id]
                val file = pair?.second ?: ShareFile(task.fileId, task.fileName, task.fileSize, false, task.parentId, task.fidToken, task.modifyTime)
                val personal = task.sourceLink == "jiexi-cloud:" + task.platform
                val platform: SharePlatform
                val credential: String
                val genericDirect = task.sourceLink.startsWith("jiexi-direct:")
                if (genericDirect) {
                    platform = SharePlatform.C139
                    credential = ""
                    val uri = URI(task.sourceLink.removePrefix("jiexi-direct:"))
                    require(uri.scheme in setOf("http","https") && !uri.host.isNullOrBlank())
                    direct = com.yunx.app.data.network.model.DownloadLink(task.id,task.fileName,uri.toASCIIString(),task.fileSize)
                } else if (personal) {
                    platform = SharePlatform.valueOf(task.platform)
                    credential = cloud.credential(platform)
                    direct = cloud.download(platform, file)
                } else {
                    context = pair?.first ?: resolver.resolve(task.sourceLink, password.takeIf { publicLink(task.sourceLink) == publicLink(linkText) && it.isNotBlank() })
                    platform = context.platform
                    credential = context.credential
                    direct = resolver.getDownloadLink(context, file)
                }
                withContext(Dispatchers.Swing) { task.state = TaskState.DOWNLOADING; task.error = null; task.errorCode = ""; persist() }
                val output = downloader.download(direct, if(genericDirect) emptyMap() else resolver.downloadHeaders(platform, credential), File(downloadDirectory), threadCount,
                    task.downloadKey, settings.speedLimitBytes) { progress ->
                    javax.swing.SwingUtilities.invokeLater { task.progress = progress; task.updatedAt = System.currentTimeMillis(); persistThrottled() }
                }
                withContext(Dispatchers.Swing) {
                    task.outputFile = output; task.state = TaskState.COMPLETED
                    task.progress = DownloadProgress(output.length(), output.length(), 0, "下载完成"); persist()
                    if (!downloader.commit(task.downloadKey, output)) {
                        throw CancellationException("任务已删除，取消提交临时成品")
                    }
                    if (settings.notifyOnCompletion) onTaskNotification?.invoke("下载完成", task.fileName)
                }
            } catch (_: CancellationException) {
                // pause/remove already persisted the final user-selected state
            } catch (error: Throwable) {
                // Cancelling the underlying OkHttp calls can surface as an IO
                // failure just before coroutine cancellation is observed. Do
                // not let that stale failure overwrite PAUSED or a deletion.
                if (job.isCancelled) return@launch
                val failure = DesktopFailureClassifier.classify(error)
                withContext(Dispatchers.Swing) {
                    task.error = friendlyError(error); task.errorCode = failure.first
                    if (failure.second && task.retryCount < settings.retryLimit) {
                        task.retryCount += 1
                        task.state = TaskState.RETRY_WAIT
                    } else {
                        task.state = if (failure.first == "AUTH_EXPIRED") TaskState.NEEDS_REAUTH else if (failure.first == "PASSCODE_REQUIRED") TaskState.NEEDS_INPUT else TaskState.FAILED
                    }
                    persist()
                    if (task.state in setOf(TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT) && settings.notifyOnCompletion) {
                        onTaskNotification?.invoke("下载未完成", "${task.fileName}：${task.error.orEmpty()}")
                    }
                }
                if (task.state == TaskState.RETRY_WAIT) {
                    delay((1_000L shl task.retryCount.coerceIn(0, 5)).coerceAtMost(30_000L))
                    withContext(Dispatchers.Swing) { task.state = TaskState.WAITING; persist() }
                }
            } finally {
                // download() publishes to a unique public path first, but that
                // path remains provisional until the COMPLETED state above is
                // persisted. Pause/failure/cancellation restores only this
                // task's exact file to its hidden resumable path.
                downloader.rollbackProvisional(task.downloadKey)
                direct?.let { link -> context?.let { runCatching { resolver.cleanup(it, link) } } }
                // A cancelled generation remains registered until this point.
                // Only that exact Job may clear the slot; an older generation
                // must never erase a replacement registered for the same task.
                if (downloadJobs.finish(task.id, job)) {
                    taskContexts.remove(task.id)
                    withContext(Dispatchers.Swing) { updatePowerGuard(); schedule() }
                }
            }
        }
        if (downloadJobs.register(task.id, job)) {
            job.start()
        } else {
            job.cancel(CancellationException("任务已有活动下载"))
        }
    }

    fun pauseDownload(task: DesktopDownloadTask) {
        downloader.cancel(task.downloadKey)
        // Keep the cancelled job registered until its finally block finishes.
        // An immediate resume stays WAITING and is scheduled only afterwards.
        downloadJobs.cancel(task.id)
        task.state = TaskState.PAUSED
        persist()
        schedule()
    }
    fun resumeDownload(task: DesktopDownloadTask) { if (task.state != TaskState.COMPLETED) { task.state = TaskState.WAITING; task.error = null; task.retryCount = 0; persist(); schedule() } }
    fun retryDownload(task: DesktopDownloadTask) = resumeDownload(task)
    fun downloadAgain(task: DesktopDownloadTask) {
        val copy = DesktopDownloadTask(task.fileName, sourceLink = task.sourceLink, platform = task.platform,
            fileId = task.fileId, fileSize = task.fileSize, parentId = task.parentId,
            fidToken = task.fidToken, modifyTime = task.modifyTime)
        taskContexts[task.id]?.let { taskContexts[copy.id] = it }
        downloads.add(0, copy)
        persist(); schedule()
    }
    fun pauseAll() {
        downloads.filter { it.state in setOf(TaskState.WAITING, TaskState.PREPARING, TaskState.DOWNLOADING, TaskState.RETRY_WAIT) }.forEach(::pauseDownload)
        media.pauseAll()
    }
    fun resumeAll() {
        downloads.filter { it.state in setOf(TaskState.PAUSED, TaskState.INTERRUPTED, TaskState.FAILED, TaskState.RETRY_WAIT) }.forEach { it.state = TaskState.WAITING }
        media.resumeAll()
        persist()
        schedule()
    }
    fun setPriority(task: DesktopDownloadTask, high: Boolean) { task.priority = if (high) 50 else 0; persist(); schedule() }

    fun removeDownload(task: DesktopDownloadTask) {
        // Tombstone first: if final move has already happened, a concurrently
        // queued completion block can no longer commit the provisional file.
        downloader.beginDiscard(task.downloadKey)
        downloader.cancel(task.downloadKey)
        val active = downloadJobs.cancel(task.id, CancellationException("任务已删除"))
        downloads.remove(task)
        taskContexts.remove(task.id)
        persist()
        schedule()
        scope.launch(Dispatchers.IO) {
            active?.join()
            downloader.discard(task.downloadKey, File(downloadDirectory), task.fileName)
        }
    }

    fun addFavoriteCurrent() {
        val item = resolved ?: return
        if (favorites.none { it.link == publicLink(linkText) }) {
            favorites.add(0, DesktopFavorite(UUID.randomUUID().toString(), publicLink(linkText), item.session.title, item.platform.name, System.currentTimeMillis())); persist()
        }
    }
    fun removeFavorite(item: DesktopFavorite) { favorites.remove(item); persist() }
    fun saveFavorite(link: String, title: String, category: String) {
        val url = UnifiedLinkClassifier.classifyText(link).firstOrNull()?.normalizedUrl ?: error("请输入有效链接")
        val safe = publicLink(url)
        val existing = favorites.indexOfFirst { it.link == safe }
        val value = DesktopFavorite(if(existing >= 0) favorites[existing].id else UUID.randomUUID().toString(),
            safe, title.ifBlank { safe }, UnifiedLinkClassifier.classifyText(link).first().platform.name,
            if(existing >= 0) favorites[existing].createdAt else System.currentTimeMillis(), category.ifBlank { "未分类" })
        if(existing >= 0) favorites[existing] = value else favorites.add(0,value)
        persist()
    }
    fun setFavoriteCategory(item: DesktopFavorite, category: String) {
        val index = favorites.indexOfFirst { it.id == item.id }
        if(index >= 0) { favorites[index] = item.copy(category = category.ifBlank { "未分类" }); persist() }
    }
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
                    else if (DesktopUpdateService.compareVersions(release.version, APP_VERSION) > 0) {
                        downloadedUpdate = null
                        updateRelease = release
                    }
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
            runCatching {
                updater.download(asset, File(settings.downloadDirectory, "更新")) { bytes, total ->
                    val amount = "%.1f".format(java.util.Locale.ROOT, bytes / 1048576.0)
                    val progress = if (total > 0) "${(bytes * 100 / total).coerceIn(0, 100)}% · $amount MB" else "$amount MB"
                    javax.swing.SwingUtilities.invokeLater { if (updateDownloading) updateMessage = "正在下载更新：$progress" }
                }
            }
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
    private fun updateBlockedByTasks(): Boolean = downloadJobs.isNotEmpty || media.hasActiveTasks || media.toolBusy ||
        downloads.any { it.state in setOf(TaskState.WAITING, TaskState.PREPARING, TaskState.DOWNLOADING, TaskState.RETRY_WAIT, TaskState.VERIFYING) }

    fun installUpdate(onReady: (File) -> Unit) {
        if (updateBlockedByTasks()) {
            updateMessage = "请先暂停或完成下载和媒体处理，再退出安装更新。"
            return
        }
        val file = downloadedUpdate ?: return
        val asset = updateRelease?.let(DesktopUpdateService::windowsInstaller) ?: return
        updateDownloading = true
        scope.launch {
            val verified = runCatching { DesktopUpdateService.verifiedFile(asset, file) }.getOrDefault(false)
            withContext(Dispatchers.Swing) {
                updateDownloading = false
                if (!verified) {
                    downloadedUpdate = null
                    updateMessage = "更新包已变动，请重新下载并校验。"
                } else if (updateBlockedByTasks()) {
                    updateMessage = "请先暂停或完成下载和媒体处理，再退出安装更新。"
                } else {
                    persist()
                    onReady(file)
                }
            }
        }
    }
    fun saveGitHubRepository(url: String) { settings.githubRepositoryUrl = url }
    fun openGitHubFeedback(): Boolean {
        val url = settings.githubIssuesUrl() ?: updater.defaultFeedbackUrl ?: return false
        openUrl(url)
        return true
    }
    fun openSupportEmail(): Boolean {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        val uri = DesktopSupportLinks.feedbackEmailUri()
        when {
            desktop.isSupported(Desktop.Action.MAIL) -> desktop.mail(uri)
            desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(uri)
            else -> return false
        }
        return true
    }
    fun openDiagnosticLogs() {
        DesktopLog.directory.mkdirs()
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(DesktopLog.directory)
    }

    fun exportDiagnosticBundle(): File = DiagnosticBundleExporter.export(
        File(settings.downloadDirectory, "诊断"), APP_VERSION, downloads.toList()
    )

    fun saveSettings(directory: String, threads: Int) {
        val dir = File(directory).absoluteFile; dir.mkdirs(); require(dir.isDirectory && dir.canWrite()) { "下载目录不可写" }
        downloadDirectory = dir.absolutePath; threadCount = threads.coerceIn(1, 64); settings.downloadDirectory = dir; settings.threadCount = threadCount
    }
    fun applyPreset(preset: DesktopPreset) {
        settings.preset = preset
        threadCount = settings.threadCount
        schedule()
    }
    fun setReduceMotion(enabled: Boolean) { settings.reduceMotion = enabled; appearanceRevision++ }
    fun saveDesktopIntegration(closeToTray: Boolean, notifications: Boolean, preventSleep: Boolean) {
        settings.closeToTray = closeToTray
        settings.notifyOnCompletion = notifications
        settings.preventSleepWhileDownloading = preventSleep
        updatePowerGuard()
    }
    fun saveProxy(mode: ProxyMode, host: String, port: Int) {
        val config = com.yunx.app.data.network.NetworkProxyConfig(mode, host.trim(), port)
        settings.proxyMode = mode
        settings.proxyHost = host
        settings.proxyPort = port
        HttpClients.configure(config)
    }
    fun openFile(file: File) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file) }
    fun openFolder(file: File) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.parentFile ?: file) }
    fun openDirectory(directory: File) { directory.mkdirs(); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(directory) }
    fun openUrl(url: String) { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }

    private var lastPersist = 0L
    private fun persistThrottled() { val now = System.currentTimeMillis(); if (now - lastPersist > 1000) { lastPersist = now; persist() } }
    private fun persist() = stateStore.save(DesktopStateStore.State(downloads.map { it.persisted() }, history.toList(), favorites.toList()))
    private fun trimHistory() { val max = 500; while (history.size > max) history.removeLast() }
    private fun updatePowerGuard() = WindowsPowerGuard.setDownloading(
        settings.preventSleepWhileDownloading && (downloadJobs.isNotEmpty || media.hasActiveTasks)
    )
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

    companion object { val APP_VERSION: String get() = DesktopBuildInfo.version }
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
