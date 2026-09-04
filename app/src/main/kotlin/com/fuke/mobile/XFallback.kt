package com.fuke.mobile

import com.yunx.app.BuildConfig

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.roundToInt

/** Fast anonymous fallback for public X posts, backed by the open-source FxEmbed API. */
object XFallback {
    fun analyze(url: String): VideoPreview {
        val statusId = Regex("/status/(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
            ?: error("没有从 X 链接中找到帖子编号。")
        val endpoint = "https://api.fxtwitter.com/i/status/$statusId"
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "JieXi-Android/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept", "application/json")
        val (code, body) = try {
            connection.connect()
            val responseCode = connection.responseCode
            val stream = if (responseCode >= 400) connection.errorStream else connection.inputStream
            responseCode to stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
        if (code >= 400) error("X 公开帖子接口请求失败（$code）。")

        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("code")?.asInt != 200) {
            error(root.get("message")?.asString ?: "X 没有返回公开帖子资料。")
        }
        val tweet = root.getAsJsonObject("tweet") ?: error("X 没有返回公开帖子资料。")
        val videos = tweet.getAsJsonObject("media")?.getAsJsonArray("videos")
            ?: error("这个 X 帖子没有公开可下载的视频。")
        val video = videos.firstOrNull()?.asJsonObject
            ?: error("这个 X 帖子没有公开可下载的视频。")
        // FxEmbed already returns the downloadable media URL. Rewriting its host
        // produced a non-existent video.fxtwitter.com address for normal X videos.
        val mediaUrl = video.get("url")?.asString.orEmpty()
        if (mediaUrl.isBlank()) error("X 没有返回公开的视频地址。")

        val author = tweet.getAsJsonObject("author")?.get("name")?.asString.orEmpty()
        val text = tweet.get("text")?.asString.orEmpty().ifBlank { "X 视频" }
        val height = video.get("height")?.asInt ?: 0
        return VideoPreview(
            url = url,
            downloadUrl = mediaUrl,
            title = if (author.isBlank()) text else "$author - $text",
            uploader = author,
            platform = "X",
            durationSeconds = (video.get("duration")?.asDouble ?: 0.0).roundToInt(),
            thumbnail = video.get("thumbnail_url")?.asString.orEmpty(),
            formats = listOf(
                FormatChoice("best", if (height > 0) "公开最高画质 · ${height}p" else "公开最高画质"),
                FormatChoice("bestaudio/best", "仅音频 MP3")
            )
        )
    }
}
