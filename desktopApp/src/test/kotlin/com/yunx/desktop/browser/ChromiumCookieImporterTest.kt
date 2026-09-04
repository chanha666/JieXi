package com.yunx.desktop.browser

import com.yunx.desktop.security.CredentialKey
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromiumCookieImporterTest {
    private val importer = ChromiumCookieImporter()

    @Test
    fun `imports only matching non-expired cookies and removes duplicate names`() {
        val future = System.currentTimeMillis() / 1000.0 + 3600
        val cookies = listOf(
            cookie("SID", "specific", ".pan.quark.cn", "/", future),
            cookie("SID", "generic", ".quark.cn", "/", future),
            cookie("TOKEN", "ok", ".quark.cn", "/drive", future),
            cookie("OLD", "expired", ".quark.cn", "/", 1.0),
            cookie("OTHER", "ignored", ".baidu.com", "/", future)
        )

        assertEquals(
            "SID=specific; TOKEN=ok",
            importer.cookieHeaderFor(CredentialKey.QUARK_COOKIE, cookies)
        )
    }

    @Test
    fun `baidu import requires BDUSS`() {
        val cookies = listOf(cookie("BAIDUID", "value", ".baidu.com", "/", 0.0))
        assertFailsWith<IllegalStateException> {
            importer.cookieHeaderFor(CredentialKey.BAIDU_COOKIE, cookies)
        }
    }

    @Test
    fun `token based providers reject cookie import`() {
        assertFailsWith<IllegalStateException> {
            importer.cookieHeaderFor(CredentialKey.PAN123_TOKEN, emptyList())
        }
    }

    @Test
    fun `logout removes all dedicated browser import profiles`() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val chrome = root.resolve("chrome/Default").apply { mkdirs() }
        chrome.resolve("Cookies").writeText("session")
        val edge = root.resolve("edge/Default").apply { mkdirs() }
        edge.resolve("Cookies").writeText("session")
        val isolated = ChromiumCookieImporter(profileRoot = root)

        assertTrue(isolated.clearLoginData())
        assertFalse(root.resolve("chrome").exists())
        assertFalse(root.resolve("edge").exists())
        root.deleteRecursively()
    }

    @Test
    fun `edge devtools smoke test when explicitly enabled`() {
        if (System.getenv("YUNX_BROWSER_SMOKE") != "1") return
        importer.openLogin(ChromiumBrowser.EDGE, "https://pan.quark.cn/")
        Thread.sleep(3500)
        assertTrue(importer.importCookieHeader(ChromiumBrowser.EDGE, CredentialKey.QUARK_COOKIE).isNotBlank())
    }

    @Test
    fun `chrome devtools smoke test when explicitly enabled`() {
        if (System.getenv("YUNX_BROWSER_SMOKE") != "1") return
        importer.openLogin(ChromiumBrowser.CHROME, "https://pan.quark.cn/")
        Thread.sleep(3500)
        assertTrue(importer.importCookieHeader(ChromiumBrowser.CHROME, CredentialKey.QUARK_COOKIE).isNotBlank())
    }

    private fun cookie(name: String, value: String, domain: String, path: String, expires: Double) =
        JSONObject()
            .put("name", name)
            .put("value", value)
            .put("domain", domain)
            .put("path", path)
            .put("expires", expires)
}
