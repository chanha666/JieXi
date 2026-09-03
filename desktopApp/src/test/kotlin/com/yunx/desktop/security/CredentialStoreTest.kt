package com.yunx.desktop.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.prefs.Preferences

class CredentialStoreTest {
    @Test
    fun protectsAndRestoresCredentialForCurrentWindowsUser() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
        val node = Preferences.userRoot().node("com/yunx/desktop/test/${UUID.randomUUID()}")
        try {
            val store = CredentialStore(node)
            store.put(CredentialKey.QUARK_COOKIE, "secret-cookie")
            assertEquals("secret-cookie", store.get(CredentialKey.QUARK_COOKIE))
            store.remove(CredentialKey.QUARK_COOKIE)
            assertFalse(store.has(CredentialKey.QUARK_COOKIE))
        } finally {
            node.removeNode()
        }
    }
}
