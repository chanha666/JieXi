package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelsTest {
    @Test
    fun newTaskHasSafeDefaults() {
        val task = DownloadTask(url = "https://example.com/video")
        assertEquals(TaskStatus.QUEUED, task.status)
        assertEquals("mp4", task.outputFormat)
        assertFalse(task.embedSubtitles)
    }

    @Test
    fun settingsDefaultToAnonymousBestQuality() {
        val settings = AppSettings()
        assertEquals("best", settings.defaultQuality)
        assertEquals(3, settings.retries)
        assertFalse(settings.wifiOnly)
    }
}
