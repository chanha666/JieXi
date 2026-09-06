package com.yunx.desktop.security

import kotlin.test.*
import java.util.Base64

class DesktopAuthBackupTest {
    @Test fun roundTripPreservesEveryCredentialAndUsesAndroidEnvelope() {
        val data = CredentialKey.entries.associateWith { "test-" + it.name }
        val encoded = DesktopAuthBackup.encode(data, "test-pass-123")
        assertEquals("YUNX_AUTH_V2", String(Base64.getDecoder().decode(encoded).copyOfRange(0,12)))
        assertEquals(data, DesktopAuthBackup.decode(encoded, "test-pass-123"))
        assertFalse(encoded.contains("test-QUARK"))
    }
    @Test fun wrongPasswordAndTamperingAreRejected() {
        val encoded = DesktopAuthBackup.encode(mapOf(CredentialKey.QUARK_COOKIE to "test-cookie"), "test-pass-123")
        assertFails { DesktopAuthBackup.decode(encoded, "wrong-pass") }
        val bytes = Base64.getDecoder().decode(encoded)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertFails { DesktopAuthBackup.decode(Base64.getEncoder().encodeToString(bytes), "test-pass-123") }
    }
    @Test fun weakPasswordsEmptyAccountsAndPlainJsonAreRejected() {
        assertFails { DesktopAuthBackup.encode(mapOf(CredentialKey.QUARK_COOKIE to "x"),"short") }
        assertFails { DesktopAuthBackup.encode(emptyMap(),"test-pass-123") }
        assertFails { DesktopAuthBackup.decode("{}", "test-pass-123") }
    }
    @Test fun android139AuthorizationFieldIsRestored() {
        val plain = """{"app":"yunx_auth_backup","version":1,"accounts":[{"platform":"c139","cookie":"sample=1","authorization":"Basic test"}]}"""
        val decoded = DesktopAuthBackup.decode(DesktopAuthBackup.encrypt(plain,"test-pass-123"),"test-pass-123")
        assertEquals("sample=1; authorization=Basic test",decoded[CredentialKey.C139_COOKIE])
    }
    @Test fun duplicateOrUnknownPlatformsAreRejectedBeforeImport() {
        for(accounts in listOf("""[{"platform":"unknown"}]""", """[{"platform":"quark","cookie":"a"},{"platform":"quark","cookie":"b"}]""")) {
            val plain = """{"app":"yunx_auth_backup","version":1,"accounts":$accounts}"""
            assertFails { DesktopAuthBackup.decode(DesktopAuthBackup.encrypt(plain,"test-pass-123"),"test-pass-123") }
        }
    }
}
