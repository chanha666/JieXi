package com.yunx.app.data.download

enum class DownloadStage { PARSE_LINK, AUTH_CHECK, GET_DIRECT_LINK, CONNECT_DOWNLOAD, DOWNLOAD_RANGE, MERGE, VERIFY_FILE, CLEANUP_REMOTE, WRITE_STORAGE, UPDATE_APP }

data class DownloadFailure(
    val code: String,
    val stage: DownloadStage,
    val retryable: Boolean,
    val action: String
)

object DownloadErrorClassifier {
    fun classify(error: Throwable, stage: DownloadStage = DownloadStage.CONNECT_DOWNLOAD): DownloadFailure {
        val message = generateSequence(error) { it.cause }.mapNotNull(Throwable::message).joinToString(" ").lowercase()
        return when {
            "sha-256" in message || "安全校验" in message || "更新包名" in message ||
                "更新版本" in message || "发行签名" in message || "android 安装包" in message ||
                "apk 身份" in message -> DownloadFailure("UPDATE_INTEGRITY_FAILED", DownloadStage.UPDATE_APP, false, "重新检查更新")
            "401" in message || "登录" in message || "cookie" in message -> DownloadFailure("AUTH_EXPIRED", DownloadStage.AUTH_CHECK, false, "重新登录当前网盘")
            "提取码" in message || "password" in message -> DownloadFailure("PASSCODE_REQUIRED", DownloadStage.PARSE_LINK, false, "检查提取码后重试")
            "空间" in message || "no space" in message -> DownloadFailure("DISK_FULL", DownloadStage.WRITE_STORAGE, false, "释放磁盘空间或更换目录")
            "403" in message || "直链" in message -> DownloadFailure("DIRECT_LINK_EXPIRED", DownloadStage.GET_DIRECT_LINK, true, "重新获取直链")
            "429" in message || "503" in message || "reset" in message || "timeout" in message || "timed out" in message -> DownloadFailure("NETWORK_RETRYABLE", stage, true, "稍后自动重试")
            "range" in message -> DownloadFailure("RANGE_UNSUPPORTED", DownloadStage.DOWNLOAD_RANGE, true, "自动降级单连接")
            "校验" in message || "size" in message -> DownloadFailure("VERIFY_FAILED", DownloadStage.VERIFY_FILE, true, "重新下载损坏分片")
            else -> DownloadFailure("DOWNLOAD_FAILED", stage, true, "检查网络后重试")
        }
    }

    fun backoffMillis(attempt: Int, jitter: Long = 0L): Long =
        ((1_000L shl attempt.coerceIn(0, 6)) + jitter.coerceIn(0, 750)).coerceAtMost(60_000L)
}
