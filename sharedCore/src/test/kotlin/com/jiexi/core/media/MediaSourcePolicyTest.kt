package com.jiexi.core.media

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Test
import kotlin.test.*

class MediaSourcePolicyTest {
    @Test fun failedRefreshDoesNotReuseExpiredUrl() {
        for (platform in listOf("抖音", "X", "X / Twitter", "哔哩哔哩")) {
            assertEquals("", MediaSourcePolicy.resolve(platform, "https://cdn.example/expired") { throw IOException("503") })
            assertEquals("", MediaSourcePolicy.resolve(platform, "https://cdn.example/expired") { "" })
            assertEquals("", MediaSourcePolicy.resolve(platform, "https://cdn.example/expired") { "file:///private" })
        }
    }
    @Test fun freshAndNonExpiringSourcesArePreserved() {
        assertEquals("https://cdn.example/new", MediaSourcePolicy.resolve("抖音", "old") { "https://cdn.example/new" })
        assertEquals("https://cdn.example/a.mp4", MediaSourcePolicy.resolve("直链视频", "https://cdn.example/a.mp4") { error("must not refresh") })
    }
    @Test fun cancellationIsNotSwallowed() {
        assertFailsWith<CancellationException> { MediaSourcePolicy.resolve("X", "") { throw CancellationException() } }
        assertFailsWith<InterruptedException> { MediaSourcePolicy.resolve("X", "") { throw InterruptedException() } }
    }
    @Test fun manifestsAndPostProcessingMustUseMediaCore() {
        for (url in listOf("https://cdn.example/a.M3U8?token=1", "https://cdn.example/a.mpd", "https://cdn.example/a.m3u")) {
            assertFalse(MediaSourcePolicy.useDirectTransfer(url, "mp4", false))
        }
        assertFalse(MediaSourcePolicy.useDirectTransfer("https://cdn.example/a.mp4", "mp3", false))
        assertFalse(MediaSourcePolicy.useDirectTransfer("https://cdn.example/a.mp4", "mp4", true))
        assertFalse(MediaSourcePolicy.useDirectTransfer("invalid", "mp4", false))
        assertTrue(MediaSourcePolicy.useDirectTransfer("https://cdn.example/play?token=x", "mp4", false))
    }
}
