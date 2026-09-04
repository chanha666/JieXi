package com.yunx.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateIntegrityTest {
    @Test
    fun `accepts exact hash and rejects tampered file`() {
        val file = File.createTempFile("jiexi-update-", ".apk")
        try {
            file.writeText("trusted release bytes")
            val expected = UpdateIntegrity.sha256(file)
            assertTrue(UpdateIntegrity.verify(file, expected.uppercase()))

            file.appendText("tampered")
            assertFalse(UpdateIntegrity.verify(file, expected))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects missing or malformed expected hash`() {
        assertTrue(UpdateIntegrity.normalizeExpectedSha256("A".repeat(64)) == "a".repeat(64))
        assertTrue(UpdateIntegrity.normalizeExpectedSha256("") == null)
        assertTrue(UpdateIntegrity.normalizeExpectedSha256("abc") == null)
    }

    @Test
    fun `uses standard lowercase sha256 encoding`() {
        val file = File.createTempFile("jiexi-sha-vector-", ".bin")
        try {
            file.writeText("abc")
            assertTrue(
                UpdateIntegrity.sha256(file) ==
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            )
        } finally {
            file.delete()
        }
    }
}
