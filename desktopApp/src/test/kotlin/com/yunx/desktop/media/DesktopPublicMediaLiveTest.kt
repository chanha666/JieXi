package com.yunx.desktop.media

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Public network checks; opt-in, no user account or existing task access. */
class DesktopPublicMediaLiveTest {
    @Test fun `official Bilibili sample returns a downloadable public format`() {
        assumeTrue(System.getenv("JIEXI_PUBLIC_MEDIA_TEST") == "1")
        val preview = DesktopMediaEngine().analyze("https://www.bilibili.com/video/BV1GJ411x7h7")
        assertTrue(preview.title.isNotBlank())
        assertTrue(preview.downloadUrl.startsWith("https://"))
        println("BILIBILI LIVE ANALYSIS PASS: ${preview.title}")
    }

    @Test fun `Douyin public sample downloads and contains video and audio`() {
        assumeTrue(System.getenv("JIEXI_PUBLIC_MEDIA_TEST") == "1")
        val engine = DesktopMediaEngine()
        val preview = engine.analyze("https://www.douyin.com/video/7646342441319408036")
        assertTrue(preview.downloadUrl.startsWith("https://"))
        val root = File("D:/CodexBuilds/JieXi/media-live-qa/douyin-${System.currentTimeMillis()}").apply { mkdirs() }
        val task = DesktopMediaTask(preview.sourceUrl, preview.downloadUrl, preview.title, preview.platform, "best", "公开格式", "mp4", false)
        val process = engine.startDownload(task, root, 4, retries = 0)
        val output = CompletableFuture.supplyAsync {
            val lines = StringBuilder()
            File(root, "download.log").bufferedWriter().use { writer ->
                process.inputStream.bufferedReader().useLines { sequence -> sequence.forEach { line ->
                    lines.appendLine(line); writer.appendLine(line); writer.flush()
                } }
            }
            lines.toString()
        }
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Douyin sample transfer exceeded deadline")
            val log = output.get(5, TimeUnit.SECONDS)
            File(root, "download.log").writeText(log)
            assertEquals(0, process.exitValue(), DesktopMediaEngine.cleanToolError(log))
            assertTrue(log.contains("[progress]"))
            val file = root.listFiles().orEmpty().single { it.extension == "mp4" }
            val probe = JSONObject(engine.runFfprobe(listOf("-v", "error", "-show_streams", "-of", "json", file.absolutePath), 20))
            val streams = probe.getJSONArray("streams")
            val types = (0 until streams.length()).map { streams.getJSONObject(it).optString("codec_type") }
            assertTrue("video" in types && "audio" in types)
            println("DOUYIN LIVE DOWNLOAD PASS: ${file.length()} bytes; $types")
        } finally {
            val children = process.descendants().toList()
            if (process.isAlive) process.destroyForcibly()
            children.filter { it.isAlive }.forEach { it.destroyForcibly() }
            runCatching { File(root, "download.log").writeText(output.get(10, TimeUnit.SECONDS)) }
        }
    }
}
