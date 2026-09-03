package com.yunx.desktop.browser

import com.yunx.desktop.security.CredentialKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EmbeddedLoginImporterTest {
    @Test
    fun `converts WebView2 cookie export to request header`() {
        val future = System.currentTimeMillis() / 1000 + 3600
        val json = """[
            {"name":"SID","value":"one","domain":".quark.cn","path":"/","expires":$future},
            {"name":"OTHER","value":"two","domain":".example.com","path":"/","expires":0}
        ]"""
        assertEquals(
            "SID=one",
            EmbeddedLoginImporter().cookieHeaderFromJson(CredentialKey.QUARK_COOKIE, json)
        )
    }

    @Test
    fun `sensitive cookie export is overwritten and deleted`() {
        val file = kotlin.io.path.createTempFile().toFile().apply { writeText("BDUSS=secret") }
        EmbeddedLoginImporter().deleteSensitiveFile(file)
        assertFalse(file.exists())
    }

    @Test
    fun `provider allowlist is restricted`() {
        assertEquals(setOf("baidu.com"), EmbeddedLoginImporter().allowedHosts(CredentialKey.BAIDU_COOKIE))
    }
}
