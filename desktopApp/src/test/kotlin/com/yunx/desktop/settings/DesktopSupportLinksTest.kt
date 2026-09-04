package com.yunx.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSupportLinksTest {
    @Test
    fun `uses author owned GitHub and QQ email destinations`() {
        assertEquals("https://github.com/chanha666/JieXi", DesktopSupportLinks.GITHUB_REPOSITORY)
        assertEquals("https://github.com/chanha666/JieXi/issues/new", DesktopSupportLinks.GITHUB_ISSUES)
        assertEquals("3316109338@qq.com", DesktopSupportLinks.FEEDBACK_EMAIL)
    }

    @Test
    fun `builds a safe mailto link with encoded subject`() {
        val uri = DesktopSupportLinks.feedbackEmailUri()

        assertEquals("mailto", uri.scheme)
        assertTrue(uri.rawSchemeSpecificPart.startsWith("3316109338@qq.com?subject="))
        assertTrue(uri.rawSchemeSpecificPart.contains("%E8%A7%A3%E6%9E%90"))
        assertFalse(uri.rawSchemeSpecificPart.contains(' '))
    }
}
