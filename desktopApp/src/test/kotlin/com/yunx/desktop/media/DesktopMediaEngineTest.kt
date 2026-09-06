package com.yunx.desktop.media

import com.jiexi.core.link.ClassifiedLink
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.LinkPlatform
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopMediaEngineTest {
    @Test
    fun `explicit media engine property is the first complete candidate`() = withTemporaryDirectory { directory ->
        val configured = directory.resolve("configured").toFile()
        val fallback = directory.resolve("workspace/tools/media-engine").toFile()
        createCompleteToolRoot(configured)
        createCompleteToolRoot(fallback)

        val located = DesktopMediaEngine.ToolPaths.locateFrom(
            propertyRoot = configured.absolutePath,
            environmentRoot = fallback.absolutePath,
            workingDirectory = directory.resolve("workspace").toFile()
        )

        assertEquals(configured.canonicalFile, located.root.canonicalFile)
        assertTrue(located.ready)
    }

    @Test
    fun `tool discovery skips an incomplete earlier candidate`() = withTemporaryDirectory { directory ->
        val incomplete = directory.resolve("incomplete").toFile().apply { mkdirs() }
        File(incomplete, "yt-dlp.exe").writeText("present but incomplete")
        val complete = directory.resolve("workspace/tools/media-engine").toFile()
        createCompleteToolRoot(complete)

        val located = DesktopMediaEngine.ToolPaths.locateFrom(
            propertyRoot = incomplete.absolutePath,
            workingDirectory = directory.resolve("workspace").toFile()
        )

        assertEquals(complete.canonicalFile, located.root.canonicalFile)
        assertTrue(located.ready)
    }

    @Test
    fun `tool discovery supports running from the desktop module directory`() = withTemporaryDirectory { directory ->
        val workspace = directory.resolve("workspace")
        val complete = workspace.resolve("tools/media-engine").toFile()
        createCompleteToolRoot(complete)

        val located = DesktopMediaEngine.ToolPaths.locateFrom(
            workingDirectory = workspace.resolve("desktopApp").toFile()
        )

        assertEquals(complete.canonicalFile, located.root.canonicalFile)
        assertTrue(located.ready)
    }

    @Test
    fun `direct media analysis is instant and preserves the signed url`() {
        val source = "https://cdn.example.com/media/My%20Video.mp4?token=a%2Fb&expires=999"

        val preview = DesktopMediaEngine().analyze(source)

        assertEquals(source, preview.sourceUrl)
        assertEquals(source, preview.downloadUrl)
        assertEquals("My Video", preview.title)
        assertEquals("媒体直链", preview.platform)
        assertEquals("直链即时识别", preview.engine)
        assertEquals("best", preview.formats.first().selector)
    }

    @Test
    fun `X analysis keeps the direct media url returned by FxTwitter`() {
        val server = MockWebServer()
        val mediaUrl = "https://video.twimg.com/ext_tw_video/123456789/pu/vid/1280x720/sample.mp4?tag=12"
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """
                {
                  "code": 200,
                  "tweet": {
                    "text": "公开测试视频",
                    "author": {"name": "测试作者"},
                    "media": {"videos": [{
                      "url": "$mediaUrl",
                      "height": 720,
                      "duration": 12.6,
                      "thumbnail_url": "https://pbs.twimg.com/thumb.jpg"
                    }]}
                  }
                }
                """.trimIndent()
            )
        )
        server.start()
        try {
            val engine = DesktopMediaEngine(xApiBaseUrl = server.url("/").toString())

            val preview = engine.analyze("https://x.com/test/status/123456789")

            assertEquals(mediaUrl, preview.downloadUrl)
            assertFalse(preview.downloadUrl.contains("video.fxtwitter.com"))
            assertEquals("测试作者 - 公开测试视频", preview.title)
            assertEquals(13, preview.durationSeconds)
            assertEquals("/i/status/123456789", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `Bilibili analysis returns and refreshes the official anonymous direct url`() {
        val server = MockWebServer()
        server.start()
        val originalUrl = "https://www.bilibili.com/video/BV1GJ411x7h7"
        val previewUrl = "https://upos-sz.example.bilivideo.com/video.mp4?token=preview"
        val refreshedUrl = "https://upos-sz.example.bilivideo.com/video.mp4?token=refreshed"
        try {
            server.enqueue(MockResponse().setBody(bilibiliViewJson().toString()))
            server.enqueue(MockResponse().setBody(bilibiliPlayJson(previewUrl).toString()))
            val engine = DesktopMediaEngine(bilibiliApiBaseUrl = server.url("/").toString())

            val preview = engine.analyze(originalUrl)

            assertEquals(previewUrl, preview.downloadUrl)
            assertEquals("哔哩哔哩匿名直连", preview.engine)
            assertTrue(preview.formats.single().label.contains("720P"))

            server.enqueue(MockResponse().setBody(bilibiliViewJson().toString()))
            server.enqueue(MockResponse().setBody(bilibiliPlayJson(refreshedUrl).toString()))
            val task = DesktopMediaTask(
                sourceUrl = originalUrl,
                downloadUrl = preview.downloadUrl,
                title = preview.title,
                platform = preview.platform,
                formatSelector = preview.formats.single().selector,
                formatLabel = preview.formats.single().label,
                outputFormat = "mp4",
                embedSubtitles = false
            )

            assertEquals(refreshedUrl, engine.resolveFreshDownloadUrl(task))

            server.enqueue(MockResponse().setResponseCode(503))
            assertEquals(previewUrl, engine.resolveFreshDownloadUrl(task))
            assertEquals(5, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `Douyin fallback follows the resolved id and selects the best H264 MP4`() {
        val server = MockWebServer()
        server.start()
        val videoId = "7646342441319408036"
        val selectedUrl = server.url("/media/1080-h264-high.mp4?mime_type=video_mp4&token=signed").toString()
        try {
            server.enqueue(
                MockResponse().setResponseCode(302)
                    .setHeader("Location", server.url("/redirected/video/$videoId"))
            )
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody("<html><body>legacy page without public video data</body></html>")
            )
            val model = JSONObject()
                .put("video_duration", 356.38)
                .put(
                    "video_list",
                    JSONArray()
                        .put(douyinStream(server.url("/media/2160-h265.mp4").toString(), "2160p", 2160, 3840, 9_000_000, "h265", "mp4"))
                        .put(douyinStream(server.url("/media/1080-h264-low.mp4").toString(), "1080p", 1080, 1920, 3_000_000, "h264", "mp4"))
                        .put(douyinStream(selectedUrl, "1080p", 1080, 1920, 3_500_000, "h264", "mp4", 148_915_518))
                        .put(douyinStream(server.url("/media/1440-h264.webm").toString(), "1440p", 1440, 2560, 8_000_000, "h264", "webm"))
                )
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(jingxuanHtml(model, modelAsString = true))
            )
            val source = server.url("/share/without-id").toString()
            val engine = DesktopMediaEngine(
                douyinJingxuanBaseUrl = server.url("/official").toString()
            )

            val preview = engine.analyzeDouyin(douyinLink(source))

            assertEquals(selectedUrl, preview.downloadUrl)
            assertEquals("#公开测试 {视频}", preview.title)
            assertEquals("测试作者", preview.uploader)
            assertEquals(356, preview.durationSeconds)
            assertEquals("https://img.example.com/cover.jpg", preview.thumbnailUrl)
            assertEquals("抖音精选公开页", preview.engine)
            assertTrue(preview.formats.first().label.contains("1080p"))
            assertTrue(preview.formats.first().label.contains("H264"))
            assertEquals(148_915_518, preview.formats.first().estimatedBytes)

            val initialRequest = server.takeRequest(2, TimeUnit.SECONDS)
            val redirectedRequest = server.takeRequest(2, TimeUnit.SECONDS)
            val officialRequest = server.takeRequest(2, TimeUnit.SECONDS)
            assertEquals("/share/without-id", initialRequest?.path)
            assertEquals("/redirected/video/$videoId", redirectedRequest?.path)
            assertEquals("/official/m/video/$videoId", officialRequest?.path)
            assertTrue(officialRequest?.getHeader("User-Agent").orEmpty().contains("Android"))
            assertEquals("document", officialRequest?.getHeader("Sec-Fetch-Dest"))
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `Douyin fallback chooses the highest available stream when H264 MP4 is absent`() {
        val server = MockWebServer()
        server.start()
        val videoId = "7646342441319408036"
        val selectedUrl = server.url("/media/2160-h265.mp4?mime_type=video_mp4").toString()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>no legacy data</html>"))
            val model = JSONObject()
                .put("video_duration", 42.6)
                .put(
                    "video_list",
                    JSONArray()
                        .put(douyinStream(server.url("/media/1080-vp9.webm").toString(), "1080p", 1920, 1080, 6_000_000, "vp9", "webm"))
                        .put(douyinStream(selectedUrl, "2160p", 3840, 2160, 4_000_000, "h265", "mp4"))
                )
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(jingxuanHtml(model, modelAsString = false))
            )
            val source = server.url("/video/$videoId").toString()
            val engine = DesktopMediaEngine(
                douyinJingxuanBaseUrl = server.url("/official").toString()
            )

            val preview = engine.analyzeDouyin(douyinLink(source))

            assertEquals(selectedUrl, preview.downloadUrl)
            assertTrue(preview.formats.first().label.contains("2160p"))
            assertEquals(43, preview.durationSeconds)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `Douyin id extraction ignores unrelated short numbers`() {
        assertEquals(
            "7646342441319408036",
            DesktopMediaEngine.extractDouyinVideoId(
                "https://v.douyin.com/abc2026/",
                "https://www.douyin.com/video/7646342441319408036?previous_page=1"
            )
        )
        assertEquals(
            "7646342441319408036",
            DesktopMediaEngine.extractDouyinVideoId("https://www.douyin.com/?modal_id=7646342441319408036")
        )
    }

    @Test
    fun `Douyin fallback reports incomplete official data without leaking raw markup`() {
        val server = MockWebServer()
        server.start()
        val videoId = "7646342441319408036"
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>no legacy data</html>"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("<html>missing SSR assignment</html>"))
            val source = server.url("/video/$videoId").toString()
            val engine = DesktopMediaEngine(
                douyinJingxuanBaseUrl = server.url("/official").toString()
            )

            val failure = assertFailsWith<IllegalStateException> {
                engine.analyzeDouyin(douyinLink(source))
            }

            assertEquals("抖音精选公开页没有返回视频资料。", failure.message)
            assertFalse(failure.message.orEmpty().contains("<html>"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `tool errors are reduced to actionable messages`() {
        assertEquals(
            "链接指向的资源不存在或已经失效（HTTP 404）。",
            DesktopMediaEngine.cleanToolError("ERROR: unable to download video data: HTTP Error 404: Not Found")
        )
        assertEquals(
            "无法解析源站域名，请检查网络或 DNS 设置后重试。",
            DesktopMediaEngine.cleanToolError("ERROR: [Errno 11001] getaddrinfo failed")
        )
        assertEquals(
            "连接源站失败或响应超时，请检查网络后重试。",
            DesktopMediaEngine.cleanToolError("ERROR: The read operation timed out")
        )
        assertEquals(
            "检测到 DRM 或加密媒体流，无法导出。",
            DesktopMediaEngine.cleanToolError("ERROR: This video is DRM protected")
        )
        assertEquals(
            "源站没有向匿名访问提供该资源。",
            DesktopMediaEngine.cleanToolError("ERROR: Sign in to confirm you are not a bot")
        )
        assertNotEquals(
            "检测到 DRM 或加密媒体流，无法导出。",
            DesktopMediaEngine.cleanToolError("ERROR: monkey service returned an unknown failure")
        )
    }

    @Test
    fun `bundled yt-dlp downloads a direct media url without changing its bytes`() = withTemporaryDirectory { directory ->
        assumeTrue("Windows-only bundled executable test", System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val engine = DesktopMediaEngine()
        assumeTrue("Bundled media engine is required", engine.tools.ready)
        val content = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", content.size)
                    .setBody(Buffer().write(content))
            }
            start()
        }
        var process: Process? = null
        try {
            val source = server.url("/direct-test.mp4").newBuilder().host("127.0.0.1").build().toString()
            assertTrue(DesktopMediaEngine.isLoopbackMediaUrl(source), source)
            val preview = engine.analyze(source)
            val task = DesktopMediaTask(
                sourceUrl = preview.sourceUrl,
                downloadUrl = preview.downloadUrl,
                title = "公开视频 #测试 100% [作品]",
                platform = preview.platform,
                formatSelector = "best",
                formatLabel = "原始文件",
                outputFormat = "mp4",
                embedSubtitles = false
            )

            val workspace = directory.resolve("中文 下载目录").toFile()
            process = engine.startDownload(task, workspace, concurrentFragments = 64)
            val output = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "yt-dlp did not finish in time")
            assertEquals(0, process.exitValue(), output.get(5, TimeUnit.SECONDS))
            val downloaded = workspace.listFiles().orEmpty().single { it.extension.equals("mp4", true) }
            assertContentEquals(content, downloaded.readBytes())
            val reported = output.get(5, TimeUnit.SECONDS).lineSequence()
                .first { it.startsWith("[finished]") }.removePrefix("[finished]").trim()
            assertEquals(downloaded.absolutePath, reported)
            assertEquals(downloaded.canonicalFile, DesktopMediaWorkspace.requireOwnedOutput(workspace, File(reported)).canonicalFile)
            assertFalse(directory.toFile().walk().any { it.name.endsWith(".part") })
        } finally {
            process?.takeIf(Process::isAlive)?.destroyForcibly()
            server.shutdown()
        }
    }

    @Test
    fun `bundled media toolchain reports versions and produces a probed mp4`() = withTemporaryDirectory { directory ->
        assumeTrue("Windows-only bundled executable test", System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val engine = DesktopMediaEngine()
        assumeTrue("Bundled media engine is required", engine.tools.ready)

        val status = engine.coreStatus()
        assertTrue(status.ready, status.detail)
        assertTrue(status.ytDlpVersion.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}.*")), status.ytDlpVersion)
        assertNotEquals("未知", status.ffmpegVersion)
        assertNotEquals("未知", status.denoVersion)

        val output = directory.resolve("toolchain-smoke.mp4").toFile()
        engine.runFfmpeg(
            listOf(
                "-f", "lavfi", "-i", "color=c=black:s=64x64:r=24:d=0.25",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=0.25",
                "-shortest", "-c:v", "mpeg4", "-c:a", "aac", output.absolutePath
            ),
            timeoutSeconds = 30
        )
        assertTrue(output.isFile && output.length() > 0L)

        val probe = JSONObject(
            engine.runFfprobe(
                listOf("-v", "quiet", "-print_format", "json", "-show_streams", output.absolutePath),
                timeoutSeconds = 30
            )
        )
        val streams = probe.getJSONArray("streams")
        val types = (0 until streams.length()).map { streams.getJSONObject(it).getString("codec_type") }.toSet()
        assertEquals(setOf("video", "audio"), types)
    }

    private fun createCompleteToolRoot(root: File) {
        listOf(
            File(root, "yt-dlp.exe"),
            File(root, "deno.exe"),
            File(root, "ffmpeg/bin/ffmpeg.exe"),
            File(root, "ffmpeg/bin/ffprobe.exe")
        ).forEach { file ->
            file.parentFile.mkdirs()
            file.writeText("test")
        }
    }

    private fun bilibiliViewJson() = JSONObject()
        .put("code", 0)
        .put(
            "data",
            JSONObject()
                .put("bvid", "BV1GJ411x7h7")
                .put("cid", 1001)
                .put("title", "公开测试视频")
                .put("duration", 212)
                .put("pic", "https://i.example/cover.jpg")
                .put("owner", JSONObject().put("name", "测试作者"))
                .put(
                    "pages",
                    JSONArray().put(
                        JSONObject()
                            .put("page", 1)
                            .put("cid", 1001)
                            .put("part", "公开测试视频")
                            .put("duration", 212)
                    )
                )
        )

    private fun bilibiliPlayJson(url: String) = JSONObject()
        .put("code", 0)
        .put(
            "data",
            JSONObject()
                .put("quality", 64)
                .put("format", "mp4720")
                .put(
                    "durl",
                    JSONArray().put(
                        JSONObject()
                            .put("url", url)
                            .put("size", 51_973_319L)
                    )
                )
        )

    private fun douyinLink(source: String) = ClassifiedLink(
        originalUrl = source,
        normalizedUrl = source,
        kind = LinkKind.VIDEO_PAGE,
        platform = LinkPlatform.DOUYIN,
        confidence = 96,
        mayRequireLogin = false
    )

    private fun douyinStream(
        url: String,
        definition: String,
        width: Int,
        height: Int,
        bitrate: Long,
        codec: String,
        type: String,
        size: Long = 1_000_000
    ) = JSONObject()
        .put("main_url", url)
        .put(
            "video_meta",
            JSONObject()
                .put("definition", definition)
                .put("vwidth", width)
                .put("vheight", height)
                .put("bitrate", bitrate)
                .put("real_bitrate", bitrate)
                .put("codec_type", codec)
                .put("vtype", type)
                .put("size", size)
        )

    private fun jingxuanHtml(model: JSONObject, modelAsString: Boolean): String {
        val result = JSONObject()
            .put("title", "测试")
            .put("abstract", "#公开测试 {视频}")
            .put("cover_image_url", "https://img.example.com/cover.jpg")
            .put("media_user", JSONObject().put("screen_name", "测试作者"))
            .put("video_model", if (modelAsString) model.toString() else model)
        val root = JSONObject().put(
            "data",
            JSONObject().put(
                "storeState",
                JSONObject().put(
                    "detail",
                    JSONObject().put(
                        "videoData",
                        JSONObject().put("result", result)
                    )
                )
            )
        )
        return "<html><script>window._SSR_DATA = $root;</script><body>ok</body></html>"
    }

    private inline fun withTemporaryDirectory(block: (java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("desktop-media-engine-test-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
