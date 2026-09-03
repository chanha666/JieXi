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
        val payload = """{"tag_name":"v2.3.1","assets":[{"name":"app.apk","url":"https://downloads.example/app.apk"}]}"""
            .toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(pair.private); update(payload); sign()
        }
        val envelope = JSONObject()
            .put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature))
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)
        assertEquals("v2.3.1", UpdateChecker.parseSignedManifest(envelope, key)?.tagName)
        envelope.put("payload", Base64.getEncoder().encodeToString("{}".toByteArray()))
        assertNull(UpdateChecker.parseSignedManifest(envelope, key))
    }

    @Test
    fun `requires credential-free https asset urls`() {
        assertTrue(UpdateChecker.isTrustedHttpsUrl("https://downloads.example/app.apk"))
        assertNull(UpdateChecker.parseSignedManifest(JSONObject(), "missing"))
    }
}
