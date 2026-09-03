package com.yunx.desktop.update

import org.json.JSONObject
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DesktopUpdateServiceTest {
    @Test
    fun `accepts author signed update manifest and rejects tampering`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = """{"tag_name":"v3.1.0","body":"test","assets":[{"name":"解析-3.1.0-安装版.exe","url":"https://downloads.example/app.exe","sha256":"${"a".repeat(64)}"}]}""".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(payload); sign() }
        val envelope = JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)

        val release = DesktopUpdateService().parseSignedEnvelope(envelope, key)
        assertEquals("v3.1.0", release.version)
        assertEquals("解析-3.1.0-安装版.exe", DesktopUpdateService.windowsInstaller(release)?.name)

        envelope.put("payload", Base64.getEncoder().encodeToString("{}".toByteArray()))
        assertFails { DesktopUpdateService().parseSignedEnvelope(envelope, key) }
    }

    @Test
    fun `compares semantic version numbers numerically`() {
        assertEquals(1, DesktopUpdateService.compareVersions("v3.1.10", "3.1.0").coerceIn(-1, 1))
        assertEquals(0, DesktopUpdateService.compareVersions("3.1.0", "v3.1.0"))
    }
}
