package com.yunx.app.data.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class UpdateCheckerTest {
    @Test
    fun `accepts authentic manifest and rejects tampering`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = """{"tag_name":"v2.3.1","assets":[{"name":"app.apk","url":"https://downloads.example/app.apk","sha256":"${"a".repeat(64)}"}]}"""
            .toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private); update(payload); sign()
        }
        val envelope = JSONObject()
            .put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)
        val release = UpdateChecker.parseSignedManifest(envelope, key)
        assertEquals("v2.3.1", release?.tagName)
        assertEquals(1, release?.assets?.size)
        envelope.put("payload", Base64.getEncoder().encodeToString("{}".toByteArray()))
        assertNull(UpdateChecker.parseSignedManifest(envelope, key))
    }

    @Test
    fun `requires credential-free https asset urls`() {
        assertTrue(UpdateChecker.isTrustedHttpsUrl("https://downloads.example/app.apk"))
        assertNull(UpdateChecker.parseSignedManifest(JSONObject(), "missing"))
    }

    @Test
    fun `filters installable apk without a valid sha256`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = """{"tag_name":"v4.0.1","assets":[{"name":"app.apk","url":"https://downloads.example/app.apk","sha256":"missing"}]}""".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private); update(payload); sign()
        }
        val envelope = JSONObject()
            .put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)

        assertTrue(UpdateChecker.parseSignedManifest(envelope, key)?.assets.orEmpty().isEmpty())
    }
}
