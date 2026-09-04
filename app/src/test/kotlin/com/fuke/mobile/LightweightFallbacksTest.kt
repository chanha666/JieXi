package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class LightweightFallbacksTest {
    @Test
    fun extractsCommonYoutubeUrlFormsWithoutNetwork() {
        assertEquals("dQw4w9WgXcQ", YouTubeFallback.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeFallback.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeFallback.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }
}
