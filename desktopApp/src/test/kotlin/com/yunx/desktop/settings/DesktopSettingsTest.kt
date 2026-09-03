package com.yunx.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DesktopSettingsTest {
    @Test
    fun `normalizes only complete GitHub repository urls`() {
        assertEquals("https://github.com/example/jiexi", DesktopSettings.normalizeGitHubRepository("https://github.com/example/jiexi.git/"))
        assertFails { DesktopSettings.normalizeGitHubRepository("https://example.com/not-github") }
        assertEquals("", DesktopSettings.normalizeGitHubRepository(" "))
    }
}
