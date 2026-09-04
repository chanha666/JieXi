package com.yunx.app.data.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformCookieCleanerTest {
    @Test
    fun `extracts exact cookie names for targeted deletion`() {
        assertEquals(setOf("BDUSS", "STOKEN", "theme"), PlatformCookieCleaner.cookieNames("BDUSS=a; STOKEN=b; theme=light"))
    }

    @Test
    fun `deletes both host and provider domain cookies`() {
        assertEquals(setOf("pan.quark.cn", "quark.cn"), PlatformCookieCleaner.candidateDomains("pan.quark.cn"))
    }
}
