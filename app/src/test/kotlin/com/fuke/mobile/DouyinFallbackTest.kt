package com.fuke.mobile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinFallbackTest {
    @Test
    fun `featured page prefers highest h264 rendition for broad playback compatibility`() {
        val html = featuredHtml(
            rendition("https://cdn.example/4k-h265.mp4", "2160p", 2160, 8_000_000, "h265", 9000),
            rendition("https://cdn.example/720-h264.mp4", "720p", 720, 6_000_000, "h264", 2000),
            rendition("https://cdn.example/1080-h264.mp4", "1080p", 1080, 4_000_000, "h264", 5000)
        )

        val result = DouyinFallback.parseFeaturedPage("https://www.douyin.com/video/7646342441319408036", html)

        assertEquals("https://cdn.example/1080-h264.mp4", result.downloadUrl)
        assertEquals("抖音", result.platform)
        assertEquals("公开测试视频", result.title)
        assertEquals("测试作者", result.uploader)
        assertEquals(12, result.durationSeconds)
        assertEquals(5000L, result.formats.first().bytes)
        assertTrue(result.formats.first().label.contains("1080p"))
        assertTrue(result.formats.first().label.contains("H264"))
    }

    @Test
    fun `featured page falls back to highest rendition when h264 is absent`() {
        val html = featuredHtml(
            rendition("https://cdn.example/720-h265.mp4", "720p", 720, 5_000_000, "h265", 2000),
            rendition("https://cdn.example/4k-h265.mp4", "2160p", 2160, 3_000_000, "h265", 8000)
        )

        val result = DouyinFallback.parseFeaturedPage("https://www.douyin.com/video/7646342441319408036", html)

        assertEquals("https://cdn.example/4k-h265.mp4", result.downloadUrl)
        assertTrue(result.formats.first().label.contains("2160p"))
    }

    @Test
    fun `video id extraction supports direct modal and featured urls`() {
        assertEquals("7646342441319408036", DouyinFallback.extractVideoId("https://www.douyin.com/video/7646342441319408036"))
        assertEquals("7646342441319408036", DouyinFallback.extractVideoId("https://www.douyin.com/?modal_id=7646342441319408036"))
        assertEquals("7646342441319408036", DouyinFallback.extractVideoId("https://jingxuan.douyin.com/m/video/7646342441319408036"))
    }

    private fun rendition(
        url: String,
        definition: String,
        height: Int,
        bitrate: Long,
        codec: String,
        size: Long
    ) = JsonObject().apply {
        addProperty("main_url", url)
        add("video_meta", JsonObject().apply {
            addProperty("definition", definition)
            addProperty("vheight", height)
            addProperty("vwidth", height * 9 / 16)
            addProperty("bitrate", bitrate)
            addProperty("codec_type", codec)
            addProperty("size", size)
        })
    }

    private fun featuredHtml(vararg renditions: JsonObject): String {
        val model = JsonObject().apply {
            addProperty("video_duration", 12.8)
            add("video_list", JsonArray().apply { renditions.forEach(::add) })
        }
        val result = JsonObject().apply {
            addProperty("title", "公开测试视频")
            addProperty("abstract", "公开测试摘要")
            addProperty("cover_image_url", "https://cdn.example/cover.jpg")
            add("media_user", JsonObject().apply { addProperty("screen_name", "测试作者") })
            addProperty("video_model", model.toString())
        }
        val root = JsonObject().apply {
            add("data", JsonObject().apply {
                add("storeState", JsonObject().apply {
                    add("detail", JsonObject().apply {
                        add("videoData", JsonObject().apply { add("result", result) })
                    })
                })
            })
        }
        return "<html><script>window._SSR_DATA = $root</script></html>"
    }
}
