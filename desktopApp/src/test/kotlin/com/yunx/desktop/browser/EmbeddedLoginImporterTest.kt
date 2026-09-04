package com.yunx.desktop.browser

import com.yunx.desktop.security.CredentialKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `logout removes only the selected owned WebView profile`() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val selected = File(root, "profile-quark_cookie").apply { resolve("Network").mkdirs() }
        selected.resolve("Network/Cookies").writeText("session")
        val other = File(root, "profile-uc_cookie").apply { mkdirs() }
        val importer = EmbeddedLoginImporter(loginRoot = root)

        assertTrue(importer.clearLoginData(CredentialKey.QUARK_COOKIE))
        assertFalse(selected.exists())
        assertTrue(other.exists())
        root.deleteRecursively()
    }
}
