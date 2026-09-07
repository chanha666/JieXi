package com.yunx.desktop.media

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

/** Opt-in real network checks. Never uses user tasks, cookies or download directories. */
class DesktopMediaLiveTest {
    @Test fun `reported youtube 4k stream actually transfers bytes`() {
        assumeTrue(System.getenv("JIEXI_MEDIA_4K_TEST") == "1")
        val engine = DesktopMediaEngine()
        val root = File("D:/CodexBuilds/JieXi/media-live-qa/4k-${System.currentTimeMillis()}").apply { mkdirs() }
        val task = DesktopMediaTask("https://www.youtube.com/watch?v=JXZ_CUfTweo", "", "YouTube 4K transfer QA", "YouTube",
            "bestvideo*[height<=2160]+bestaudio/best[height<=2160]", "最高 4K", "mp4", false)
        val process = engine.startDownload(task, root, 4, retries = 0)
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        var bytes = 0L
        try {
            val deadline = System.nanoTime() + 90_000_000_000L
            while (process.isAlive && bytes < 65_536 && System.nanoTime() < deadline) {
                bytes = root.listFiles().orEmpty().filter { it.name.endsWith(".part") || it.extension == "mp4" }.sumOf { it.length() }
                Thread.sleep(100)
            }
        } finally {
            val children = process.descendants().toList()
            if (process.isAlive) process.destroyForcibly()
            children.filter { it.isAlive }.forEach { it.destroyForcibly() }
        }
        val log = output.get(10, TimeUnit.SECONDS)
        File(root, "download-test.log").writeText(log)
        assertTrue(bytes >= 65_536, DesktopMediaEngine.cleanToolError(log))
        println("4K PARTIAL TRANSFER PASSED: $bytes bytes; deliberately stopped QA process, not a full 4K download")
    }
    @Test fun `youtube returns real metadata and available resolution`() {
        assumeTrue(System.getenv("JIEXI_MEDIA_LIVE_TEST") == "1")
        val preview = DesktopMediaEngine().analyze("https://www.youtube.com/watch?v=JXZ_CUfTweo")
        assertTrue(!preview.title.startsWith("YouTube 视频 "))
        assertTrue(preview.formats.any { it.label.contains("2160") })
        println("REAL ANALYSIS PASSED: ${preview.platform} / ${preview.title} / ${preview.formats.first().label}")
    }
    @Test fun `reported youtube sample downloads and probes as actual video`() {
        assumeTrue(System.getenv("JIEXI_MEDIA_LIVE_TEST") == "1")
        val engine = DesktopMediaEngine()
        val preview = engine.analyze("https://www.youtube.com/watch?v=JXZ_CUfTweo")
        val root = File("D:/CodexBuilds/JieXi/media-live-qa/${System.currentTimeMillis()}").apply { mkdirs() }
        val task = DesktopMediaTask(preview.sourceUrl, "", preview.title, "YouTube", "worstvideo[ext=mp4]+worstaudio/worst", "测试低流量格式", "mp4", false)
        val process = engine.startDownload(task, root, 4, retries = 0)
        try {
            val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Sample transfer exceeded deadline")
            val log = output.get(5, TimeUnit.SECONDS)
            File(root, "download-test.log").writeText(log)
            assertEquals(0, process.exitValue(), DesktopMediaEngine.cleanToolError(log))
            assertTrue(log.contains("[progress]"))
            val file = root.listFiles().orEmpty().single { it.extension == "mp4" }
            val probe = JSONObject(engine.runFfprobe(listOf("-v", "error", "-show_streams", "-of", "json", file.absolutePath), 20))
            assertTrue(probe.getJSONArray("streams").length() > 0)
            println("REAL DOWNLOAD PASSED: ${file.length()} bytes; ffprobe streams=${probe.getJSONArray("streams").length()}")
        } finally {
            val children = process.descendants().toList()
            if (process.isAlive) process.destroyForcibly()
            children.filter { it.isAlive }.forEach { it.destroyForcibly() }
        }
    }
}
