package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTaskPolicyTest {
    @Test
    fun runningTaskIsRecoveredAsPausedAndValuesAreSanitized() {
        val restored = MediaTaskPolicy.restore(
            DownloadTask(
                url = "  https://example.com/video.mp4  ",
                status = TaskStatus.RUNNING,
                progress = 140,
                downloadedBytes = 20,
                totalBytes = 10
            ),
            now = 1234L
        )

        assertEquals(TaskStatus.PAUSED, restored.status)
        assertEquals(99, restored.progress)
        assertEquals(20, restored.totalBytes)
        assertEquals("https://example.com/video.mp4", restored.url)
        assertTrue(restored.error.contains("断点续传"))
    }

    @Test
    fun completedTaskAlwaysHasFullProgress() {
        val restored = MediaTaskPolicy.restore(
            DownloadTask(url = "https://example.com/video.mp4", status = TaskStatus.COMPLETED, progress = 12),
            now = 1L
        )
        assertEquals(100, restored.progress)
    }

    @Test
    fun duplicateRuleBlocksOnlyActiveTasks() {
        val url = "https://example.com/video.mp4"
        assertTrue(MediaTaskPolicy.hasActiveDuplicate(listOf(DownloadTask(url = url, status = TaskStatus.PAUSED)), " $url "))
        assertFalse(MediaTaskPolicy.hasActiveDuplicate(listOf(DownloadTask(url = url, status = TaskStatus.COMPLETED)), url))
    }

    @Test
    fun directOutputTemplateIsPlatformNeutralAndWindowsSafe() {
        assertEquals("解析视频.%(ext)s", MediaTaskPolicy.directOutputTemplate("  "))
        assertEquals("B站_测试_视频.%(ext)s", MediaTaskPolicy.directOutputTemplate("B站/测试:视频"))
    }
}
