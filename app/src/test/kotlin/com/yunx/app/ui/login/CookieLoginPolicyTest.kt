package com.yunx.app.ui.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieLoginPolicyTest {
    @Test
    fun `accepts complete cookie set regardless of spacing`() {
        assertTrue(CookieLoginPolicy.hasRequiredCookies("foo=1; __pus=abc;  __puus=xyz", setOf("__pus", "__puus")))
    }

    @Test
    fun `rejects partial or similarly named cookies`() {
        assertFalse(CookieLoginPolicy.hasRequiredCookies("__pus=abc", setOf("__pus", "__puus")))
        assertFalse(CookieLoginPolicy.hasRequiredCookies("prefix_BDUSS=x", setOf("BDUSS")))
    }

    @Test
    fun `allows only https provider domain and subdomains`() {
        assertTrue(CookieLoginPolicy.isAllowedNavigation("https://passport.baidu.com/v2", setOf("baidu.com")))
        assertFalse(CookieLoginPolicy.isAllowedNavigation("http://pan.baidu.com/", setOf("baidu.com")))
        assertFalse(CookieLoginPolicy.isAllowedNavigation("https://baidu.com.evil.example/", setOf("baidu.com")))
    }
}
