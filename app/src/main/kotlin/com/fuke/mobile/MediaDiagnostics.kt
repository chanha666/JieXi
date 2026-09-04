package com.fuke.mobile

import com.yunx.app.util.LogRedactor

/** Privacy-minimal diagnostics: never serializes the full DownloadTask model. */
internal object MediaDiagnostics {
    fun taskSummary(tasks: List<DownloadTask>): String = tasks.joinToString("\n") { task ->
        val shortId = task.id.takeLast(6).ifBlank { "unknown" }
        val platform = task.platform.replace(UNSAFE_LABEL, "?").take(32).ifBlank { "未知" }
        val error = cleanError(task.error)
        "任务 #$shortId | 平台=$platform | 状态=${task.status.name} | 进度=${task.progress.coerceIn(0, 100)}% | 错误=$error"
    }.ifBlank { "（无任务记录）" }

    internal fun cleanError(value: String): String {
        if (value.isBlank()) return "无"
        return LogRedactor.line(value)
            .replace(URL, "<url>")
            .replace(CONTENT_URI, "<uri>")
            .replace(WINDOWS_PATH, "<path>")
            .replace(UNIX_PATH, "<path>")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
            .take(240)
            .ifBlank { "无" }
    }

    private val URL = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    private val CONTENT_URI = Regex("(?:content|file)://[^\\s]+", RegexOption.IGNORE_CASE)
    private val WINDOWS_PATH = Regex("[A-Za-z]:\\\\[^\\r\\n]+")
    private val UNIX_PATH = Regex("(?<![A-Za-z0-9])/(?:[^/\\s]+/)+[^\\s]+")
    private val UNSAFE_LABEL = Regex("[^\\p{L}\\p{N} _-]")
}
