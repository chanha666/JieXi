package com.yunx.desktop.core

import com.yunx.app.data.network.SharePlatform
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymousAccessPolicyTest {
    @Test
    fun `public shares can be browsed anonymously except xunlei`() {
        SharePlatform.entries.filter { it != SharePlatform.XUNLEI }.forEach {
            assertTrue(DesktopResolver.supportsAnonymousBrowse(it), it.name)
        }
        assertFalse(DesktopResolver.supportsAnonymousBrowse(SharePlatform.XUNLEI))
    }

    @Test
    fun `only direct-share providers are marked anonymous-download capable`() {
        assertTrue(DesktopResolver.supportsAnonymousDownload(SharePlatform.UC))
        assertTrue(DesktopResolver.supportsAnonymousDownload(SharePlatform.C139))
        listOf(
            SharePlatform.QUARK,
            SharePlatform.XUNLEI,
            SharePlatform.BAIDU,
            SharePlatform.PAN123
        ).forEach { assertFalse(DesktopResolver.supportsAnonymousDownload(it), it.name) }
    }
}
