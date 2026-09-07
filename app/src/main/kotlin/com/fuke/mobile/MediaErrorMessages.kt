package com.fuke.mobile

/** Converts low-level extractor/network failures into short, actionable Chinese messages. */
internal object MediaErrorMessages {
    fun forDownload(error: Throwable): String = map(error, "下载")

    fun forAction(error: Throwable): String = map(error, "操作")

    private fun map(error: Throwable, action: String): String {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
            .joinToString(" | ")
        return when {
            matches(text, "video is unavailable|video unavailable|private video|video has been removed") ->
                "当前视频不可用，可能已删除、设为私有或受到访问限制。"
            matches(text, """DRM|encrypted|\bKID\b|encryption key""") ->
                "检测到加密媒体流，匿名模式无法导出。"
            matches(text, "login|cookie|sign[ -]?in|会员|登录") ->
                "网站没有向匿名访问提供这个视频。"
            matches(text, "cleartext.*not permitted|CLEARTEXT communication") ->
                "这个地址使用了不安全的 HTTP 连接，应用已阻止访问；请改用 HTTPS 链接。"
            matches(text, "Unable to resolve host|No address associated with hostname|UnknownHost|Name or service not known|Temporary failure in name resolution") ->
                "无法连接到网站（域名解析失败），请检查网络、DNS 或代理后重试。"
            matches(text, """(?:HTTP(?: Error)?|status|response|request failed)[^0-9]{0,12}404|\(404\)|404 Not Found""") ->
                "资源已失效或被删除（HTTP 404），请重新解析原链接。"
            matches(text, """(?:HTTP(?: Error)?|status|response|request failed)[^0-9]{0,12}(401|403)|\((401|403)\)|401 Unauthorized|403 Forbidden""") ->
                "网站拒绝了匿名访问，链接可能已过期或需要登录。"
            matches(text, """(?:HTTP(?: Error)?|status|response|request failed)[^0-9]{0,12}429|\(429\)|Too Many Requests""") ->
                "网站请求过于频繁，请稍后再试。"
            matches(text, """(?:HTTP(?: Error)?|status|response|request failed)[^0-9]{0,12}5\d\d|\(5\d\d\)""") ->
                "网站服务暂时不可用，请稍后重试。"
            matches(text, "timed? out|timeout|SocketTimeout") ->
                "连接超时，请检查网络后重试；下载任务可以继续或重试。"
            matches(text, "SSLHandshake|certificate|CertPath|SSL peer|hostname verification") ->
                "安全连接校验失败，请检查系统时间、网络代理或证书设置。"
            matches(text, "ENOSPC|No space left|磁盘空间|存储空间") ->
                "设备存储空间不足，请清理空间后重试。"
            matches(text, "failed to connect|connection refused|network is unreachable|connection reset|connection closed") ->
                "无法连接到网站，请检查网络或代理后重试。"
            matches(text, "unsupported|no video formats") ->
                "没有找到可下载的开放格式，请先更新下载核心。"
            text.isBlank() -> "$action 没有成功，请检查网络后重试。"
            else -> sanitize(text)
        }
    }

    private fun matches(text: String, pattern: String): Boolean =
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)

    private fun sanitize(text: String): String = text
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s{2,}"), " ")
        .trim()
        .takeLast(600)
}
