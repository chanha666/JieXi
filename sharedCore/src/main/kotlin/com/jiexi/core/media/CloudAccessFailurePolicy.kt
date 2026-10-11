package com.jiexi.core.media

/** Do not infer authentication expiry from a URL, filename, or the mere word Cookie. */
object CloudAccessFailurePolicy {
    fun isAuthenticationFailure(message: String): Boolean {
        val safe = message.replace(Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE), "<url>")
        return Regex(
            "(?<![0-9])401(?![0-9])|unauthorized|unauthenticated|not logged[ -]?in|" +
                "(?:cookie|token|session)[^\\r\\n]{0,24}(?:expired|invalid|missing|过期|失效|无效|未配置|为空)|" +
                "(?:expired|invalid|missing)[^\\r\\n]{0,12}(?:cookie|token|session)|" +
                "未登录|(?:请|需要)[^。\\r\\n]{0,16}登录|登录[^。\\r\\n]{0,12}(?:过期|失效|失败)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(safe)
    }
}
