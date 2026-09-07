package com.jiexi.core.media

import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Retry messages and repeated percentages are not evidence of forward progress. */
class TransferLiveness(private val idleMillis: Long = 120_000, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var lastGrowth = clock()
    private var sizes = emptyMap<String, Long>()
    private var postProcessingAt: Long? = null
    @Synchronized fun sample(current: Map<String, Long>) {
        if (current.any { (name, size) -> size > (sizes[name] ?: 0L) }) lastGrowth = clock()
        sizes = current
    }
    @Synchronized fun observe(line: String) {
        if (isPostProcessing(line) && postProcessingAt == null) postProcessingAt = clock()
    }
    @Synchronized fun expired(): Boolean = postProcessingAt?.let { clock() - it >= 900_000 }
        ?: (clock() - lastGrowth >= idleMillis)
    companion object {
        fun isPostProcessing(line: String) = listOf("[Merger]", "[ExtractAudio]", "[VideoConvertor]", "[VideoRemuxer]", "[EmbedSubtitle]", "[Fixup").any(line::startsWith)
        fun stage(line: String): String? = when {
            isPostProcessing(line) -> "正在合并或转换媒体"
            line.contains("Retrying", true) || line.contains("timed out", true) -> "连接失败，正在重试"
            line.startsWith("[youtube]") -> "正在获取 YouTube 媒体信息"
            line.startsWith("[download] Destination") -> "正在连接媒体服务器"
            else -> null
        }
    }
}

/** Owns only its timer. The caller supplies a task-specific process cancellation action. */
class MediaTransferWatchdog(root: File?, idleMillis: Long = 120_000, private val onStalled: () -> Unit) : Closeable {
    private val liveness = TransferLiveness(idleMillis)
    private var closed = false
    @Volatile var timedOut = false
        private set
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "jiexi-media-watchdog").apply { isDaemon = true } }
    init {
        timer.scheduleWithFixedDelay({
            synchronized(this) {
                if (!closed) {
                    val sizes = root?.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".ytdl") && !it.name.endsWith(".json") }
                        .associate { it.name to it.length() }
                    liveness.sample(sizes)
                    if (liveness.expired()) {
                        timedOut = true
                        closed = true
                        runCatching(onStalled)
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS)
    }
    fun observe(line: String) = liveness.observe(line)
    @Synchronized override fun close() { closed = true; timer.shutdownNow() }
    companion object {
        const val TIMEOUT_MESSAGE = "媒体任务长时间没有数据进展，已停止等待并保留断点。请检查网络或代理后重试。"
    }
}
