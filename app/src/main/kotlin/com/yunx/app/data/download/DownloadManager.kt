package com.yunx.app.data.download

import android.content.Context
import android.util.Log
import com.yunx.app.util.LogRedactor
import com.yunx.app.data.db.DownloadTaskDao
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import com.yunx.app.data.security.CredentialCipher
import com.yunx.app.data.update.ApkUpdateVerifier
import com.yunx.app.data.update.UpdateIntegrity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

/** 实时下载统计（用于 UI 展示速度/剩余时间/线程数） */
data class DownloadStats(
    val speed: Long = 0L,        // 字节/秒
    val remainMillis: Long = -1L, // 剩余时间（毫秒），未知为 -1
    val chunkCount: Int = 1       // 分片（线程）数
)

private const val TAG = "YunX-DL"

/** 单文件 Range 分片的安全并发上限。迅雷等 CDN 对单文件并发 Range 有阈值，
 *  超过约 8 个并发会把多余请求降级为 200 整文件（忽略 Range），
 *  进而触发整任务回退单流、速度暴跌。压在安全上限内，所有分片都能稳定拿到 206。 */
private const val RANGE_WORKERS_CAP = 8

/** 错峰建连上限（序号）：第 i 个分片首次请求前延迟 (min(i, STAGGER_CAP) * STAGGER_MS) */
private const val STAGGER_CAP = 8
private const val STAGGER_MS = 25L

/** RANGE_IGNORED 容忍次数：CDN 偶发 200（限流中间态）前 N 次不触发整任务回退，继续领新片；超过才回退单流 */
private const val RANGE_IGNORED_TOLERANCE = 3

/** 重试区间（主池 part_i 或弹性区间 seg_{start}_{end}） */
private data class RetryRange(val start: Long, val end: Long, val file: File)

/**
 * 弹性区分配器：按字节顺序领取固定大小块（默认 4MB），保证线程拿到的区间**物理相邻**。
 * 替代"中点劈分"——劈分（先大后小）导致主池耗尽瞬间全部线程涌入弹性区、区间跨度翻倍、
 * 连接复用率崩塌（中后段掉速根因）；按序分配则线程逐个平滑转入弹性区，并发形态不突变。
 */
private class ElasticAllocator(
    private val total: Long,
    private val elasticStart: Long
) {
    private val lock = Any()
    private var nextStart = elasticStart

    /** 领取下一个弹性块（按字节顺序，块大小 DEFAULT_ELASTIC_BLOCK；不足 4MB 的尾部整块领取） */
    fun take(): LongRange? = synchronized(lock) {
        if (nextStart >= total) return null
        val s = nextStart
        val e = minOf(s + DEFAULT_ELASTIC_BLOCK - 1, total - 1)
        nextStart = e + 1
        s..e
    }

    /** 断点续传：跳过已下载前缀（nextStart 只前进） */
    fun skipTo(start: Long) = synchronized(lock) {
        if (start > nextStart) nextStart = start
    }

    companion object {
        /** 弹性块大小：4MB（可调；CDN 对同区间并发敏感可降 2MB，单连接限速严重可升 8MB） */
        const val DEFAULT_ELASTIC_BLOCK = 4 * 1024 * 1024L
    }
}

/**
 * 下载任务管理器：
 * - 任务持久化（Room），状态流转 PENDING → DOWNLOADING → COMPLETED / PAUSED / FAILED；
 * - 分片多线程下载（每片一个协程，信号量限并发）；
 * - 断点续传：part 文件保留，暂停/重启后从已有大小继续；
 * - 完成后合并分片并保存到公共 Download 目录。
 */
class DownloadManager(
    private val context: Context,
    private val dao: DownloadTaskDao,
    private val downloader: ChunkDownloader,
    /** 下载线程数提供者（按平台，可在设置中修改，动态生效），默认 32 */
    private val threadProvider: (String) -> Int = { 32 },
    /** 自定义下载保存目录提供者（SAF tree Uri，可空）；null 时保存到系统默认 Download */
    private val saveDirProvider: () -> String? = { null },
    /** 最大同时下载任务数提供者（默认 3）：限制后台并发任务，避免占满带宽/耗尽路由器连接 */
    private val concurrencyProvider: () -> Int = { 3 },
    /** 全局下载速度限制提供者（字节/秒；0 = 不限速） */
    private val speedLimitProvider: () -> Long = { 0L },
    /** 下载失败后自动重试次数提供者（默认 3，上限 10） */
    private val retryCountProvider: () -> Int = { 3 },
    /** 锁屏后保持下载开关（开启时获取 WakeLock 维持 Wi-Fi/CPU） */
    private val keepWhenLockedProvider: () -> Boolean = { true },
    /** 通知栏显示下载速度开关（false 时仅显示通知，隐藏速度） */
    private val showSpeedProvider: () -> Boolean = { true },
    /** 返回 null 表示允许下载；非空原因会把任务安全暂停。 */
    private val conditionBlocker: () -> String? = { null }
) {
    private val credentialCipher: CredentialCipher = AndroidKeystoreCredentialCipher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 当前实际下载中的任务数（用于最大同时下载任务数限制） */
    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    /** 全局限速器（令牌桶）：所有任务合计不超过 speedLimitProvider 的字节/秒 */
    private val speedLimiter = SpeedLimiter()

    /**
     * 保存前存储权限检查（Android 9- 写公共 Download 需 WRITE_EXTERNAL_STORAGE 运行时授权）。
     * UI 层注入：无权限时动态申请并等待授权结果；已授权/Android 10+ 直接返回 true。
     * 授权后会自动继续保存（同一协程 await 授权结果再往下走）。
     */
    var storagePermissionProvider: suspend () -> Boolean = { true }

    /**
     * 同一任务的所有开始/暂停/删除命令都串到一条 tail 上，并复用同一把 IO 互斥锁。
     * generation 负责 identity-CAS：旧协程的 finally/状态写入不能命中新一代任务。
     */
    private class TaskControl {
        val generation = TaskRunGeneration()
        val ioMutex = Mutex()
        var active: ActiveRun? = null
        var tail: Job? = null
    }

    private data class ActiveRun(val lease: TaskRunGeneration.Lease, val job: Job)

    private class RunLeaseContext(
        val control: TaskControl,
        val lease: TaskRunGeneration.Lease
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<RunLeaseContext>
    }

    private val taskControls = ConcurrentHashMap<Long, TaskControl>()

    /** 前台服务计数：有任务在下载时保持前台（避免切后台限速/进程被杀） */
    private val activeTaskCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** 前台通知进度节流（毫秒）：2 秒更新一次，避免频繁刷新系统通知 */
    private val notifyThrottleMs = 2000L
    private val lastNotifyTs = AtomicLong(0)

    /** 更新前台通知进度（2 秒节流；total<=0 时不确定进度，只更新标题；可显示下载速度） */
    private fun notifyProgress(id: Long, fileName: String, new: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTs.get() >= notifyThrottleMs) {
            lastNotifyTs.set(now)
            val percent = if (total > 0) ((new * 100 / total).toInt().coerceIn(0, 100)) else -1
            val speed = _stats.value[id]?.speed ?: 0L
            val speedText = if (speed > 0) formatSpeed(speed) else ""
            DownloadService.update(context, id, fileName, percent, speedText, showSpeedProvider())
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSec.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[i])
    }

    /**
     * 进度落盘节流：多 worker 并发回调下，每 progressPersistIntervalMs 最多写一次 DB。
     * - force / (total>0 且 new>=total)：完成时强制写，确保最终进度准确；
     * - total<=0（大小未知）时仅按时间节流；
     * - 用 lastAt 的 CAS 保证并发下同一任务只有一个回调写库（避免多线程重复 UPDATE）。
     */
    private suspend fun persistProgressIfDue(
        id: Long,
        new: Long,
        total: Long,
        force: Boolean,
        lastAt: AtomicLong
    ) {
        if (!isTaskActive()) return
        val now = System.currentTimeMillis()
        val last = lastAt.get()
        if (force || (total > 0 && new >= total) || now - last >= progressPersistIntervalMs) {
            if (lastAt.compareAndSet(last, now)) {
                if (isTaskActive()) {
                    dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, new, total)
                }
            }
        }
    }

    /** 完成任务并写入平均速度（字节/秒）：avg = total / 本次运行耗时 */
    private suspend fun completeWithAvg(id: Long, savedPath: String, total: Long): Boolean {
        if (!isTaskActive()) return false
        val start = taskStartTimes.remove(id) ?: 0L
        val elapsedSec = ((System.currentTimeMillis() - start) / 1000.0).coerceAtLeast(1.0)
        val avg = if (total > 0 && elapsedSec > 0) (total / elapsedSec).toLong() else 0L
        if (!isTaskActive()) return false
        // Once the public file exists, make the DB commit non-cancellable. If a
        // pause/remove races this commit, pendingSavedPaths lets the queued
        // command deterministically keep or delete the public file afterwards.
        withContext(NonCancellable) {
            dao.complete(id, DownloadTaskEntity.STATUS_COMPLETED, savedPath, avg)
        }
        return isTaskActive()
    }

    /** 任务请求头（Cookie/UA），暂停后恢复仍需使用 */
    private val taskHeaders = ConcurrentHashMap<Long, Map<String, String>>()

    /** 已知文件大小（API 返回，避免探测失败）；-1 表示未知 */
    private val taskSizes = ConcurrentHashMap<Long, Long>()

    /** 任务开始时间（毫秒）：完成时计算平均速度用（暂停/恢复会重置，表示最近一次运行段均值） */
    private val taskStartTimes = ConcurrentHashMap<Long, Long>()

    /** 任务下载完成后的清理回调（如删除网盘临时转存文件；下载成功后才触发） */
    private val taskCallbacks = ConcurrentHashMap<Long, suspend () -> Unit>()

    /** 已创建但尚未由当前 lease 完成提交的公共文件；暂停/删除命令负责最终收口。 */
    private val pendingSavedPaths = ConcurrentHashMap<Long, String>()

    /** 实时下载统计（速度/剩余时间/线程数） */
    private val _stats = MutableStateFlow<Map<Long, DownloadStats>>(emptyMap())
    val stats: StateFlow<Map<Long, DownloadStats>> = _stats.asStateFlow()

    /** 进度落盘节流（毫秒）：updateProgress 写库会触发全表 Flow 重发 → 主线程全列表重组；
     *  按字节（256KB）节流时高速下载每秒写库几十次，主线程重组洪峰 → ANR。
     *  改为按时间节流落盘，UI 进度由内存 _stats 高频展示、DB 低频持久化（断点续传最多丢几百 ms 进度）。 */
    private val progressPersistIntervalMs = 500L

    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeAll()

    /** 入队并立即开始下载 */
    suspend fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        /** 已知文件大小（字节）；-1 表示未知，需探测 */
        size: Long = -1L,
        /** 下载来源平台标识（按平台应用下载线程数设置）；通用/手动添加传空串 */
        platform: String = "",
        /** 作者签名更新清单中的 SHA-256；普通下载留空。 */
        expectedSha256: String = "",
        /** 下载成功完成后的清理回调（如删除网盘临时转存文件）；失败/取消不触发 */
        onComplete: suspend () -> Unit = {}
    ): Long {
        // 文件名兜底：空白时从 URL 推导，避免保存时变成时间戳
        val safeName = fileName.ifBlank {
            url.substringAfterLast('/').substringBefore('?')
                .ifBlank { "download_${System.currentTimeMillis()}" }
        }
        val normalizedExpectedSha256 = expectedSha256.trim().let { value ->
            if (value.isBlank()) "" else requireNotNull(UpdateIntegrity.normalizeExpectedSha256(value)) {
                "更新包缺少有效的 SHA-256 校验值"
            }
        }
        Log.d(TAG, "enqueue: origin=${LogRedactor.url(url)} fileName=$safeName headers=${headers.keys} size=$size")
        val id = dao.insert(
            DownloadTaskEntity(
                url = url,
                fileName = safeName,
                requestHeadersJson = encodeHeaders(headers),
                platform = platform,
                expectedSha256 = normalizedExpectedSha256
            )
        )
        // 保存请求头（Cookie/UA），暂停后恢复仍需携带
        if (headers.isNotEmpty()) taskHeaders[id] = headers
        if (size > 0) taskSizes[id] = size
        taskCallbacks[id] = onComplete
        start(id, headers)
        return id
    }

    /**
     * 重新下载：用原直链新建任务（任务卡长按菜单「重新下载」）。
     * 先做 Range 探测校验直链有效性：403/404/网络错误视为直链已过期，返回 false 由 UI 提示。
     */
    suspend fun redownload(id: Long): Boolean {
        val task = dao.get(id) ?: return false
        val headers = loadPersistedHeaders(id)
        val valid = runCatching { downloader.getTotalSize(task.url, headers) != null }.getOrDefault(false)
        if (!valid) return false
        enqueue(task.url, task.fileName, headers, task.totalSize, task.platform, task.expectedSha256)
        return true
    }

    /** 开始/恢复下载（断点续传） */
    fun start(id: Long, headers: Map<String, String> = emptyMap()) {
        // 恢复时未传 headers：沿用入队时保存的（Cookie/UA 对直链下载是必需的）
        val effectiveHeaders = headers.ifEmpty { taskHeaders[id] ?: emptyMap() }
        Log.d(TAG, "start: id=$id headers=${effectiveHeaders.keys}")
        val control = taskControls.getOrPut(id) { TaskControl() }
        synchronized(control) {
            // tryStart 是 generation + identity 的原子 CAS。重复点击开始会被拒绝；
            // remove 后同一 Room id 永久封存，任何迟到的“继续”都不能复活它。
            val lease = control.generation.tryStart() ?: return
            val predecessor = control.tail
            lateinit var registered: ActiveRun
            val job = scope.launch(
                context = RunLeaseContext(control, lease),
                start = CoroutineStart.LAZY
            ) {
                var serviceStarted = false
                try {
                    // 严格等待同 id 的上一条命令（旧下载退出/暂停落盘/删除清理）。
                    // 因而新 Job 不可能与旧 Job 同时持有分片 fd 或交叉写 Room。
                    predecessor?.join()
                    control.ioMutex.withLock {
                        if (!control.generation.isCurrent(lease)) return@withLock
                        conditionBlocker()?.let { reason ->
                            if (control.generation.isCurrent(lease)) {
                                dao.updateFailure(id, "CONDITION_BLOCKED", reason, 0, 0, DownloadTaskEntity.STATUS_PAUSED)
                            }
                            return@withLock
                        }
                        // 任务开始：有任务在下载时保持前台服务（避免切后台限速/进程被杀）
                        serviceStarted = true
                        onTaskStarted(id)
                        val restoredHeaders = if (effectiveHeaders.isNotEmpty()) {
                            effectiveHeaders
                        } else {
                            loadPersistedHeaders(id)
                        }
                        if (restoredHeaders.isNotEmpty()) taskHeaders[id] = restoredHeaders
                        runTaskWithRetry(id, restoredHeaders)
                    }
                } catch (e: CancellationException) {
                    // 主动暂停/删除：part 文件保留（或由 remove 清理）；状态已由调用方设置
                    _stats.update { it - id }
                } catch (e: Exception) {
                    _stats.update { it - id }
                    // identity-CAS：旧代次即使迟到抛错，也不能覆盖新 Job/暂停/删除状态。
                    if (isTaskActive()) {
                        Log.e(TAG, "task $id failed: ${LogRedactor.line(e.message ?: e.javaClass.simpleName)}")
                        val failure = DownloadErrorClassifier.classify(e)
                        val status = when (failure.code) {
                            "AUTH_EXPIRED" -> DownloadTaskEntity.STATUS_NEEDS_REAUTH
                            "PASSCODE_REQUIRED" -> DownloadTaskEntity.STATUS_NEEDS_INPUT
                            else -> DownloadTaskEntity.STATUS_FAILED
                        }
                        dao.updateFailure(id, failure.code, e.message ?: e.javaClass.simpleName, 0, 0, status)
                    } else {
                        Log.w(TAG, "task $id cancelled: ${LogRedactor.line(e.message.orEmpty())}")
                    }
                } finally {
                    // 任务结束（成功/失败/暂停/删除）：无任务时停止前台服务
                    if (serviceStarted) onTaskFinished()
                    // 双重 identity-CAS：旧 finally 既不能释放新 lease，也不能清掉新 job。
                    control.generation.release(lease)
                    synchronized(control) {
                        if (control.active === registered) control.active = null
                    }
                }
            }
            registered = ActiveRun(lease, job)
            control.active = registered
            control.tail = job
            job.start()
        }
    }

    /** 任务开始/结束计数：控制前台服务生命周期（有任务在下载即保持前台） */
    private suspend fun onTaskStarted(id: Long) {
        if (activeTaskCount.getAndIncrement() == 0) {
            val name = runCatching { dao.get(id)?.fileName }.getOrNull() ?: "下载任务"
            DownloadService.start(context, id, name)
        }
        // 锁屏保持下载：开启时获取 PARTIAL_WAKE_LOCK（息屏维持 CPU/网络）
        acquireWakeLockIfNeeded()
    }

    private fun onTaskFinished() {
        if (activeTaskCount.decrementAndGet() <= 0) {
            activeTaskCount.set(0)
            DownloadService.stop(context)
            releaseWakeLock()
        }
    }

    // ---------- 锁屏保持下载（WakeLock） ----------

    @Volatile
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLockIfNeeded() {
        if (!keepWhenLockedProvider()) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "yunx:download"
            ).apply { setReferenceCounted(false) }
        }
        // Fail-safe only: every normally finished/paused task still releases immediately.
        // This prevents a process bug from holding the CPU lock indefinitely.
        wakeLock?.let { if (!it.isHeld) it.acquire(24L * 60L * 60L * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    /** 暂停下载（保留 part 文件与请求头） */
    fun pause(id: Long) {
        Log.d(TAG, "pause: id=$id")
        val control = taskControls.getOrPut(id) { TaskControl() }
        lateinit var command: Job
        synchronized(control) {
            val revision = control.generation.pause() ?: return
            val previous = control.active
            control.active = null
            // 先同步发出取消，再把暂停落盘排到旧任务 tail 之后。
            previous?.job?.cancel(CancellationException("task paused"))
            val predecessor = control.tail
            command = scope.launch(start = CoroutineStart.LAZY) {
                predecessor?.join()
                control.ioMutex.withLock {
                    // 暂停后立刻点“继续”会推进 generation；旧暂停命令必须自动失效。
                    if (!control.generation.isCurrent(revision)) return@withLock
                    val real = chunkDirOf(id).listFiles()
                        ?.filter {
                            it.name.startsWith("part_") ||
                                (it.name.startsWith("seg_") && it.name.endsWith(".part"))
                        }
                        ?.sumOf { it.length() } ?: 0L
                    val task = dao.get(id) ?: return@withLock
                    // 已在取消抵达前完成的任务保持 COMPLETED，不能倒退为 PAUSED。
                    if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
                        pendingSavedPaths.remove(id)
                        return@withLock
                    }
                    // 暂停发生在公共目录拷贝期间：删除未提交成品，但保留内部 part 供续传。
                    pendingSavedPaths.remove(id)?.let { DownloadSaver.delete(context, it) }
                    if (!control.generation.isCurrent(revision)) return@withLock
                    if (real > task.downloadedSize) {
                        dao.updateProgress(id, DownloadTaskEntity.STATUS_PAUSED, real, task.totalSize)
                    } else {
                        dao.updateStatus(id, DownloadTaskEntity.STATUS_PAUSED)
                    }
                }
            }
            control.tail = command
        }
        // 立即中断所有阻塞网络 IO；命令本身会等旧协程/文件句柄完全退出。
        downloader.cancelCalls(id)
        _stats.update { it - id }
        command.start()
    }

    /**
     * 删除任务：取消下载 + 清 DB + 清 part 文件。
     * @param deleteLocal 同时删除已保存到本地的文件（savePath）
     */
    fun remove(id: Long, deleteLocal: Boolean = false) {
        Log.d(TAG, "remove: id=$id deleteLocal=$deleteLocal")
        val control = taskControls.getOrPut(id) { TaskControl() }
        lateinit var command: Job
        synchronized(control) {
            val removal = control.generation.remove()
            val previous = control.active
            control.active = null
            previous?.job?.cancel(CancellationException("task removed"))
            val predecessor = control.tail
            command = scope.launch(start = CoroutineStart.LAZY) {
                // remove 与下载共用同一 tail + mutex：旧 fd/协程完全退出后才删文件和 DB。
                predecessor?.join()
                control.ioMutex.withLock {
                    if (!control.generation.isCurrent(removal)) return@withLock
                    if (deleteLocal) {
                        dao.get(id)?.savePath?.let {
                            val deleted = DownloadSaver.delete(context, it)
                            Log.d(TAG, "remove: id=$id 删除本地文件 ${if (deleted) "成功" else "失败/未找到"} ($it)")
                        }
                    }
                    // 无论 deleteLocal 如何，remove 发生在保存提交窗口时产生的
                    // provisional 文件都不是用户确认的成品，必须删除。
                    pendingSavedPaths.remove(id)?.let { DownloadSaver.delete(context, it) }
                    dao.delete(id)
                    chunkDirOf(id).deleteRecursively()
                    File(context.cacheDir, "hls_$id").delete()
                    File(context.cacheDir, "merged_$id").delete()
                    // 删除任务后清理云盘转存（与下载成功完成同语义）；失败不阻断
                    taskCallbacks.remove(id)?.let { cleanup -> runCatching { cleanup() } }
                    taskHeaders.remove(id)
                    taskSizes.remove(id)
                    taskStartTimes.remove(id)
                }
            }
            control.tail = command
        }
        // 取消阻塞网络调用与协程；tombstone 已在上方同步生效，继续按钮无法复活任务。
        downloader.cancelCalls(id)
        _stats.update { it - id }
        command.start()
    }

    fun setPriority(id: Long, priority: Int) {
        scope.launch { dao.updatePriority(id, priority.coerceIn(-100, 100)) }
    }

    // ---------- 内部实现 ----------

    private fun encodeHeaders(headers: Map<String, String>): String {
        val json = JSONObject().apply { headers.forEach { (name, value) -> put(name, value) } }.toString()
        return credentialCipher.encrypt(json, "download.requestHeaders")
    }

    private suspend fun loadPersistedHeaders(id: Long): Map<String, String> {
        val stored = dao.get(id)?.requestHeadersJson.orEmpty()
        if (stored.isBlank()) return emptyMap()
        return runCatching {
            val jsonText = credentialCipher.decrypt(stored, "download.requestHeaders")
            val json = JSONObject(jsonText)
            buildMap {
                json.keys().forEach { name -> put(name, json.getString(name)) }
            }.also {
                if (!credentialCipher.isEncrypted(stored)) {
                    dao.updateRequestHeaders(id, encodeHeaders(it))
                }
            }
        }.getOrElse {
            dao.updateRequestHeaders(id, encodeHeaders(emptyMap()))
            emptyMap()
        }
    }

    /**
     * 当前协程不仅要未取消，还必须持有该任务的最新 generation/identity lease。
     * 这是所有运行中 Room 写入的内存 CAS；旧协程即使迟到恢复也会返回 false。
     */
    private suspend fun isTaskActive(): Boolean {
        if (coroutineContext[Job]?.isActive != true) return false
        val identity = coroutineContext[RunLeaseContext] ?: return false
        return identity.control.generation.isCurrent(identity.lease)
    }

    /**
     * Copies to MediaStore/SAF/legacy storage with lease-aware cancellation.
     * The path is registered inside the NonCancellable IO block, closing the
     * gap where a public file existed but cancellation hid its returned Uri.
     */
    private suspend fun savePublicFile(id: Long, fileName: String, source: File): String? {
        val identity = coroutineContext[RunLeaseContext] ?: return null
        val ownerJob = coroutineContext[Job]
        return withContext(NonCancellable + Dispatchers.IO) {
            val path = DownloadSaver.save(
                context = context,
                fileName = fileName,
                source = source,
                targetDirUri = saveDirProvider(),
                shouldContinue = {
                    ownerJob?.isActive == true &&
                        identity.control.generation.isCurrent(identity.lease)
                }
            )
            if (path != null) pendingSavedPaths[id] = path
            path
        }
    }

    /** 等待并发许可：当前下载任务数 >= 上限时轮询等待（暂停/取消可退出等待） */
    private suspend fun awaitConcurrencySlot() {
        val max = concurrencyProvider().coerceAtLeast(1)
        while (isTaskActive() && activeDownloads.get() >= max) {
            delay(300)
        }
    }

    /**
     * 执行任务并支持失败自动重试（断点续传，part 文件保留）。
     * 同时负责「最大同时下载任务数」并发许可的获取/释放。
     */
    private suspend fun runTaskWithRetry(id: Long, headers: Map<String, String>) {
        var attempts = 0
        val maxRetries = retryCountProvider().coerceIn(0, 10)
        while (true) {
            // 并发许可：排队等待，直到有空闲下载槽位（或任务被暂停/取消）
            awaitConcurrencySlot()
            if (!isTaskActive()) return
            activeDownloads.incrementAndGet()
            try {
                try {
                    runTask(id, headers)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempts++
                    val failure = DownloadErrorClassifier.classify(e)
                    if (isTaskActive() && failure.retryable && attempts <= maxRetries) {
                        val backoff = DownloadErrorClassifier.backoffMillis(attempts, Random.nextLong(0, 751))
                        dao.updateFailure(
                            id = id,
                            code = failure.code,
                            message = e.message ?: failure.action,
                            retryCount = attempts,
                            nextRetryAt = System.currentTimeMillis() + backoff,
                            status = DownloadTaskEntity.STATUS_RETRY_WAIT
                        )
                        Log.d(TAG, "runTaskWithRetry: id=$id 失败，自动重试 $attempts/$maxRetries：${LogRedactor.line(e.message.orEmpty())}")
                        delay(backoff)
                    } else {
                        throw e
                    }
                }
            } finally {
                activeDownloads.decrementAndGet()
            }
        }
    }

    private suspend fun runTask(id: Long, headers: Map<String, String>) {
        // 协程已被取消（暂停/删除）：直接退出，不写状态
        if (!isTaskActive()) return
        val task = dao.get(id) ?: return
        dao.updateStatus(id, DownloadTaskEntity.STATUS_PREPARING)
        taskStartTimes[id] = System.currentTimeMillis()
        Log.d(TAG, "runTask: id=$id fileName=${task.fileName}")

        // HLS（m3u8 转码流，如 UC play）：不走 Range 分片，直接拉分片合并
        if (task.url.contains(".m3u8", true) || task.url.contains(".m3u", true)) {
            Log.d(TAG, "runTask: id=$id HLS 转码流下载 origin=${LogRedactor.url(task.url)}")
            hlsDownload(id, task, headers)
            return
        }

        // 总大小以服务器探测为准（Range0-0 的 Content-Range 是真实总大小），
        // 避免各平台传入的 size 与实际不符导致分片区间错误 → 文件截断/膨胀损坏
        val total = downloader.getTotalSize(task.url, headers)
            ?: taskSizes[id]?.takeIf { it > 0 }
        if (total == null) {
            // 服务器不返回文件大小（Range/Content-Length 均缺失）：降级为流式下载（开放区间 Range）
            Log.w(TAG, "runTask: id=$id 无法获取总大小，降级流式下载 origin=${LogRedactor.url(task.url)}")
            streamDownload(id, task, headers)
            return
        }
        Log.d(TAG, "getTotalSize: id=$id total=$total origin=${LogRedactor.url(task.url)}")
        dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, total)
        // 取到大小后再次检查取消（暂停可能发生在 getTotalSize 期间）
        if (!isTaskActive()) return

        val threadCount = threadProvider(task.platform).coerceAtLeast(1)
        val chunkCount = chunkCountFor(total, threadCount)
        val chunkSize = ceil(total.toDouble() / chunkCount).toLong()
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        // ★ 分片计划签名：part_$i 按索引命名，但区间由 chunkCount/total 推导。
        //   若跨会话改了线程数或服务器探测大小变化 → 旧 part 区间错位 → 续传膨胀/损坏。
        //   检测到计划不一致时整目录清空重下（旧 part 不可信）。
        val mainPoolCount = (chunkCount * 0.7).toInt().coerceIn(1, chunkCount) // 主池片数（70%）
        val elasticStart = mainPoolCount * chunkSize                          // 弹性区起始字节
        val planFile = File(chunkDir, "plan.txt")
        val plan = "chunks=$chunkCount total=$total main=$mainPoolCount"
        if (planFile.exists() && planFile.readText() != plan) {
            Log.w(TAG, "runTask: id=$id 分片计划变化（$plan），清空旧 part 重下")
            chunkDir.deleteRecursively()
            chunkDir.mkdirs()
        } else {
            // 计划一致（断点续传）：主池 part_i 与弹性区 seg_{start}_{end} 均按文件已有长度续传
            // （seg 文件名携带区间信息，downloadChunk 按长度续传，不再删除重下）
        }
        planFile.writeText(plan)
        // 有效并发：仅迅雷（CDN 对单文件并发 Range 有阈值，约 8 个，超过会降级 200 整文件）封顶安全上限；
        // 其他平台保持用户设置的线程数（满并发）
        val isXunlei = headers["User-Agent"]?.contains("xunlei", ignoreCase = true) == true ||
            task.url.contains("xunlei", ignoreCase = true)
        val effectiveWorkers = if (isXunlei) {
            min(threadCount, RANGE_WORKERS_CAP).coerceAtLeast(1)
        } else {
            threadCount.coerceAtLeast(1)
        }
        Log.d(TAG, "分片规划: id=$id chunks=$chunkCount main=$mainPoolCount elasticStart=$elasticStart size=$chunkSize threads=$threadCount effectiveWorkers=$effectiveWorkers isXunlei=$isXunlei")

        // 注册实时统计：线程数 = 有效并发（受安全上限约束）
        _stats.update { it + (id to DownloadStats(0L, -1L, effectiveWorkers)) }

        // 必须先删除不完整/越界/畸形 seg，再统计断点字节；否则残片先计数、
        // 随后又删除重下，会让同一批字节重复累加并提前显示 100%。
        val completedElasticSegments = if (elasticStart < total) {
            ElasticSegmentPolicy.prepare(chunkDir, elasticStart, total)
        } else {
            emptyList()
        }
        // 统计已有 part/seg 大小（断点续传起点；只计校验为完整的弹性 seg）
        val downloaded = AtomicLong(0)
        (0 until mainPoolCount).forEach { i ->
            downloaded.addAndGet(File(chunkDir, "part_$i").length())
        }
        completedElasticSegments.forEach { downloaded.addAndGet(it.size) }
        // ★ 钳制到 total：防旧 job 残留累加导致显示"已下载 > 总大小"
        val init = minOf(downloaded.get(), total)
        downloaded.set(init)
        // ★ 恢复时 DB 旧值可能滞后于磁盘（暂停瞬间未上报的字节）：以磁盘真实大小为准回写，避免进度回跳
        if (init > task.downloadedSize) {
            dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, init, total)
        }
        val lastPersistAt = AtomicLong(0L)
        val speedRecorder = SpeedRecorder()

        // ---------- 任务池（主池 70% 等分）+ 弹性区（30%，空闲线程中点劈分） ----------
        val results = arrayOfNulls<ChunkResult?>(mainPoolCount)
        val nextIdx = AtomicInteger(0)
        val fallback = AtomicBoolean(false)              // 任一分片检测到「服务器忽略 Range」→ 整任务回退单流
        val failReason = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val rangeIgnoredCount = AtomicInteger(0)         // RANGE_IGNORED 累计次数（偶发 200 容忍）

        // ★ 弹性区分配器：按字节顺序领取 4MB 块，区间物理相邻（替代中点劈分，根治中后段掉速）。
        //   续传：不完整 seg 删除重下；完整 seg 前缀推进 nextStart（弹性区按序分配，完成块天然是字节前缀）。
        val elasticAllocator = ElasticAllocator(total, elasticStart)
        if (elasticStart < total) {
            // 推进到已完整前缀末尾（只前进，跳过已下载弹性块）
            var resumeNext = elasticStart
            for (segment in completedElasticSegments) {
                if (segment.start == resumeNext) resumeNext = segment.end + 1 else break
            }
            elasticAllocator.skipTo(resumeNext)
        }
        val elasticResults = ConcurrentHashMap<String, ChunkResult>()

        // ★ 固定容量信号量：容量 = effectiveWorkers，绝不手动 release，杜绝溢出崩溃
        val sem = Semaphore(effectiveWorkers)

        val allOk = coroutineScope {
            val workers = List(effectiveWorkers) {
                async(Dispatchers.IO) {
                    // 阶段 1：主池循环领取
                    while (true) {
                        if (fallback.get()) break
                        val i = nextIdx.getAndIncrement()
                        if (i >= mainPoolCount) break
                        // 错峰建连：首请求前按序号微延迟，平摊 TCP/TLS 突发（仅影响首请求，不影响稳态并发）
                        if (i > 0) delay(min(i.toLong(), STAGGER_CAP.toLong()) * STAGGER_MS)
                        sem.withPermit {
                            if (fallback.get()) return@withPermit
                            val start = i * chunkSize
                            val end = min(start + chunkSize - 1, total - 1)
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = start, end = end,
                                    partFile = File(chunkDir, "part_$i"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failReason.compareAndSet(null, "分片 ${i + 1}/$mainPoolCount：${e.message ?: e.javaClass.simpleName}")
                                ChunkResult.FAILED
                            }
                            results[i] = res
                            when (res) {
                                ChunkResult.RANGE_IGNORED -> {
                                    // 偶发 200（CDN 限流中间态）不算真降级：前 N 次不触发回退，继续领新片；
                                    // 持续 RANGE_IGNORED 才回退单流
                                    val n = rangeIgnoredCount.incrementAndGet()
                                    Log.w(TAG, "runTask: id=$id 分片${i + 1} 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                    if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                                }
                                ChunkResult.FAILED -> failReason.compareAndSet(null, "分片 ${i + 1}/$mainPoolCount 下载失败")
                                else -> {}
                            }
                        }
                    }
                    // 阶段 2：主池取空 → 弹性区按字节顺序领取 4MB 块（空闲线程逐个平滑转入，并发形态不突变）
                    while (!fallback.get()) {
                        val range = elasticAllocator.take() ?: break
                        val s = range.first
                        val e = range.last
                        val key = "${s}_${e}"
                        val res = try {
                            sem.withPermit {
                                if (fallback.get()) return@withPermit ChunkResult.FAILED
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = s, end = e,
                                    partFile = File(chunkDir, "seg_$key.part"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ChunkResult.FAILED
                        }
                        elasticResults[key] = res
                        when (res) {
                            ChunkResult.RANGE_IGNORED -> {
                                val n = rangeIgnoredCount.incrementAndGet()
                                Log.w(TAG, "runTask: id=$id 弹性区间 $key 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                            }
                            ChunkResult.FAILED -> failReason.compareAndSet(null, "弹性区间 ${s}-${e} 下载失败")
                            else -> {}
                        }
                    }
                }
            }
            workers.awaitAll()
            !fallback.get() && results.all { it == ChunkResult.OK } &&
                elasticResults.values.all { it == ChunkResult.OK }
        }

        // ---------- 三种结局 ----------
        if (fallback.get()) {
            // 服务器忽略 Range：回退单条整文件流（只下一次，不按分片重复下载整文件）
            Log.w(TAG, "runTask: id=$id 回退单流整文件下载（避免重复下载整文件）")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        if (!allOk) {
            // 失败区间并行重试：收集主池缺失片 + 弹性区失败区间，复用 worker 池并发补下
            val missing = buildList {
                for (i in 0 until mainPoolCount) {
                    val f = File(chunkDir, "part_$i")
                    val s = i * chunkSize
                    val e = min(s + chunkSize - 1, total - 1)
                    if (f.length() < (e - s + 1)) add(RetryRange(s, e, f))
                }
                elasticResults.forEach { (key, res) ->
                    if (res != ChunkResult.OK) {
                        val s = key.substringBefore('_').toLong()
                        val e = key.substringAfter('_').toLong()
                        add(RetryRange(s, e, File(chunkDir, "seg_$key.part")))
                    }
                }
            }
            Log.e(TAG, "runTask: id=$id 缺失区间 ${missing.size} 个 reason=${failReason.get()}，并行重试")
            val retryOk = if (missing.isEmpty()) true else coroutineScope {
                val retryIdx = AtomicInteger(0)
                val retryResults = arrayOfNulls<ChunkResult?>(missing.size)
                val retryWorkers = List(min(effectiveWorkers, missing.size)) {
                    async(Dispatchers.IO) {
                        while (true) {
                            if (!isTaskActive()) break
                            val pos = retryIdx.getAndIncrement()
                            if (pos >= missing.size) break
                            val m = missing[pos]
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = m.start, end = m.end,
                                    partFile = m.file, headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, new, total)
                                    notifyProgress(id, task.fileName, new, total)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ChunkResult.FAILED
                            }
                            retryResults[pos] = res
                            if (res != ChunkResult.OK) {
                                failReason.compareAndSet(null, "区间 ${m.start}-${m.end} 重试仍失败")
                            }
                        }
                    }
                }
                retryWorkers.awaitAll()
                retryResults.all { it == ChunkResult.OK }
            }
            if (retryOk) {
                Log.d(TAG, "runTask: id=$id 重试补齐所有区间，开始合并")
                finishDownload(id, chunkDir, finalChunkFiles(chunkDir, mainPoolCount), task.fileName, total)
                return
            }
            // 重试仍失败：回退单流
            Log.w(TAG, "runTask: id=$id 分片重试失败，回退单流整文件下载")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        Log.d(TAG, "runTask: id=$id 所有区间完成，开始合并")
        finishDownload(id, chunkDir, finalChunkFiles(chunkDir, mainPoolCount), task.fileName, total)
    }

    /** 最终合并文件列表：主池 part_0..part_{n-1}（连续前半段）+ 弹性区 seg_{start}_{end} 按 start 排序（后半段） */
    private fun finalChunkFiles(chunkDir: File, mainPoolCount: Int): List<File> {
        val mainFiles = (0 until mainPoolCount).map { File(chunkDir, "part_$it") }
        val elasticFiles = chunkDir.listFiles { f ->
            f.name.startsWith("seg_") && f.name.endsWith(".part")
        }?.sortedBy { it.name.removePrefix("seg_").substringBefore('_').toLong() }
            ?: emptyList()
        return mainFiles + elasticFiles
    }

    /**
     * 回退：单条整文件流下载（服务器忽略 Range 时）。
     * 写入**独立**的 full_single.bin（从 0 开始），不复用 part_0，避免与已下分片错位/重复。
     */
    private suspend fun singleStreamFallback(
        id: Long,
        task: DownloadTaskEntity,
        headers: Map<String, String>,
        total: Long,
        chunkDir: File,
        failReason: java.util.concurrent.atomic.AtomicReference<String?>
    ) {
        val fullFile = File(chunkDir, "full_single.bin").apply { delete() } // 全新整文件，从 0 开始
        val fullDownloaded = AtomicLong(0)
        val fullLastAt = AtomicLong(0L)
        val ok = downloader.downloadFull(id, task.url, fullFile, headers, total) { bytes ->
            speedLimiter.awaitAllow(bytes)
            // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
            val new = minOf(fullDownloaded.addAndGet(bytes), total)
            if (!isTaskActive()) return@downloadFull
            persistProgressIfDue(id, new, total, force = false, lastAt = fullLastAt)
            notifyProgress(id, task.fileName, new, total)
        }
        if (!ok) throw IllegalStateException(failReason.get() ?: "分片与单流下载均失败")
        finishDownload(id, chunkDir, listOf(fullFile), task.fileName, total)
    }

    /** 流式降级下载：总大小未知时单分片开放区间下载（Range: bytes=from-），读到 EOF */
    private suspend fun streamDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, 0)
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        val partFile = File(chunkDir, "part_0")
        val downloaded = AtomicLong(partFile.length())
        val streamLastAt = AtomicLong(0L)
        val ok = downloader.downloadChunk(
            taskId = id,
            url = task.url,
            start = 0,
            end = Long.MAX_VALUE,
            partFile = partFile,
            headers = headers
        ) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = downloaded.addAndGet(bytes)
            if (!isTaskActive()) return@downloadChunk
            // 大小未知：只更新已下载量（total=0 表示未知）
            persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            // 前台通知进度（2 秒节流，total 未知时仅更新标题）
            notifyProgress(id, task.fileName, new, 0)
        }
        if (ok != ChunkResult.OK) {
            // Range 被 CDN 拒绝（416/403）或忽略（200 整文件）：回退为无 Range 完整 GET
            Log.w(TAG, "streamDownload: id=$id Range 失败，回退完整 GET 下载")
            downloaded.set(0)
            dao.updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, 0, 0)
            val ok2 = downloader.downloadFull(
                taskId = id,
                url = task.url,
                partFile = partFile,
                headers = headers
            ) { bytes ->
                speedLimiter.awaitAllow(bytes)
                val new = downloaded.addAndGet(bytes)
                if (!isTaskActive()) return@downloadFull
                persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            }
            if (!ok2) throw IllegalStateException("下载失败（Range 与完整下载均失败）")
        }
        if (!isTaskActive()) return
        finishDownload(id, chunkDir, listOf(partFile), task.fileName, 0)
    }

    /** HLS（m3u8 转码流，如 UC play）下载：拉取分片合并 → 保存 → 完成回调 */
    private suspend fun hlsDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val hlsFile = File(context.cacheDir, "hls_$id")
        hlsFile.delete()
        val downloaded = AtomicLong(0)
        val hlsLastAt = AtomicLong(0L)
        val ok = HlsDownloader.download(task.url, headers, hlsFile) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = downloaded.addAndGet(bytes)
            persistProgressIfDue(id, new, 0, force = false, lastAt = hlsLastAt)
            notifyProgress(id, task.fileName, new, 0)
        }
        if (!isTaskActive()) return
        if (!ok) {
            hlsFile.delete()
            throw IllegalStateException("HLS 转码流下载失败")
        }
        // Android 9- 保存前检查存储权限（动态申请，授权后继续；无权限则报错提示）
        if (!storagePermissionProvider()) {
            hlsFile.delete()
            throw IllegalStateException("未授予存储权限，无法保存到下载目录")
        }
        val savedPath = savePublicFile(id, task.fileName, hlsFile)
            ?: throw IllegalStateException("保存到下载目录失败")
        val hlsTotal = dao.get(id)?.totalSize ?: 0L
        if (!completeWithAvg(id, savedPath, hlsTotal)) throw CancellationException("task changed while saving")
        pendingSavedPaths.remove(id)
        Log.d(TAG, "hlsDownload: id=$id 下载完成 savedPath=$savedPath size=${hlsFile.length()}")
        taskCallbacks.remove(id)?.let { cb -> runCatching { cb() } }
        _stats.update { it - id }
        hlsFile.delete()
    }

    /**
     * 合并分片 → 保存到公共 Download 目录 → 触发完成回调 → 清理。
     * ★ 增加完整性校验：分片非空 + 合并后总大小 == total，任一不符直接抛错，绝不保存损坏文件。
     */
    private suspend fun finishDownload(
        id: Long,
        chunkDir: File,
        chunkFiles: List<File>,
        fileName: String,
        total: Long
    ) {
        if (!isTaskActive()) return
        // 1) 分片完整性
        for (part in chunkFiles) {
            if (!part.exists() || part.length() <= 0) {
                Log.e(TAG, "finishDownload: id=$id 分片缺失/为空 $part")
                throw IllegalStateException("分片文件缺失或为空，拒绝合并（防止文件损坏）")
            }
        }
        // 2) 合并
        // ★ 合并产物放内部缓存（data 分区，非 FUSE 挂载）：大文件 IO 快得多；保存完成即删
        val merged = File(context.cacheDir, "merged_$id")
        if (!downloader.mergeChunks(chunkFiles, merged)) {
            Log.e(TAG, "finishDownload: id=$id 合并分片失败")
            throw IllegalStateException("合并分片失败")
        }
        // 3) 整体大小校验（total>0 时）
        if (total > 0 && merged.length() != total) {
            Log.e(TAG, "finishDownload: id=$id 文件大小校验失败 期望=$total 实际=${merged.length()}")
            merged.delete()
            throw IllegalStateException("文件大小校验失败：期望 $total 字节，实际 ${merged.length()} 字节（已拒绝保存损坏文件）")
        }
        // 4) 软件内更新额外校验作者签名清单中的 SHA-256。
        // 只有校验通过才保存成品，下载页才可能显示安装按钮。
        val expectedSha256 = dao.get(id)?.expectedSha256.orEmpty()
        if (expectedSha256.isNotBlank()) {
            dao.updateStatus(id, DownloadTaskEntity.STATUS_VERIFYING)
            val verified = withContext(Dispatchers.IO) { UpdateIntegrity.verify(merged, expectedSha256) }
            if (!verified) {
                merged.delete()
                throw SecurityException("更新包 SHA-256 安全校验失败，文件已删除，请勿安装")
            }
            if (fileName.endsWith(".apk", ignoreCase = true)) {
                val identity = withContext(Dispatchers.IO) { ApkUpdateVerifier.verify(context, merged) }
                if (!identity.accepted) {
                    merged.delete()
                    throw SecurityException("${identity.message}，文件已删除")
                }
            }
        }
        // 5) Android 9- 保存前检查存储权限（动态申请，授权后继续；无权限则报错提示）
        if (!storagePermissionProvider()) {
            merged.delete()
            throw IllegalStateException("未授予存储权限，无法保存到下载目录")
        }
        // 6) 保存（自定义目录经 SAF 写入；默认目录走 MediaStore/传统路径）
        // ★ 同步阻塞拷贝必须切 IO 线程：任务跑在 Dispatchers.Default（CPU 池），
        //   大文件保存若占满 Default 线程会让整个下载器协程饿死（"100% 卡死保存不了"）
        val savedPath = savePublicFile(id, fileName, merged)
            ?: throw IllegalStateException("保存到下载目录失败")
        if (!completeWithAvg(id, savedPath, total)) throw CancellationException("task changed while saving")
        pendingSavedPaths.remove(id)
        Log.d(TAG, "finishDownload: id=$id 下载完成 size=${merged.length()}")
        taskCallbacks.remove(id)?.let { cb ->
            runCatching { cb() }
        }
        _stats.update { it - id }
        merged.delete()
        chunkDir.deleteRecursively()
    }

    /**
     * 速度采样器：取近 [WINDOW_MS] 秒滑动窗口的平均速度，平滑多线程下载的速度波动。
     * 多线程并发下瞬时速率波动大，短窗口估算剩余时长会剧烈跳动；
     * 改用 5 秒窗口均值后，剩余时长更稳定可靠。
     */
    private class SpeedRecorder {
        private data class Sample(val timeMs: Long, val bytes: Long)

        private val samples = ArrayDeque<Sample>()
        private var lastEmit = 0L

        @Synchronized
        fun onBytes(total: Long): Long? {
            val now = System.currentTimeMillis()
            samples.addLast(Sample(now, total))
            // 剔除窗口外的旧样本，但始终保留至少 2 个（下载起步阶段窗口尚短）
            while (samples.size > 2 && now - samples.first().timeMs > WINDOW_MS) {
                samples.removeFirst()
            }
            // 250ms 发射一次，避免高频刷新 UI/通知
            if (now - lastEmit < 250) return null
            val first = samples.first()
            val elapsed = now - first.timeMs
            val speed = if (elapsed > 0) {
                ((total - first.bytes) * 1000 / elapsed).coerceAtLeast(0)
            } else 0L
            lastEmit = now
            return speed
        }

        private companion object {
            const val WINDOW_MS = 5000L
        }
    }

    /** 全局限速器（令牌桶）：所有任务合计不超过 speedLimitProvider 的字节/秒；0 = 不限速 */
    private inner class SpeedLimiter {
        @Volatile
        private var tokens = 0L
        @Volatile
        private var lastRefillNanos = System.nanoTime()

        @Synchronized
        private fun refill(limit: Long) {
            val now = System.nanoTime()
            val elapsedSec = ((now - lastRefillNanos).coerceAtLeast(0) / 1_000_000_000.0)
            lastRefillNanos = now
            tokens = minOf(limit, tokens + (elapsedSec * limit).toLong())
        }

        /** 消耗 bytes 字节额度；不足则挂起等待（限速生效） */
        suspend fun awaitAllow(bytes: Long) {
            val limit = speedLimitProvider().coerceAtLeast(0L)
            if (limit <= 0L) return
            while (true) {
                val waitMs = synchronized(this) {
                    refill(limit)
                    if (bytes <= tokens) {
                        tokens -= bytes
                        return
                    }
                    ((bytes - tokens) * 1000 / limit).coerceIn(1L, 200L)
                }
                // 锁外挂起等待，避免持锁阻塞其他任务
                delay(waitMs)
            }
        }
    }

    /** 下载临时文件缓存根目录：外部缓存（/storage/emulated/0/Android/data/com.yunx.app/cache），
     *  与最终保存目录解耦，系统可自动清理；外部存储不可用时回退内部缓存目录。 */
    private fun cacheBase(): File = context.externalCacheDir ?: context.cacheDir

    /** 分片临时文件目录：cacheBase()/download_tmp/$id */
    private fun chunkDirOf(id: Long): File = File(cacheBase(), "download_tmp/$id")

    /** 分片数规划（任务池模型）：分片数 = 线程数 × 8，远多于并发线程数。
     *  worker 循环领取盈余块，任一分片慢时其他线程继续领新片，根治"尾部并发塌缩"；
     *  保留 1MB 单片下限（避免过多小片）与 512 封顶。 */
    private fun chunkCountFor(total: Long, threads: Int): Int {
        if (total <= 0) return 1
        val minChunkBytes = 1 * 1024 * 1024L
        val bySize = when {
            total < 5 * 1024 * 1024 -> 1          // < 5MB 不分片
            total < 50 * 1024 * 1024 -> 8         // < 50MB
            total < 500 * 1024 * 1024 -> 32       // < 500MB
            else -> 64                            // ≥ 500MB 基础值
        }
        // 任务池：每线程平均领 8 片，天然抗慢片拖尾（比 1:1 映射多 8 倍盈余）
        val want = maxOf(bySize, threads * 8)
        return minOf(want, (total / minChunkBytes).toInt().coerceAtLeast(1), 512)
    }
}
