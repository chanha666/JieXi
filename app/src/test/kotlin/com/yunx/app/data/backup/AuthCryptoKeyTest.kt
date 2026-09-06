package com.yunx.app.data.backup

import org.junit.Test
import org.junit.Assert.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class AuthCryptoKeyTest {
    @Test fun derivedKeyIsUsableByAesGcmAcrossJavaAndAndroidProviders() {
        val key = AuthCrypto.deriveKey("test-password",ByteArray(16) { it.toByte() },210000)
        assertEquals("AES",key.algorithm)
        assertEquals(32,key.encoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,ByteArray(12)))
        val encrypted = cipher.doFinal("backup".toByteArray())
        cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,ByteArray(12)))
        assertEquals("backup",String(cipher.doFinal(encrypted)))
    }
}
