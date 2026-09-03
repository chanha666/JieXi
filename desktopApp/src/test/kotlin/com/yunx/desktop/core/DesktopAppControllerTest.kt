package com.yunx.desktop.core

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAppControllerTest {
    @Test
    fun `removes a download task from the visible list`() {
        val controller = DesktopAppController()
        val task = DesktopDownloadTask("sample.bin")
        controller.downloads += task

        controller.removeDownload(task)

        assertTrue(controller.downloads.isEmpty())
    }
}
