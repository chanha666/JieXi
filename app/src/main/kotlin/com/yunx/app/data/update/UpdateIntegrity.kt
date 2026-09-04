package com.yunx.app.data.update

import java.io.File
import java.security.MessageDigest

/** Integrity checks for APK assets referenced by the author-signed update manifest. */
internal object UpdateIntegrity {
    private val SHA256 = Regex("[0-9a-fA-F]{64}")

    fun normalizeExpectedSha256(value: String): String? =
        value.trim().takeIf { SHA256.matches(it) }?.lowercase()

    fun verify(file: File, expectedSha256: String): Boolean {
        val expected = normalizeExpectedSha256(expectedSha256) ?: return false
        return sha256(file).equals(expected, ignoreCase = true)
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
