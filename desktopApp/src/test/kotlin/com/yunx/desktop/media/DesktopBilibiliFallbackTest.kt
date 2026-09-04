package com.yunx.desktop.media

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopBilibiliFallbackTest {
    @Test
    fun `official anonymous API returns the requested page as a direct combined stream`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(viewJson().toString()))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(playJson().toString()))
        server.start()
        try {
            val fallback = DesktopBilibiliFallback(server.url("/").toString())
            val preview = fallback.analyze("https://www.bilibili.com/video/BV1GJ411x7h7?p=2")

            assertEquals("https://upos-sz.example.bilivideo.com/video.mp4?token=backup", preview.downloadUrl)
            assertEquals("公开测试视频 · 第二段", preview.title)
            assertEquals("测试作者", preview.uploader)
            assertEquals(22, preview.durationSeconds)
            assertEquals("哔哩哔哩匿名直连", preview.engine)
            assertEquals(51_973_319L, preview.formats.single().estimatedBytes)
            assertTrue(preview.formats.single().label.contains("公开 720P"))

            val viewRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
            val playRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/x/web-interface/view?bvid=BV1GJ411x7h7", viewRequest.path)
            assertTrue(playRequest.path.orEmpty().startsWith("/x/player/playurl?"))
            assertTrue(playRequest.requestUrl!!.queryParameterNames.containsAll(setOf("bvid", "cid", "qn", "fnval")))
            assertEquals("127", playRequest.requestUrl!!.queryParameter("qn"))
            assertEquals("1", playRequest.requestUrl!!.queryParameter("fnval"))
            assertTrue(playRequest.getHeader("Referer").orEmpty().contains("BV1GJ411x7h7", ignoreCase = true))
            assertTrue(playRequest.getHeader("User-Agent").orEmpty().contains("Chrome"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `official short link is resolved without downloading the final webpage`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setResponseCode(302)
                    .setHeader("Location", server.url("/video/BV1GJ411x7h7?p=2"))
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(viewJson().toString()))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(playJson().toString()))
            val serverHost = server.url("/").host
            val fallback = DesktopBilibiliFallback(
                apiRoot = server.url("/").toString(),
                shortLinkHosts = setOf(serverHost)
            )

            val preview = fallback.analyze(server.url("/short/BiliTest").toString())

            assertEquals("公开测试视频 · 第二段", preview.title)
            assertEquals("/short/BiliTest", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS)?.path.orEmpty().startsWith("/x/web-interface/view?"))
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS)?.path.orEmpty().startsWith("/x/player/playurl?"))
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `segmented response fails instead of silently downloading an incomplete video`() {
        val play = playJson()
        play.getJSONObject("data").getJSONArray("durl").put(
            JSONObject().put("url", "https://cdn.example/part-2.mp4").put("size", 1000)
        )

        val error = assertFailsWith<IllegalArgumentException> {
            DesktopBilibiliFallback.createPreview(
                "https://www.bilibili.com/video/BV1GJ411x7h7",
                viewJson(),
                play
            )
        }

        assertTrue(error.message.orEmpty().contains("多个旧式分段"))
    }

    @Test
    fun `extracts BV AV and page identifiers`() {
        assertEquals(
            "BV1GJ411x7h7",
            DesktopBilibiliFallback.extractIdentity("https://www.bilibili.com/video/BV1GJ411x7h7")?.display
        )
        assertEquals(
            "av80433022",
            DesktopBilibiliFallback.extractIdentity("https://www.bilibili.com/video/av80433022/")?.display
        )
        assertEquals(3, DesktopBilibiliFallback.extractPageNumber("https://www.bilibili.com/video/BV1GJ411x7h7?p=3"))
    }

    private fun viewJson() = JSONObject()
        .put("code", 0)
        .put(
            "data",
            JSONObject()
                .put("bvid", "BV1GJ411x7h7")
                .put("cid", 1001)
                .put("title", "公开测试视频")
                .put("duration", 33)
                .put("pic", "https://i.example/cover.jpg")
                .put("owner", JSONObject().put("name", "测试作者"))
                .put(
                    "pages",
                    JSONArray()
                        .put(JSONObject().put("page", 1).put("cid", 1001).put("part", "第一段").put("duration", 11))
                        .put(JSONObject().put("page", 2).put("cid", 1002).put("part", "第二段").put("duration", 22))
                )
        )

    private fun playJson() = JSONObject()
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
                            .put("url", "https://slow.edge.example/video.mp4?token=primary")
                            .put("size", 51_973_319L)
                            .put(
                                "backup_url",
                                JSONArray().put("https://upos-sz.example.bilivideo.com/video.mp4?token=backup")
                            )
                    )
                )
        )
}
