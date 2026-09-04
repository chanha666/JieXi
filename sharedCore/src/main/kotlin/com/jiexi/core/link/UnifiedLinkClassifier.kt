package com.jiexi.core.link

import java.net.URI

enum class LinkKind {
    CLOUD_SHARE,
    VIDEO_PAGE,
    DIRECT_MEDIA,
    UNKNOWN
}

enum class LinkPlatform(val label: String) {
    QUARK("夸克网盘"),
    UC("UC 网盘"),
    XUNLEI("迅雷网盘"),
    BAIDU("百度网盘"),
    C139("139 网盘"),
    PAN123("123 云盘"),
    YOUTUBE("YouTube"),
    BILIBILI("哔哩哔哩"),
    DOUYIN("抖音"),
    X("X / Twitter"),
    TIKTOK("TikTok"),
    XIAOHONGSHU("小红书"),
    WEIBO("微博"),
    IXIGUA("西瓜视频"),
    AC_FUN("AcFun"),
    TENCENT_VIDEO("腾讯视频"),
    WECHAT_CHANNELS("微信视频号"),
    YOUKU("优酷"),
    IQIYI("爱奇艺"),
    INSTAGRAM("Instagram"),
    DIRECT("媒体直链"),
    OTHER("其他网站")
}

data class ClassifiedLink(
    val originalUrl: String,
    val normalizedUrl: String,
    val kind: LinkKind,
    val platform: LinkPlatform,
    val passcode: String? = null,
    val confidence: Int,
    val mayRequireLogin: Boolean,
    val warning: String? = null
)

object UnifiedLinkClassifier {
    private val urlRegex = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf(
        ',', '.', ';', ':', '!', '?', ')', ']', '}',
        '，', '。', '；', '：', '！', '？', '）', '】', '》', '、'
    )
    private val passcodeRegexes = listOf(
        Regex("(?:提取码|访问码|密码|口令)\\s*[:：]?\\s*([A-Za-z0-9]{4,8})", RegexOption.IGNORE_CASE),
        Regex("(?:pwd|code)\\s*[=:：]\\s*([A-Za-z0-9]{4,8})", RegexOption.IGNORE_CASE)
    )
    private val directExtensions = setOf(
        "mp4", "m4v", "mov", "webm", "mkv", "avi", "flv", "ts", "m3u8", "mpd", "mp3", "m4a", "aac", "flac", "wav", "ogg"
    )

    fun classifyText(text: String): List<ClassifiedLink> {
        val passcode = extractPasscode(text)
        val seen = linkedSetOf<String>()
        return urlRegex.findAll(text)
            .map { it.value.trimEnd(*trailingPunctuation) }
            .mapNotNull { raw -> classifyUrl(raw, passcode) }
            .filter { seen.add(it.normalizedUrl) }
            .toList()
    }

    fun classifyUrl(rawUrl: String, passcode: String? = null): ClassifiedLink? {
        val cleaned = rawUrl.trim().trimEnd(*trailingPunctuation)
        val uri = runCatching { URI(cleaned) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val normalized = normalize(uri)
        val path = uri.path.orEmpty().lowercase()
        val extension = path.substringAfterLast('.', "").substringBefore('/')

        if (extension in directExtensions || path.endsWith("/manifest")) {
            return ClassifiedLink(
                cleaned, normalized, LinkKind.DIRECT_MEDIA, LinkPlatform.DIRECT,
                confidence = 95, mayRequireLogin = false
            )
        }

        cloudPlatform(host)?.let { platform ->
            return ClassifiedLink(
                cleaned,
                normalized,
                LinkKind.CLOUD_SHARE,
                platform,
                passcode,
                confidence = 98,
                mayRequireLogin = platform != LinkPlatform.C139,
                warning = null
            )
        }

        videoPlatform(host)?.let { platform ->
            val likelyProtected = platform in setOf(
                LinkPlatform.TENCENT_VIDEO,
                LinkPlatform.WECHAT_CHANNELS,
                LinkPlatform.YOUKU,
                LinkPlatform.IQIYI
            )
            return ClassifiedLink(
                cleaned,
                normalized,
                LinkKind.VIDEO_PAGE,
                platform,
                confidence = 96,
                mayRequireLogin = likelyProtected,
                warning = if (likelyProtected) "只能下载当前页面实际提供的开放格式，不绕过会员或 DRM" else null
            )
        }

        return ClassifiedLink(
            cleaned,
            normalized,
            LinkKind.UNKNOWN,
            LinkPlatform.OTHER,
            confidence = 40,
            mayRequireLogin = false,
            warning = "尚未确认该网站能否提取，提交后将交给通用媒体引擎检测"
        )
    }

    fun extractPasscode(text: String): String? = passcodeRegexes
        .asSequence()
        .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
        .firstOrNull()

    private fun normalize(uri: URI): String {
        val scheme = uri.scheme.lowercase()
        val host = uri.host.lowercase().removePrefix("www.")
        val port = when {
            uri.port < 0 -> ""
            scheme == "http" && uri.port == 80 -> ""
            scheme == "https" && uri.port == 443 -> ""
            else -> ":${uri.port}"
        }
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$scheme://$host$port$path$query"
    }

    private fun cloudPlatform(host: String): LinkPlatform? = when {
        host == "pan.quark.cn" || host.endsWith(".pan.quark.cn") -> LinkPlatform.QUARK
        host == "drive.uc.cn" || host.endsWith(".drive.uc.cn") -> LinkPlatform.UC
        host == "pan.xunlei.com" || host.endsWith(".pan.xunlei.com") -> LinkPlatform.XUNLEI
        host == "pan.baidu.com" || host == "yun.baidu.com" -> LinkPlatform.BAIDU
        host == "yun.139.com" || host.endsWith(".yun.139.com") -> LinkPlatform.C139
        host == "123pan.com" || host.endsWith(".123pan.com") ||
            host == "123684.com" || host.endsWith(".123684.com") -> LinkPlatform.PAN123
        else -> null
    }

    private fun videoPlatform(host: String): LinkPlatform? = when {
        host == "youtu.be" || host.endsWith("youtube.com") -> LinkPlatform.YOUTUBE
        host == "b23.tv" || host.endsWith("bilibili.com") -> LinkPlatform.BILIBILI
        host.endsWith("douyin.com") || host.endsWith("iesdouyin.com") -> LinkPlatform.DOUYIN
        host == "x.com" || host.endsWith("twitter.com") -> LinkPlatform.X
        host.endsWith("tiktok.com") -> LinkPlatform.TIKTOK
        host == "xhslink.com" || host.endsWith("xiaohongshu.com") -> LinkPlatform.XIAOHONGSHU
        host.endsWith("weibo.com") || host == "weibo.cn" -> LinkPlatform.WEIBO
        host.endsWith("ixigua.com") -> LinkPlatform.IXIGUA
        host.endsWith("acfun.cn") -> LinkPlatform.AC_FUN
        host == "v.qq.com" || host.endsWith("video.qq.com") -> LinkPlatform.TENCENT_VIDEO
        host == "weixin.qq.com" || host.endsWith("channels.weixin.qq.com") -> LinkPlatform.WECHAT_CHANNELS
        host.endsWith("youku.com") -> LinkPlatform.YOUKU
        host.endsWith("iqiyi.com") -> LinkPlatform.IQIYI
        host.endsWith("instagram.com") -> LinkPlatform.INSTAGRAM
        else -> null
    }
}
