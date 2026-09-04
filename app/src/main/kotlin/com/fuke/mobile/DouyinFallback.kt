package com.fuke.mobile

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI

/**
 * Reads the public Douyin mobile share page. This is deliberately independent
 * from the private detail API, whose a_bogus signature changes frequently.
 */
object DouyinFallback {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private data class FetchedPage(val finalUrl: String, val html: String)

    fun analyze(url: String): VideoPreview {
        // The official Douyin Featured page currently exposes the public MP4
        // renditions in its server-rendered state. Prefer it whenever the input
        // already contains a video id: this avoids starting Python/yt-dlp and
        // avoids requiring a logged-in account or a manually exported cookie.
        extractVideoId(url)?.let { id ->
            runCatching {
                val page = fetch("https://jingxuan.douyin.com/m/video/$id", linkedMapOf())
                return parseFeaturedPage(url, page.html)
            }
        }

        // Short v.douyin.com links first need one official redirect request to
        // reveal the numeric id. Keep the old mobile-page parser as a fallback
        // for deployments where Douyin still embeds videoInfoRes directly.
        val page = fetch(url, linkedMapOf())
        runCatching { return parseLegacyPage(url, page.html) }
        val id = extractVideoId(page.finalUrl)
            ?: extractVideoId(page.html)
            ?: error("抖音分享页没有返回可识别的视频编号。")
        val featured = fetch("https://jingxuan.douyin.com/m/video/$id", linkedMapOf())
        return parseFeaturedPage(url, featured.html)
    }

    private fun parseLegacyPage(url: String, html: String): VideoPreview {
        val json = Regex(
            "window\\._ROUTER_DATA\\s*=\\s*(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1)
            ?: error("抖音公开分享页没有返回视频资料，请稍后重试。")

        val root = JsonParser.parseString(json)
        val videoInfo = findObject(root, "videoInfoRes")
            ?: error("抖音公开分享页暂时没有可用的视频资料。")
        val item = videoInfo.array("item_list")?.firstOrNull()?.asJsonObject
            ?: error("这个抖音链接没有公开可下载的视频。")
        val video = item.obj("video") ?: error("抖音没有返回公开的视频流。")
        val playUrl = video.obj("play_addr")?.array("url_list")
            ?.firstOrNull()?.asString.orEmpty()
            .replace("playwm", "play", ignoreCase = true)
        if (playUrl.isBlank()) error("抖音没有返回公开的视频地址。")

        val title = item.text("desc").ifBlank { "抖音视频" }
        val uploader = item.obj("author")?.text("nickname").orEmpty()
        val thumbnail = video.obj("cover")?.array("url_list")
            ?.firstOrNull()?.asString.orEmpty()
        val duration = (video.long("duration") / 1000L).toInt()
        val height = video.int("height")
        val width = video.int("width")
        val resolution = when {
            height > 0 -> "${height}p"
            width > 0 -> "${width} 像素"
            else -> "公开最高画质"
        }

        return VideoPreview(
            url = url,
            downloadUrl = playUrl,
            title = title,
            uploader = uploader,
            platform = "抖音",
            durationSeconds = duration,
            thumbnail = thumbnail,
            formats = listOf(
                FormatChoice("best", "公开最高画质 · $resolution"),
                FormatChoice("bestaudio/best", "仅音频 MP3")
            )
        )
    }

    internal fun parseFeaturedPage(sourceUrl: String, html: String): VideoPreview {
        val json = Regex(
            "window\\._SSR_DATA\\s*=\\s*(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1)?.trim()?.removeSuffix(";")
            ?: error("抖音精选公开页没有返回视频资料。")
        val root = JsonParser.parseString(json).asJsonObject
        val result = root.obj("data")
            ?.obj("storeState")
            ?.obj("detail")
            ?.obj("videoData")
            ?.obj("result")
            ?: error("抖音精选公开页的视频资料不完整。")
        val modelJson = result.text("video_model")
        val model = runCatching { JsonParser.parseString(modelJson).asJsonObject }
            .getOrElse { error("抖音精选公开页的视频流资料无效。") }
        val candidates = model.array("video_list")
            ?.mapNotNull { element -> element.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .orEmpty()
            .filter { it.text("main_url").isNotBlank() }
        if (candidates.isEmpty()) error("这个抖音链接没有公开可下载的视频。")

        val h264 = candidates.filter { it.obj("video_meta")?.text("codec_type").equals("h264", true) }
        val best = (h264.ifEmpty { candidates }).maxWithOrNull(
            compareBy<JsonObject> { it.obj("video_meta")?.int("vheight") ?: 0 }
                .thenBy { it.obj("video_meta")?.int("vwidth") ?: 0 }
                .thenBy { it.obj("video_meta")?.long("bitrate") ?: 0L }
        ) ?: error("抖音精选公开页没有可用的视频流。")
        val meta = best.obj("video_meta")
        val downloadUrl = best.text("main_url")
        val definition = meta?.text("definition").orEmpty()
        val codec = meta?.text("codec_type").orEmpty().uppercase()
        val qualityLabel = listOf(definition, codec).filter(String::isNotBlank).joinToString(" · ")
            .ifBlank { "公开最高画质" }
        val title = result.text("title").ifBlank { result.text("abstract") }.ifBlank { "抖音视频" }
        val uploader = result.obj("media_user")?.text("screen_name").orEmpty()
        val duration = model.double("video_duration").toInt().coerceAtLeast(0)

        return VideoPreview(
            url = sourceUrl,
            downloadUrl = downloadUrl,
            title = title,
            uploader = uploader,
            platform = "抖音",
            durationSeconds = duration,
            thumbnail = result.text("cover_image_url"),
            formats = listOf(
                FormatChoice("best", "公开最高画质 · $qualityLabel", meta?.long("size") ?: 0L),
                FormatChoice("bestaudio/best", "仅音频 MP3")
            )
        )
    }

    internal fun extractVideoId(value: String): String? {
        val patterns = listOf(
            Regex("(?:video/|modal_id=)(\\d{15,24})", RegexOption.IGNORE_CASE),
            Regex("[\"']gid[\"']\\s*[:=]\\s*[\"'](\\d{15,24})[\"']", RegexOption.IGNORE_CASE),
            Regex("https?://jingxuan\\.douyin\\.com/m/video/(\\d{15,24})", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern -> pattern.find(value)?.groupValues?.getOrNull(1) }
    }

    private fun fetch(startUrl: String, cookies: MutableMap<String, String>): FetchedPage {
        var current = startUrl
        repeat(8) {
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (cookies.isNotEmpty()) {
                    connection.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
                connection.connect()
                connection.headerFields.entries
                    .filter { (name, _) -> name.equals("Set-Cookie", ignoreCase = true) }
                    .flatMap { it.value.orEmpty() }
                    .forEach { raw ->
                        val pair = raw.substringBefore(';').split('=', limit = 2)
                        if (pair.size == 2 && pair[0].isNotBlank()) cookies[pair[0]] = pair[1]
                    }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("抖音分享链接跳转失败。")
                    current = URI(current).resolve(location).toString()
                    return@repeat
                }
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
                if (code >= 400) error("抖音分享页请求失败（$code）。")
                return FetchedPage(current, body)
            } finally {
                connection.disconnect()
            }
        }
        error("抖音分享链接跳转次数过多。")
    }

    private fun findObject(element: JsonElement?, key: String): JsonObject? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonObject) {
            val objectValue = element.asJsonObject
            objectValue.get(key)?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
            objectValue.entrySet().forEach { (_, child) -> findObject(child, key)?.let { return it } }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { child -> findObject(child, key)?.let { return it } }
        }
        return null
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.text(key: String) = runCatching { get(key)?.asString.orEmpty() }.getOrDefault("")
    private fun JsonObject.long(key: String) = runCatching { get(key)?.asLong ?: 0L }.getOrDefault(0L)
    private fun JsonObject.int(key: String) = runCatching { get(key)?.asInt ?: 0 }.getOrDefault(0)
    private fun JsonObject.double(key: String) = runCatching { get(key)?.asDouble ?: 0.0 }.getOrDefault(0.0)
}
