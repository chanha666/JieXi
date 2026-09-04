package com.fuke.mobile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Anonymous Bilibili fallback that uses Bilibili's own public metadata and
 * combined-MP4 playurl endpoints. It deliberately avoids the WBI endpoint:
 * WBI is guarded by browser-fingerprint checks and may return HTTP 412 even
 * when the ordinary public metadata endpoint is reachable.
 */
object BilibiliPublicFallback {
    private const val API_ROOT = "https://api.bilibili.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    private val bvidPattern = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
    private val aidPattern = Regex("(?:^|/)av(\\d+)(?:[/\\?#]|$)", RegexOption.IGNORE_CASE)

    fun analyze(url: String): VideoPreview {
        val resolvedUrl = resolveShortLinkIfNecessary(url)
        val identity = extractIdentity(resolvedUrl)
            ?: error("没有从哔哩哔哩链接中找到 BV 号或 AV 号。")
        val page = extractPageNumber(resolvedUrl)
        val viewRoot = fetchJson("$API_ROOT/x/web-interface/view?${identity.query}", resolvedUrl)
        val metadata = parseMetadata(viewRoot, page)
        val playUrl = "$API_ROOT/x/player/playurl?bvid=${encode(metadata.bvid)}" +
            "&cid=${metadata.cid}&qn=127&fnval=1&fnver=0&fourk=1&otype=json"
        val playRoot = fetchJson(playUrl, "https://www.bilibili.com/video/${metadata.bvid}/")
        return createPreview(url, metadata, playRoot)
    }

    internal fun createPreview(sourceUrl: String, viewRoot: JsonObject, playRoot: JsonObject): VideoPreview {
        val metadata = parseMetadata(viewRoot, extractPageNumber(sourceUrl))
        return createPreview(sourceUrl, metadata, playRoot)
    }

    internal fun extractIdentity(url: String): VideoIdentity? {
        bvidPattern.find(url)?.value?.let { matched ->
            val bvid = "BV${matched.substring(2)}"
            return VideoIdentity(bvid, "bvid=${encode(bvid)}")
        }
        aidPattern.find(url)?.groupValues?.getOrNull(1)?.let { aid ->
            return VideoIdentity("av$aid", "aid=${encode(aid)}")
        }
        return null
    }

    internal fun extractPageNumber(url: String): Int {
        val uri = runCatching { URI(url) }.getOrNull() ?: return 1
        return uri.rawQuery.orEmpty()
            .split('&')
            .firstNotNullOfOrNull { item ->
                val (key, value) = item.split('=', limit = 2).let { it.first() to it.getOrElse(1) { "" } }
                value.toIntOrNull()?.takeIf { key.equals("p", ignoreCase = true) && it > 0 }
            }
            ?: 1
    }

    private fun parseMetadata(root: JsonObject, requestedPage: Int): Metadata {
        require(root.int("code") == 0) { root.string("message").ifBlank { "哔哩哔哩没有返回公开视频资料。" } }
        val data = root.objectOrNull("data") ?: error("哔哩哔哩没有返回公开视频资料。")
        val bvid = data.string("bvid").ifBlank { error("哔哩哔哩没有返回 BV 号。") }
        val pages = data.arrayOrNull("pages").orEmptyObjects()
        val selectedPage = pages.firstOrNull { it.int("page") == requestedPage }
            ?: pages.getOrNull(requestedPage - 1)
        val cid = selectedPage?.long("cid")?.takeIf { it > 0 }
            ?: data.long("cid").takeIf { it > 0 }
            ?: error("哔哩哔哩没有返回视频分段编号。")
        val part = selectedPage?.string("part").orEmpty()
        val baseTitle = data.string("title").ifBlank { "哔哩哔哩视频 $bvid" }
        val title = if (pages.size > 1 && part.isNotBlank() && part != baseTitle) "$baseTitle · $part" else baseTitle
        return Metadata(
            bvid = bvid,
            cid = cid,
            title = title,
            uploader = data.objectOrNull("owner")?.string("name").orEmpty(),
            durationSeconds = selectedPage?.int("duration")?.takeIf { it > 0 } ?: data.int("duration"),
            thumbnail = data.string("pic")
        )
    }

    private fun createPreview(sourceUrl: String, metadata: Metadata, root: JsonObject): VideoPreview {
        require(root.int("code") == 0) { root.string("message").ifBlank { "哔哩哔哩没有返回公开播放地址。" } }
        val data = root.objectOrNull("data") ?: error("哔哩哔哩没有返回公开播放地址。")
        val streams = data.arrayOrNull("durl").orEmptyObjects()
        require(streams.size == 1) {
            if (streams.isEmpty()) "哔哩哔哩没有返回匿名可下载的合并视频。"
            else "这个视频由多个旧式分段组成，暂时不能安全地直接下载。"
        }
        val stream = streams.single()
        val candidates = buildList {
            stream.string("url").takeIf(String::isNotBlank)?.let(::add)
            stream.arrayOrNull("backup_url")?.forEach { element ->
                runCatching { element.asString }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.mapNotNull(::safeHttpUrl).distinct()
        require(candidates.isNotEmpty()) { "哔哩哔哩没有返回可用的公开播放地址。" }

        // Bilibili frequently puts a slow third-party MCDN URL first. Its own
        // bilivideo backup is the same signed file and is usually substantially
        // faster, while the primary URL remains the final fallback.
        val downloadUrl = candidates.firstOrNull(::isBilibiliCdn) ?: candidates.first()
        val quality = data.int("quality")
        val label = qualityLabel(quality, data.string("format"))
        val size = stream.long("size").coerceAtLeast(0L)
        return VideoPreview(
            url = sourceUrl,
            downloadUrl = downloadUrl,
            title = metadata.title,
            uploader = metadata.uploader,
            platform = "哔哩哔哩",
            durationSeconds = metadata.durationSeconds,
            thumbnail = metadata.thumbnail,
            formats = listOf(FormatChoice("best", "公开 $label · H264/AAC MP4", size))
        )
    }

    private fun resolveShortLinkIfNecessary(url: String): String {
        if (extractIdentity(url) != null) return url
        val initial = runCatching { URI(url) }.getOrNull() ?: return url
        if (!initial.host.orEmpty().equals("b23.tv", ignoreCase = true)) return url
        var current = initial
        repeat(5) {
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            try {
                val status = connection.responseCode
                if (status !in 300..399) return current.toString()
                val location = connection.getHeaderField("Location").orEmpty()
                require(location.isNotBlank()) { "哔哩哔哩短链接没有返回目标地址。" }
                val next = current.resolve(location)
                require(isBilibiliPageHost(next.host.orEmpty())) { "哔哩哔哩短链接跳转到了非官方网站。" }
                current = next
                if (extractIdentity(current.toString()) != null) return current.toString()
            } finally {
                connection.errorStream?.close()
                connection.disconnect()
            }
        }
        return current.toString()
    }

    private fun fetchJson(url: String, referer: String): JsonObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        return try {
            val status = connection.responseCode
            require(status in 200..299) { "哔哩哔哩公开接口请求失败（HTTP $status）。" }
            connection.inputStream.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        } finally {
            connection.errorStream?.close()
            connection.disconnect()
        }
    }

    private fun safeHttpUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
        return uri.toASCIIString()
    }

    private fun isBilibiliCdn(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "bilivideo.com" || host.endsWith(".bilivideo.com") ||
            host == "bilivideo.cn" || host.endsWith(".bilivideo.cn")
    }

    private fun isBilibiliPageHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "b23.tv" || normalized == "bilibili.com" || normalized.endsWith(".bilibili.com")
    }

    private fun qualityLabel(quality: Int, format: String): String = when (quality) {
        127 -> "8K"
        126 -> "杜比视界"
        125 -> "HDR"
        120 -> "4K"
        116 -> "1080P 60帧"
        112 -> "1080P 高码率"
        80 -> "1080P"
        74 -> "720P 60帧"
        64 -> "720P"
        32 -> "480P"
        16 -> "360P"
        6 -> "240P"
        else -> format.ifBlank { "公开画质" }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    internal data class VideoIdentity(val display: String, val query: String)
    private data class Metadata(
        val bvid: String,
        val cid: Long,
        val title: String,
        val uploader: String,
        val durationSeconds: Int,
        val thumbnail: String
    )
}

private fun JsonObject.objectOrNull(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arrayOrNull(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.string(name: String): String =
    get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

private fun JsonObject.int(name: String): Int =
    get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() } ?: 0

private fun JsonObject.long(name: String): Long =
    get(name)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L

private fun JsonArray?.orEmptyObjects(): List<JsonObject> = this?.mapNotNull { element ->
    element.takeIf { it.isJsonObject }?.asJsonObject
}.orEmpty()
