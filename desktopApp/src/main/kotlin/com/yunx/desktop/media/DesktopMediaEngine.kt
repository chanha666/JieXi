package com.yunx.desktop.media

import com.jiexi.core.link.ClassifiedLink
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.LinkPlatform
import com.jiexi.core.link.UnifiedLinkClassifier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class DesktopMediaEngine(
    val tools: ToolPaths = ToolPaths.locate(),
    private val xApiBaseUrl: String = FXTWITTER_API_BASE_URL,
    private val douyinJingxuanBaseUrl: String = DOUYIN_JINGXUAN_BASE_URL,
    private val bilibiliApiBaseUrl: String = BILIBILI_API_BASE_URL
) {
    data class ToolPaths(
        val root: File,
        val ytDlp: File,
        val deno: File,
        val ffmpegDirectory: File,
        val ffmpeg: File,
        val ffprobe: File,
        val pluginDirectory: File
    ) {
        val ready: Boolean
            get() = listOf(ytDlp, deno, ffmpeg, ffprobe).all { it.isFile && it.length() > 0L }

        companion object {
            fun locate(): ToolPaths {
                val codeLocation = runCatching {
                    File(DesktopMediaEngine::class.java.protectionDomain.codeSource.location.toURI())
                }.getOrNull()
                return locateFrom(
                    propertyRoot = System.getProperty("jiexi.media.engine.dir"),
                    environmentRoot = System.getenv("JIEXI_MEDIA_ENGINE_DIR"),
                    jpackageAppPath = System.getProperty("jpackage.app-path"),
                    workingDirectory = File(System.getProperty("user.dir", ".")),
                    codeLocation = codeLocation
                )
            }

            internal fun locateFrom(
                propertyRoot: String? = null,
                environmentRoot: String? = null,
                jpackageAppPath: String? = null,
                workingDirectory: File,
                codeLocation: File? = null
            ): ToolPaths {
                val candidates = linkedSetOf<File>()
                fun addCandidate(file: File) {
                    candidates += file.toPath().toAbsolutePath().normalize().toFile()
                }

                propertyRoot?.takeIf(String::isNotBlank)?.let { addCandidate(File(it)) }
                environmentRoot?.takeIf(String::isNotBlank)?.let { addCandidate(File(it)) }
                jpackageAppPath?.takeIf(String::isNotBlank)?.let { appPath ->
                    val appRoot = File(appPath).absoluteFile.parentFile
                    addCandidate(File(appRoot, "app/vendor"))
                    addCandidate(File(appRoot, "vendor"))
                }
                codeLocation?.let { location ->
                    val parent = if (location.isFile) location.parentFile else location
                    parent?.let { addCandidate(File(it, "vendor")) }
                    parent?.parentFile?.let { addCandidate(File(it, "app/vendor")) }
                }
                val working = workingDirectory.toPath().toAbsolutePath().normalize().toFile()
                addCandidate(File(working, "tools/media-engine"))
                addCandidate(File(working, "vendor"))
                addCandidate(File(working, "../tools/media-engine"))
                addCandidate(File(working, "../../tools/media-engine"))

                val resolved = candidates.map(::fromRoot)
                return resolved.firstOrNull { it.ready }
                    ?: resolved.firstOrNull { it.ytDlp.isFile && it.ytDlp.length() > 0L }
                    ?: resolved.first()
            }

            private fun fromRoot(root: File): ToolPaths {
                val ffmpegDirectory = File(root, "ffmpeg/bin")
                return ToolPaths(
                    root = root,
                    ytDlp = File(root, "yt-dlp.exe"),
                    deno = File(root, "deno.exe"),
                    ffmpegDirectory = ffmpegDirectory,
                    ffmpeg = File(ffmpegDirectory, "ffmpeg.exe"),
                    ffprobe = File(ffmpegDirectory, "ffprobe.exe"),
                    pluginDirectory = File(root, "plugins")
                )
            }
        }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun analyze(rawText: String): DesktopMediaPreview {
        val classified = UnifiedLinkClassifier.classifyText(rawText).firstOrNull()
            ?: error("没有检测到完整的网址。")
        require(classified.kind != LinkKind.CLOUD_SHARE) { "这是网盘分享链接，请使用网盘解析。" }

        val fast = when (classified.kind) {
            LinkKind.DIRECT_MEDIA -> analyzeDirect(classified)
            else -> when (classified.platform) {
                LinkPlatform.YOUTUBE -> analyzeYouTube(classified)
                LinkPlatform.BILIBILI -> analyzeBilibili(classified)
                LinkPlatform.DOUYIN -> runCatching { analyzeDouyin(classified) }.getOrNull()
                LinkPlatform.X -> runCatching { analyzeX(classified) }.getOrNull()
                else -> null
            }
        }
        return fast ?: analyzeWithYtDlp(classified)
    }

    fun coreStatus(): MediaToolStatus {
        if (!tools.ready) {
            return MediaToolStatus("缺失", "缺失", "缺失", false, false, "媒体核心文件不完整：${tools.root.absolutePath}")
        }
        val ytDlp = runCatching { runAndCollect(tools.ytDlp, listOf("--version"), 20).lineSequence().firstOrNull().orEmpty() }.getOrDefault("未知")
        val ffmpeg = runCatching { runAndCollect(tools.ffmpeg, listOf("-version"), 20).lineSequence().firstOrNull().orEmpty() }
            .getOrDefault("未知").substringAfter("ffmpeg version ").substringBefore(' ')
        val deno = runCatching { runAndCollect(tools.deno, listOf("--version"), 20).lineSequence().firstOrNull().orEmpty() }
            .getOrDefault("未知").substringAfter("deno ").substringBefore(' ')
        val plugin = File(tools.pluginDirectory, "wechat/yt_dlp_plugins/extractor/wechat.py").isFile
        return MediaToolStatus(ytDlp, ffmpeg, deno, plugin, true, "本地组件完整，可离线启动")
    }

    fun startDownload(task: DesktopMediaTask, outputDirectory: File, concurrentFragments: Int, retries: Int = 10, nameRule: String = "title-id", speedLimit: Long = 0L): Process {
        require(tools.ready) { "媒体核心不完整，请重新安装解析。" }
        outputDirectory.mkdirs()
        require(outputDirectory.isDirectory && outputDirectory.canWrite()) { "下载目录不可写：${outputDirectory.absolutePath}" }

        // Bilibili's anonymous durl is signed and short-lived. Refresh it when
        // the user actually starts the download instead of trusting the URL
        // captured during an earlier preview.
        val refreshedDownloadUrl = resolveFreshDownloadUrl(task)
        val source = refreshedDownloadUrl.ifBlank { task.sourceUrl }
        val safeTitle = sanitizeFileName(task.title).take(150).ifBlank { "解析视频" }
        val outputTemplate = if (refreshedDownloadUrl.isNotBlank()) "$safeTitle.%(ext)s" else when(nameRule) {
            "title" -> "%(title).160B.%(ext)s"
            "uploader-title" -> "%(uploader,channel,creator|作者)s - %(title).140B.%(ext)s"
            else -> "%(title).160B [%(id)s].%(ext)s"
        }
        val args = mutableListOf(
            "--ignore-config",
            "--encoding", "utf-8",
            "--newline",
            "--color", "never",
            "--continue",
            "--no-playlist",
            "--windows-filenames",
            "--no-mtime",
            "--paths", outputDirectory.absolutePath,
            "--output", outputTemplate,
            "--progress-template", "download:[progress] %(progress._percent_str)s|%(progress._speed_str)s|%(progress._eta_str)s",
            "--print", "after_move:[finished] %(filepath)s",
            "--retries", retries.coerceIn(0,10).toString(),
            "--fragment-retries", retries.coerceIn(0,10).toString(),
            "--retry-sleep", "exp=1:20",
            "--socket-timeout", "60",
            "--concurrent-fragments", concurrentFragments.coerceIn(1, 64).toString(),
            "--ffmpeg-location", tools.ffmpegDirectory.absolutePath,
            "--js-runtimes", "deno:${tools.deno.absolutePath}"
        )
        if (tools.pluginDirectory.isDirectory) args += listOf("--plugin-dirs", tools.pluginDirectory.absolutePath)
        if(speedLimit > 0) args += listOf("--limit-rate", speedLimit.toString())
        args += listOf("-f", task.formatSelector)
        when (task.platform) {
            "抖音" -> args += listOf("--add-header", "Referer:https://www.douyin.com/", "--add-header", "User-Agent:$DESKTOP_USER_AGENT")
            "哔哩哔哩" -> args += listOf("--add-header", "Referer:https://www.bilibili.com/", "--add-header", "User-Agent:$DESKTOP_USER_AGENT")
            "X / Twitter", "X" -> args += listOf("--add-header", "Referer:https://x.com/", "--force-ipv4")
        }
        val bypassSystemProxy = isLoopbackMediaUrl(source)
        if (bypassSystemProxy) {
            // urllib's Windows proxy discovery does not consistently honor the
            // system localhost bypass list. Local/direct sources must stay local.
            // Keep the empty value attached to the option. This survives the
            // Windows ProcessBuilder command-line encoder as an actual value.
            args += "--proxy="
        }
        if (task.outputFormat == "mp3") {
            args += listOf("-x", "--audio-format", "mp3", "--audio-quality", "0")
        } else {
            args += listOf("--merge-output-format", "mp4")
            if (task.embedSubtitles) {
                args += listOf("--write-subs", "--write-auto-subs", "--sub-langs", "zh.*,en.*", "--embed-subs")
            }
        }
        args += source
        return processBuilder(tools.ytDlp, args).apply {
            if (bypassSystemProxy) {
                environment()["NO_PROXY"] = "*"
                environment()["no_proxy"] = "*"
            }
        }.start()
    }

    internal fun resolveFreshDownloadUrl(task: DesktopMediaTask): String {
        if (task.platform !in setOf("哔哩哔哩", "抖音", "X", "X / Twitter")) return task.downloadUrl
        return runCatching {
            when (task.platform) {
                "哔哩哔哩" -> DesktopBilibiliFallback(apiRoot = bilibiliApiBaseUrl).analyze(task.sourceUrl).downloadUrl
                else -> analyze(task.sourceUrl).downloadUrl
            }
        }.getOrNull().orEmpty().ifBlank { task.downloadUrl }
    }

    fun runFfmpeg(arguments: List<String>, timeoutSeconds: Long = 3600): String {
        require(tools.ffmpeg.isFile) { "FFmpeg 组件不存在。" }
        return runAndCollect(tools.ffmpeg, listOf("-y", "-nostdin", "-hide_banner") + arguments, timeoutSeconds)
    }

    fun runFfprobe(arguments: List<String>, timeoutSeconds: Long = 120): String {
        require(tools.ffprobe.isFile) { "FFprobe 组件不存在。" }
        return runAndCollect(tools.ffprobe, listOf("-hide_banner") + arguments, timeoutSeconds)
    }

    fun updateYtDlp(): String {
        require(tools.ytDlp.isFile) { "yt-dlp 核心不存在。" }
        return runAndCollect(tools.ytDlp, listOf("-U", "--update-to", "stable"), 240)
    }

    private fun analyzeDirect(link: ClassifiedLink): DesktopMediaPreview {
        val uri = URI(link.normalizedUrl)
        val rawName = uri.path.substringAfterLast('/').ifBlank { "直链媒体" }
        val title = runCatching { URLDecoder.decode(rawName, StandardCharsets.UTF_8) }.getOrDefault(rawName)
            .substringBeforeLast('.').ifBlank { "直链媒体" }
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            downloadUrl = link.originalUrl,
            title = title,
            platform = "媒体直链",
            formats = defaultFormats(direct = true),
            engine = "直链即时识别"
        )
    }

    private fun analyzeYouTube(link: ClassifiedLink): DesktopMediaPreview {
        val uri = URI(link.normalizedUrl)
        val id = when {
            uri.host.equals("youtu.be", true) -> uri.path.trim('/').substringBefore('/')
            uri.path.trim('/').substringBefore('/') in setOf("shorts", "embed", "live") -> uri.path.trim('/').split('/').getOrNull(1).orEmpty()
            else -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=').orEmpty()
        }
        require(id.matches(Regex("[A-Za-z0-9_-]{6,15}"))) { "没有检测到有效的 YouTube 视频编号。" }
        var title = "YouTube 视频 $id"
        var uploader = ""
        var thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        runCatching {
            val endpoint = "https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(link.originalUrl, StandardCharsets.UTF_8)}&format=json"
            val json = fetchJson(endpoint, 2_000)
            title = json.optString("title", title)
            uploader = json.optString("author_name")
            thumbnail = json.optString("thumbnail_url", thumbnail)
        }
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            title = title,
            uploader = uploader,
            platform = link.platform.label,
            thumbnailUrl = thumbnail,
            formats = defaultFormats(),
            engine = "YouTube 轻量解析",
            warning = link.warning
        )
    }

    private fun analyzeBilibili(link: ClassifiedLink): DesktopMediaPreview? {
        return runCatching {
            DesktopBilibiliFallback(apiRoot = bilibiliApiBaseUrl).analyze(link.originalUrl)
        }.getOrNull()
    }

    private fun analyzeX(link: ClassifiedLink): DesktopMediaPreview {
        val id = Regex("/status/(\\d+)", RegexOption.IGNORE_CASE).find(link.originalUrl)?.groupValues?.getOrNull(1)
            ?: error("没有从 X 链接中找到帖子编号。")
        val root = fetchJson("${xApiBaseUrl.trimEnd('/')}/i/status/$id", headers = mapOf("User-Agent" to "JieXi-Windows/4.0.0"))
        require(root.optInt("code") == 200) { root.optString("message", "X 没有返回公开帖子资料。") }
        val tweet = root.optJSONObject("tweet") ?: error("X 没有返回公开帖子资料。")
        val video = tweet.optJSONObject("media")?.optJSONArray("videos")?.optJSONObject(0)
            ?: error("这个 X 帖子没有公开可下载的视频。")
        // FxTwitter's API already returns the downloadable media URL. Rewriting
        // its host breaks valid video.twimg.com URLs and is outside the API contract.
        val mediaUrl = video.optString("url")
        require(mediaUrl.isNotBlank()) { "X 没有返回公开的视频地址。" }
        val author = tweet.optJSONObject("author")?.optString("name").orEmpty()
        val body = tweet.optString("text", "X 视频")
        val height = video.optInt("height")
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            downloadUrl = mediaUrl,
            title = if (author.isBlank()) body else "$author - $body",
            uploader = author,
            platform = link.platform.label,
            durationSeconds = video.optDouble("duration", 0.0).roundToInt(),
            thumbnailUrl = video.optString("thumbnail_url"),
            formats = listOf(
                MediaFormatChoice("best", if (height > 0) "公开最高画质 · ${height}p" else "公开最高画质"),
                MediaFormatChoice("bestaudio/best", "仅音频 MP3")
            ),
            engine = "X 公开接口",
            warning = link.warning
        )
    }

    internal fun analyzeDouyin(link: ClassifiedLink): DesktopMediaPreview {
        var resolvedUrl = link.normalizedUrl
        var legacyFailure: Throwable? = null
        runCatching {
            val page = fetchDouyinPage(link.originalUrl)
            resolvedUrl = page.finalUrl
            require(page.successful) { "抖音分享页请求失败（${page.statusCode}）。" }
            parseDouyinSharePage(link, page.html)
        }.onSuccess { return it }
            .onFailure { legacyFailure = it }

        val videoId = extractDouyinVideoId(link.originalUrl, link.normalizedUrl, resolvedUrl)
            ?: throw IllegalStateException(
                legacyFailure?.message ?: "没有从抖音链接中找到视频编号。",
                legacyFailure
            )
        return runCatching { analyzeDouyinJingxuan(link, videoId) }
            .getOrElse { failure ->
                throw IllegalStateException(
                    failure.message ?: "抖音精选公开页没有返回可下载的视频。",
                    failure
                )
            }
    }

    private fun fetchDouyinPage(url: String): DouyinPage {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Upgrade-Insecure-Requests", "1")
            .build()
        return http.newCall(request).execute().use { response ->
            DouyinPage(
                html = response.body?.string().orEmpty(),
                finalUrl = response.request.url.toString(),
                statusCode = response.code,
                successful = response.isSuccessful
            )
        }
    }

    private fun parseDouyinSharePage(link: ClassifiedLink, html: String): DesktopMediaPreview {
        val routerJson = Regex(
            "window\\._ROUTER_DATA\\s*=\\s*(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1)?.trim()?.removeSuffix(";")
        val renderJson = Regex(
            "<script[^>]+id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.getOrNull(1)?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
        val root = JSONObject(routerJson ?: renderJson ?: error("抖音公开分享页没有返回视频资料。"))
        val videoInfo = findObject(root, "videoInfoRes")
        val item = videoInfo?.optJSONArray("item_list")?.optJSONObject(0)
            ?: findObject(root, "aweme_detail")
            ?: error("这个抖音链接没有公开可下载的视频。")
        val video = item.optJSONObject("video") ?: error("抖音没有返回公开的视频流。")
        val candidates = mutableListOf<JSONObject>()
        video.optJSONObject("play_addr")?.let(candidates::add)
        video.optJSONArray("bit_rate")?.objects()?.forEach { rate -> rate.optJSONObject("play_addr")?.let(candidates::add) }
        val playUrl = candidates.asSequence()
            .flatMap { it.optJSONArray("url_list")?.strings().orEmpty().asSequence() }
            .firstOrNull(String::isNotBlank)
            .orEmpty()
            .replace("playwm", "play", ignoreCase = true)
        require(playUrl.isNotBlank()) { "抖音没有返回公开的视频地址。" }
        val author = item.optJSONObject("author")?.optString("nickname").orEmpty()
        val title = item.optString("desc").ifBlank { "抖音视频" }
        val height = video.optInt("height")
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            downloadUrl = playUrl,
            title = title,
            uploader = author,
            platform = link.platform.label,
            durationSeconds = (video.optLong("duration") / 1000L).toInt(),
            thumbnailUrl = video.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0).orEmpty(),
            formats = listOf(
                MediaFormatChoice("best", if (height > 0) "公开最高画质 · ${height}p" else "公开最高画质"),
                MediaFormatChoice("bestaudio/best", "仅音频 MP3")
            ),
            engine = "抖音公开分享页",
            warning = link.warning
        )
    }

    private fun analyzeDouyinJingxuan(link: ClassifiedLink, videoId: String): DesktopMediaPreview {
        val page = fetchDouyinPage("${douyinJingxuanBaseUrl.trimEnd('/')}/m/video/$videoId")
        require(page.successful) { "抖音精选公开页请求失败（${page.statusCode}）。" }
        val root = extractAssignedJsonObject(page.html, "window._SSR_DATA")
            ?: error("抖音精选公开页没有返回视频资料。")
        val result = root.objectAt("data", "storeState", "detail", "videoData", "result")
            ?: error("抖音精选公开页的视频资料不完整。")
        val model = when (val rawModel = result.opt("video_model")) {
            is JSONObject -> rawModel
            is String -> runCatching { JSONObject(rawModel) }.getOrNull()
            else -> null
        } ?: error("抖音精选公开页没有返回视频流清单。")

        val streams = jsonObjects(model.opt("video_list"))
            .mapNotNull(::douyinStream)
        require(streams.isNotEmpty()) { "抖音精选公开页没有返回可下载的视频地址。" }
        val preferred = streams.filter(DouyinStream::isH264Mp4).ifEmpty { streams }
        val selected = preferred.maxWithOrNull(
            compareBy<DouyinStream> { it.pixels }
                .thenBy { it.bitrate }
                .thenBy { it.sizeBytes }
        ) ?: error("抖音精选公开页没有返回可下载的视频地址。")

        val title = result.optString("abstract").ifBlank {
            result.optString("title").ifBlank { "抖音视频 $videoId" }
        }
        val uploader = result.optJSONObject("media_user")?.optString("screen_name").orEmpty()
        val duration = model.optDouble("video_duration", 0.0).takeIf { it > 0.0 }
            ?: result.optDouble("duration", 0.0)
        val definition = selected.definition.ifBlank {
            selected.height.takeIf { it > 0 }?.let { "${it}p" }.orEmpty()
        }
        val formatLabel = buildList {
            add("公开最高画质")
            definition.takeIf(String::isNotBlank)?.let(::add)
            selected.codec.takeIf(String::isNotBlank)?.uppercase()?.let(::add)
        }.joinToString(" · ")
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            downloadUrl = selected.url,
            title = title,
            uploader = uploader,
            platform = link.platform.label,
            durationSeconds = duration.roundToInt(),
            thumbnailUrl = result.optString("cover_image_url"),
            formats = listOf(
                MediaFormatChoice("best", formatLabel, selected.sizeBytes),
                MediaFormatChoice("bestaudio/best", "仅音频 MP3")
            ),
            engine = "抖音精选公开页",
            warning = link.warning
        )
    }

    private fun douyinStream(value: JSONObject): DouyinStream? {
        val meta = value.optJSONObject("video_meta") ?: value
        val url = value.optString("main_url").ifBlank { value.optString("backup_url") }
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
        val definition = meta.optString("definition")
        val parsedHeight = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
            .find(definition)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val height = meta.optInt("vheight").takeIf { it > 0 } ?: parsedHeight
        val width = meta.optInt("vwidth").takeIf { it > 0 } ?: 0
        val codec = meta.optString("codec_type").ifBlank { meta.optString("codec") }
        val format = meta.optString("vtype").ifBlank { meta.optString("format") }
        val bitrate = maxOf(meta.optLong("real_bitrate"), meta.optLong("bitrate"))
        val h264 = codec.contains("h264", true) || codec.contains("avc", true)
        val mp4 = format.contains("mp4", true) || url.contains("mime_type=video_mp4", true)
        return DouyinStream(
            url = url,
            definition = definition,
            width = width,
            height = height,
            bitrate = bitrate,
            sizeBytes = meta.optLong("size"),
            codec = codec,
            isH264Mp4 = h264 && mp4
        )
    }

    private fun extractAssignedJsonObject(html: String, variable: String): JSONObject? {
        val assignment = Regex("${Regex.escape(variable)}\\s*=", RegexOption.IGNORE_CASE).find(html) ?: return null
        val start = html.indexOf('{', assignment.range.last + 1)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val character = html[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                continue
            }
            when (character) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return runCatching { JSONObject(html.substring(start, index + 1)) }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private fun JSONObject.objectAt(vararg keys: String): JSONObject? {
        var current: Any? = this
        keys.forEach { key -> current = (current as? JSONObject)?.opt(key) }
        return current as? JSONObject
    }

    private fun jsonObjects(value: Any?): List<JSONObject> = when (value) {
        is JSONArray -> value.objects()
        is JSONObject -> value.keys().asSequence().mapNotNull(value::optJSONObject).toList()
        else -> emptyList()
    }

    private data class DouyinPage(
        val html: String,
        val finalUrl: String,
        val statusCode: Int,
        val successful: Boolean
    )

    private data class DouyinStream(
        val url: String,
        val definition: String,
        val width: Int,
        val height: Int,
        val bitrate: Long,
        val sizeBytes: Long,
        val codec: String,
        val isH264Mp4: Boolean
    ) {
        val pixels: Long get() = width.toLong() * height.toLong()
    }

    private fun analyzeWithYtDlp(link: ClassifiedLink): DesktopMediaPreview {
        require(tools.ready) { "媒体核心不完整，请重新安装解析。" }
        val args = mutableListOf(
            "--no-playlist",
            "--dump-single-json",
            "--no-warnings",
            "--socket-timeout", "15",
            "--extractor-retries", "1",
            "--retries", "1",
            "--ffmpeg-location", tools.ffmpegDirectory.absolutePath,
            "--js-runtimes", "deno:${tools.deno.absolutePath}"
        )
        if (tools.pluginDirectory.isDirectory) args += listOf("--plugin-dirs", tools.pluginDirectory.absolutePath)
        when (link.platform) {
            LinkPlatform.BILIBILI -> args += listOf("--add-header", "Referer:https://www.bilibili.com/", "--add-header", "User-Agent:$DESKTOP_USER_AGENT")
            LinkPlatform.DOUYIN -> args += listOf("--add-header", "Referer:https://www.douyin.com/", "--add-header", "User-Agent:$DESKTOP_USER_AGENT")
            LinkPlatform.X -> args += listOf("--extractor-args", "twitter:api=syndication", "--force-ipv4")
            else -> Unit
        }
        args += link.originalUrl
        val json = JSONObject(runAndCollect(tools.ytDlp, args, 90))
        val detailed = json.optJSONArray("formats")?.objects().orEmpty()
            .filter { it.optInt("height") > 0 && !it.optString("vcodec").equals("none", true) }
            .sortedWith(compareByDescending<JSONObject> { it.optInt("height") }.thenByDescending { it.optDouble("fps") })
            .distinctBy { "${it.optInt("height")}-${it.optInt("fps")}-${it.optString("vcodec")}-${it.optString("ext")}" }
            .take(24)
            .mapNotNull { format ->
                val id = format.optString("format_id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val height = format.optInt("height")
                val fps = format.optDouble("fps", 0.0).roundToInt()
                val size = maxOf(format.optLong("filesize"), format.optLong("filesize_approx"))
                val parts = buildList {
                    add("${height}p")
                    if (fps > 30) add("${fps}fps")
                    format.optString("vcodec").substringBefore('.').takeIf(String::isNotBlank)?.let { add(it.uppercase()) }
                    format.optString("ext").takeIf(String::isNotBlank)?.let { add(it.uppercase()) }
                    if (size > 0) add(formatBytes(size))
                }
                val selector = if (format.optString("acodec").equals("none", true)) "$id+bestaudio/best" else id
                MediaFormatChoice(selector, parts.joinToString(" · "), size)
            }
        return DesktopMediaPreview(
            sourceUrl = link.originalUrl,
            title = json.optString("title", json.optString("fulltitle", "未命名视频")),
            uploader = json.optString("uploader"),
            platform = link.platform.label,
            durationSeconds = json.optDouble("duration", 0.0).roundToInt(),
            thumbnailUrl = json.optString("thumbnail"),
            formats = (defaultFormats() + detailed).distinctBy { it.selector },
            engine = "yt-dlp 通用引擎",
            warning = link.warning
        )
    }

    private fun fetchJson(url: String, timeoutMs: Int = 8_000, headers: Map<String, String> = emptyMap()): JSONObject {
        val client = http.newBuilder().callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS).build()
        val builder = Request.Builder().url(url).header("User-Agent", DESKTOP_USER_AGENT).header("Accept", "application/json")
        headers.forEach(builder::header)
        return client.newCall(builder.build()).execute().use { response ->
            require(response.isSuccessful) { "网络请求失败（${response.code}）。" }
            JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun runAndCollect(executable: File, arguments: List<String>, timeoutSeconds: Long): String {
        val process = processBuilder(executable, arguments).start()
        val reader = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "jiexi-tool-output").apply { isDaemon = true } }
        val outputFuture = reader.submit<String> { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                error("组件响应超时，请检查网络后重试。")
            }
            val output = outputFuture.get(5, TimeUnit.SECONDS).trim()
            if (process.exitValue() != 0) error(cleanToolError(output))
            return output
        } finally {
            reader.shutdownNow()
        }
    }

    private fun processBuilder(executable: File, arguments: List<String>): ProcessBuilder {
        val builder = ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .directory(executable.parentFile)
            .redirectErrorStream(true)
        val path = listOf(tools.root.absolutePath, tools.ffmpegDirectory.absolutePath, builder.environment()["PATH"].orEmpty())
            .filter(String::isNotBlank).joinToString(File.pathSeparator)
        builder.environment()["PATH"] = path
        builder.environment()["PYTHONUTF8"] = "1"
        builder.environment()["PYTHONIOENCODING"] = "utf-8"
        builder.environment()["NO_COLOR"] = "1"
        return builder
    }

    private fun findObject(value: Any?, key: String): JSONObject? = when (value) {
        is JSONObject -> {
            value.optJSONObject(key) ?: value.keys().asSequence().mapNotNull { child -> findObject(value.opt(child), key) }.firstOrNull()
        }
        is JSONArray -> (0 until value.length()).asSequence().mapNotNull { findObject(value.opt(it), key) }.firstOrNull()
        else -> null
    }

    companion object {
        private const val FXTWITTER_API_BASE_URL = "https://api.fxtwitter.com"
        private const val DOUYIN_JINGXUAN_BASE_URL = "https://jingxuan.douyin.com"
        private const val BILIBILI_API_BASE_URL = "https://api.bilibili.com"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

        fun defaultFormats(direct: Boolean = false): List<MediaFormatChoice> = if (direct) {
            listOf(
                MediaFormatChoice("best", "原始文件 · 无转码"),
                MediaFormatChoice("bestaudio/best", "仅音频 MP3")
            )
        } else {
            listOf(
                MediaFormatChoice("bestvideo*+bestaudio/best", "公开最高画质（源站提供时可到 8K）"),
                MediaFormatChoice("bestvideo*[height<=2160]+bestaudio/best[height<=2160]", "最高 4K"),
                MediaFormatChoice("bestvideo*[height<=1080]+bestaudio/best[height<=1080]", "最高 1080p"),
                MediaFormatChoice("bestvideo*[height<=720]+bestaudio/best[height<=720]", "最高 720p"),
                MediaFormatChoice("bestaudio/best", "仅音频 MP3")
            )
        }

        fun sanitizeFileName(value: String): String = value
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().trimEnd('.')

        internal fun isLoopbackMediaUrl(url: String): Boolean {
            val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
            return host.equals("localhost", true) || host == "::1" || host.startsWith("127.")
        }

        internal fun extractDouyinVideoId(vararg urls: String): String? {
            val preferred = listOf(
                Regex("/(?:video|note)/(\\d{15,25})(?:[/?#]|$)", RegexOption.IGNORE_CASE),
                Regex("(?:aweme_id|item_id|modal_id)=(\\d{15,25})(?:[&#]|$)", RegexOption.IGNORE_CASE)
            )
            urls.asSequence().filter(String::isNotBlank).forEach { url ->
                preferred.forEach { pattern ->
                    pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
                }
            }
            return urls.asSequence().filter(String::isNotBlank)
                .mapNotNull { Regex("(?<!\\d)(\\d{15,25})(?!\\d)").find(it)?.groupValues?.getOrNull(1) }
                .firstOrNull()
        }

        fun cleanToolError(output: String): String {
            val text = output.lineSequence().filter(String::isNotBlank).toList().takeLast(20).joinToString("\n")
            return when {
                Regex("(?:HTTP Error 404|HTTP[^\\n]*\\b404\\b|status code 404)", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "链接指向的资源不存在或已经失效（HTTP 404）。"
                Regex("(?:HTTP Error 429|HTTP[^\\n]*\\b429\\b|too many requests|rate.?limit)", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "源站请求过于频繁，请稍后再试。"
                Regex("(?:HTTP Error 403|HTTP[^\\n]*\\b403\\b|forbidden)", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "源站拒绝了本次访问（HTTP 403），链接可能已过期或需要源站授权。"
                Regex("getaddrinfo failed|name or service not known|temporary failure in name resolution|no address associated with hostname|nodename nor servname", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "无法解析源站域名，请检查网络或 DNS 设置后重试。"
                Regex("timed? out|timeout|connection reset|connection refused|failed to connect|network is unreachable", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "连接源站失败或响应超时，请检查网络后重试。"
                Regex("certificate|SSL|TLS", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "安全连接校验失败，请检查系统时间、代理或证书设置。"
                Regex("\\bDRM\\b|\\bencrypted\\b|\\bKID\\b|\\bKEY\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "检测到 DRM 或加密媒体流，无法导出。"
                Regex("login|cookie|sign in|会员|登录", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "源站没有向匿名访问提供该资源。"
                Regex("unsupported|no video formats|requested format is not available", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                    "当前链接没有找到可下载的开放格式，请更新媒体核心后重试。"
                text.isBlank() -> "媒体组件执行失败。"
                else -> text.takeLast(1800)
            }
        }

        private fun formatBytes(bytes: Long): String = when {
            bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1073741824.0)
            bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / 1048576.0)
            else -> "%.0f KB".format(bytes / 1024.0)
        }

        private fun JSONArray.objects(): List<JSONObject> =
            (0 until length()).mapNotNull(::optJSONObject)

        private fun JSONArray.strings(): List<String> =
            (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    }
}
