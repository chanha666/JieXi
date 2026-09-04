package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectMediaTest {
    @Test
    fun recognizesDirectVideoCaseInsensitivelyAndIgnoresQuery() {
        assertTrue(DirectMedia.isVideo("https://cdn.example/video.MP4?token=abc"))
        assertTrue(DirectMedia.isVideo("https://cdn.example/path/clip.webm"))
        assertFalse(DirectMedia.isVideo("https://example.com/watch?v=123"))
        assertFalse(DirectMedia.isVideo("https://example.com/list.m3u8"))
    }

    @Test
    fun buildsImmediatePreviewWithoutNetwork() {
        val preview = DirectMedia.analyze("https://cdn.example/My%20Video.mp4")
        assertEquals("My Video", preview.title)
        assertEquals("直链视频", preview.platform)
        assertEquals(preview.url, preview.downloadUrl)
        assertEquals("best", preview.formats.first().selector)
    }
}
