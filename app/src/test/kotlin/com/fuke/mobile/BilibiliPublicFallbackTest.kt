package com.fuke.mobile

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliPublicFallbackTest {
    @Test
    fun `public combined stream selects requested page and prefers bilivideo backup`() {
        val source = "https://www.bilibili.com/video/BV1GJ411x7h7?p=2"
        val preview = BilibiliPublicFallback.createPreview(
            source,
            viewJson(),
            playJson(
                quality = 64,
                primary = "https://slow.edge.example/video.mp4?token=primary",
                backup = "https://upos-sz.example.bilivideo.com/video.mp4?token=backup",
                size = 51_973_319L
            )
        )

        assertEquals("https://upos-sz.example.bilivideo.com/video.mp4?token=backup", preview.downloadUrl)
        assertEquals("公开测试视频 · 第二段", preview.title)
        assertEquals("测试作者", preview.uploader)
        assertEquals(22, preview.durationSeconds)
        assertEquals("哔哩哔哩", preview.platform)
        assertEquals(51_973_319L, preview.formats.single().bytes)
        assertTrue(preview.formats.single().label.contains("720P"))
        assertTrue(preview.formats.single().label.contains("H264/AAC"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `segmented legacy response is never truncated to its first part`() {
        val play = playJson(64, "https://cdn.example/part-1.mp4", "", 1000)
        play.getAsJsonObject("data").getAsJsonArray("durl").add(
            JsonObject().apply {
                addProperty("url", "https://cdn.example/part-2.mp4")
                addProperty("size", 1000)
            }
        )

        BilibiliPublicFallback.createPreview("https://www.bilibili.com/video/BV1GJ411x7h7", viewJson(), play)
    }

    @Test
    fun `extracts BV AV and page identifiers without accepting unrelated digits`() {
        assertEquals(
            "BV1GJ411x7h7",
            BilibiliPublicFallback.extractIdentity("https://www.bilibili.com/video/BV1GJ411x7h7")?.display
        )
        assertEquals(
            "av80433022",
            BilibiliPublicFallback.extractIdentity("https://www.bilibili.com/video/av80433022/")?.display
        )
        assertEquals(3, BilibiliPublicFallback.extractPageNumber("https://www.bilibili.com/video/BV1GJ411x7h7?p=3"))
        assertEquals(null, BilibiliPublicFallback.extractIdentity("https://www.bilibili.com/video/1234567890"))
    }

    private fun viewJson() = JsonObject().apply {
        addProperty("code", 0)
        add("data", JsonObject().apply {
            addProperty("bvid", "BV1GJ411x7h7")
            addProperty("cid", 1001)
            addProperty("title", "公开测试视频")
            addProperty("duration", 33)
            addProperty("pic", "https://i.example/cover.jpg")
            add("owner", JsonObject().apply { addProperty("name", "测试作者") })
            add("pages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("page", 1)
                    addProperty("cid", 1001)
                    addProperty("part", "第一段")
                    addProperty("duration", 11)
                })
                add(JsonObject().apply {
                    addProperty("page", 2)
                    addProperty("cid", 1002)
                    addProperty("part", "第二段")
                    addProperty("duration", 22)
                })
            })
        })
    }

    private fun playJson(quality: Int, primary: String, backup: String, size: Long) = JsonObject().apply {
        addProperty("code", 0)
        add("data", JsonObject().apply {
            addProperty("quality", quality)
            addProperty("format", "mp4720")
            add("durl", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("url", primary)
                    addProperty("size", size)
                    add("backup_url", JsonArray().apply { if (backup.isNotBlank()) add(backup) })
                })
            })
        })
    }
}
