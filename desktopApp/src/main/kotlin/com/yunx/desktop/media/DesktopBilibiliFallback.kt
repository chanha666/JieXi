package com.yunx.desktop.media

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal class DesktopBilibiliFallback(
    private val apiRoot: String = "https://api.bilibili.com",
    private val shortLinkHosts: Set<String> = setOf("b23.tv"),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    ) {
    fun analyze(sourceUrl: String): DesktopMediaPreview {
        val resolvedUrl = resolveShortLinkIfNecessary(sourceUrl)
        val identity = extractIdentity(resolvedUrl)
            ?: error("没有从哔哩哔哩链接中找到 BV 号或 AV 号。")
        val view = fetchJson("${apiRoot.trimEnd('/')}/x/web-interface/view?${identity.query}", resolvedUrl)
        val metadata = parseMetadata(view, extractPageNumber(resolvedUrl))
        val play = fetchJson(
            "${apiRoot.trimEnd('/')}/x/player/playurl?bvid=${encode(metadata.bvid)}" +
                "&cid=${metadata.cid}&qn=127&fnval=1&fnver=0&fourk=1&otype=json",
            "https://www.bilibili.com/video/${metadata.bvid}/"
        )
        return createPreview(sourceUrl, metadata, play)
    }

    private fun resolveShortLinkIfNecessary(url: String): String {
        if (extractIdentity(url) != null) return url
        val initial = runCatching { URI(url) }.getOrNull() ?: return url
        if (initial.host.orEmpty().lowercase() !in shortLinkHosts.map(String::lowercase)) return url
        var current = initial
        val noRedirectClient = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        repeat(5) {
            val request = Request.Builder()
                .url(current.toString())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            noRedirectClient.newCall(request).execute().use { response ->
                if (response.code !in 300..399) return current.toString()
                val location = response.header("Location").orEmpty()
                require(location.isNotBlank()) { "哔哩哔哩短链接没有返回目标地址。" }
                val next = current.resolve(location)
                require(isBilibiliPageHost(next.host.orEmpty())) { "哔哩哔哩短链接跳转到了非官方网站。" }
                current = next
                if (extractIdentity(current.toString()) != null) return current.toString()
            }
        }
        return current.toString()
    }

    private fun isBilibiliPageHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized in shortLinkHosts.map(String::lowercase) ||
            normalized == "bilibili.com" || normalized.endsWith(".bilibili.com")
    }

    private fun fetchJson(url: String, referer: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "哔哩哔哩公开接口请求失败（HTTP ${response.code}）。" }
            val body = response.body?.string().orEmpty()
            require(body.isNotBlank()) { "哔哩哔哩公开接口没有返回内容。" }
            return JSONObject(body)
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
        private val bvidPattern = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
        private val aidPattern = Regex("(?:^|/)av(\\d+)(?:[/\\?#]|$)", RegexOption.IGNORE_CASE)

        internal fun createPreview(sourceUrl: String, view: JSONObject, play: JSONObject): DesktopMediaPreview {
            val metadata = parseMetadata(view, extractPageNumber(sourceUrl))
            return createPreview(sourceUrl, metadata, play)
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
            return uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { item ->
                val pieces = item.split('=', limit = 2)
                pieces.getOrNull(1)?.toIntOrNull()?.takeIf {
                    pieces.firstOrNull().equals("p", ignoreCase = true) && it > 0
                }
            } ?: 1
        }

        private fun parseMetadata(root: JSONObject, requestedPage: Int): Metadata {
            require(root.optInt("code") == 0) {
                root.optString("message").ifBlank { "哔哩哔哩没有返回公开视频资料。" }
            }
            val data = root.optJSONObject("data") ?: error("哔哩哔哩没有返回公开视频资料。")
            val bvid = data.optString("bvid").ifBlank { error("哔哩哔哩没有返回 BV 号。") }
            val pages = data.optJSONArray("pages").objects()
            val selected = pages.firstOrNull { it.optInt("page") == requestedPage }
                ?: pages.getOrNull(requestedPage - 1)
            val cid = selected?.optLong("cid")?.takeIf { it > 0 }
                ?: data.optLong("cid").takeIf { it > 0 }
                ?: error("哔哩哔哩没有返回视频分段编号。")
            val part = selected?.optString("part").orEmpty()
            val baseTitle = data.optString("title").ifBlank { "哔哩哔哩视频 $bvid" }
            return Metadata(
                bvid = bvid,
                cid = cid,
                title = if (pages.size > 1 && part.isNotBlank() && part != baseTitle) "$baseTitle · $part" else baseTitle,
                uploader = data.optJSONObject("owner")?.optString("name").orEmpty(),
                durationSeconds = selected?.optInt("duration")?.takeIf { it > 0 } ?: data.optInt("duration"),
                thumbnail = data.optString("pic")
            )
        }

        private fun createPreview(sourceUrl: String, metadata: Metadata, root: JSONObject): DesktopMediaPreview {
            require(root.optInt("code") == 0) {
                root.optString("message").ifBlank { "哔哩哔哩没有返回公开播放地址。" }
            }
            val data = root.optJSONObject("data") ?: error("哔哩哔哩没有返回公开播放地址。")
            val streams = data.optJSONArray("durl").objects()
            require(streams.size == 1) {
                if (streams.isEmpty()) "哔哩哔哩没有返回匿名可下载的合并视频。"
                else "这个视频由多个旧式分段组成，暂时不能安全地直接下载。"
            }
            val stream = streams.single()
            val candidates = buildList {
                stream.optString("url").takeIf(String::isNotBlank)?.let(::add)
                stream.optJSONArray("backup_url")?.strings()?.filter(String::isNotBlank)?.let(::addAll)
            }.mapNotNull(::safeHttpUrl).distinct()
            require(candidates.isNotEmpty()) { "哔哩哔哩没有返回可用的公开播放地址。" }
            val downloadUrl = candidates.firstOrNull(::isBilibiliCdn) ?: candidates.first()
            val label = qualityLabel(data.optInt("quality"), data.optString("format"))
            return DesktopMediaPreview(
                sourceUrl = sourceUrl,
                downloadUrl = downloadUrl,
                title = metadata.title,
                uploader = metadata.uploader,
                platform = "哔哩哔哩",
                durationSeconds = metadata.durationSeconds,
                thumbnailUrl = metadata.thumbnail,
                formats = listOf(
                    MediaFormatChoice("best", "公开 $label · H264/AAC MP4", stream.optLong("size").coerceAtLeast(0L))
                ),
                engine = "哔哩哔哩匿名直连"
            )
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

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        internal data class VideoIdentity(val display: String, val query: String)
        private data class Metadata(
            val bvid: String,
            val cid: Long,
            val title: String,
            val uploader: String,
            val durationSeconds: Int,
            val thumbnail: String
        )

        private fun JSONArray?.objects(): List<JSONObject> = buildList {
            val array = this@objects ?: return@buildList
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
        }

        private fun JSONArray.strings(): List<String> = buildList {
            for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}
