package com.fuke.mobile

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

private fun standardFormats() = listOf(
    FormatChoice("bestvideo+bestaudio/best", "匿名最高画质（可到 8K）"),
    FormatChoice("bestvideo[height<=2160]+bestaudio/best[height<=2160]", "最高 4K"),
    FormatChoice("bestvideo[height<=1080]+bestaudio/best[height<=1080]", "最高 1080p"),
    FormatChoice("bestaudio/best", "仅音频 MP3")
)

object YouTubeFallback {
    private val idPattern = Regex("^[A-Za-z0-9_-]{6,15}$")

    fun analyze(url: String): VideoPreview {
        val id = extractVideoId(url) ?: error("没有检测到有效的 YouTube 视频编号。")
        val fallback = VideoPreview(
            url = url,
            title = "YouTube 视频 $id",
            uploader = "",
            platform = "YouTube",
            durationSeconds = 0,
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            formats = standardFormats()
        )
        return runCatching {
            val encoded = URLEncoder.encode(url, Charsets.UTF_8.name())
            val connection = URI("https://www.youtube.com/oembed?url=$encoded&format=json").toURL()
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 1_200
            connection.readTimeout = 1_200
            connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            connection.inputStream.bufferedReader().use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                fallback.copy(
                    title = json.get("title")?.asString ?: fallback.title,
                    uploader = json.get("author_name")?.asString.orEmpty(),
                    thumbnail = json.get("thumbnail_url")?.asString ?: fallback.thumbnail
                )
            }
        }.getOrDefault(fallback)
    }

    internal fun extractVideoId(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val candidate = when {
            host == "youtu.be" || host.endsWith(".youtu.be") -> uri.path.trim('/').substringBefore('/')
            else -> {
                val pathParts = uri.path.trim('/').split('/')
                when (pathParts.firstOrNull()) {
                    "shorts", "embed", "live" -> pathParts.getOrNull(1)
                    else -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')
                }
            }
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }
}

object BilibiliFallback {
    private val bvidPattern = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)

    fun analyzeOrNull(url: String): VideoPreview? {
        val bvid = bvidPattern.find(url)?.value ?: return null
        return runCatching {
            val connection = URI("https://api.bilibili.com/x/web-interface/view?bvid=$bvid").toURL()
                .openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 4_000
            connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            connection.setRequestProperty("Referer", "https://www.bilibili.com/")
            connection.inputStream.bufferedReader().use { reader ->
                val root = JsonParser.parseReader(reader).asJsonObject
                if (root.get("code")?.asInt != 0) return@runCatching null
                val data = root.getAsJsonObject("data")
                VideoPreview(
                    url = url,
                    title = data.get("title")?.asString ?: "哔哩哔哩视频 $bvid",
                    uploader = data.getAsJsonObject("owner")?.get("name")?.asString.orEmpty(),
                    platform = "哔哩哔哩",
                    durationSeconds = data.get("duration")?.asInt ?: 0,
                    thumbnail = data.get("pic")?.asString.orEmpty(),
                    formats = standardFormats()
                )
            }
        }.getOrNull()
    }
}
