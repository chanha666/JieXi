package com.yunx.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateVerifierTest {
    private val installed = ApkIdentity("com.yunx.app", 40, setOf("old-signer", "new-signer"))

    @Test
    fun `accepts newer package with signer lineage overlap`() {
        val result = ApkUpdateVerifier.compare(
            installed,
            ApkIdentity("com.yunx.app", 41, setOf("new-signer"))
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `rejects wrong package downgrade and unrelated signer`() {
        assertFalse(
            ApkUpdateVerifier.compare(installed, ApkIdentity("other.app", 41, setOf("new-signer"))).accepted
        )
        assertFalse(
            ApkUpdateVerifier.compare(installed, ApkIdentity("com.yunx.app", 40, setOf("new-signer"))).accepted
        )
        assertFalse(
            ApkUpdateVerifier.compare(installed, ApkIdentity("com.yunx.app", 41, setOf("attacker"))).accepted
        )
    }

    @Test
    fun `rejects missing signature metadata`() {
        assertFalse(
            ApkUpdateVerifier.compare(installed, ApkIdentity("com.yunx.app", 41, emptySet())).accepted
        )
    }
}
