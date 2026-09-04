package com.yunx.desktop.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaTaskStoreCodecTest {
    @Test
    fun dpapiEnvelopeRoundTripsWithoutLeavingSourceLinkPlaintext() {
        val json = """[{"sourceUrl":"https://example.invalid/private?token=secret"}]"""
        val encoded = DesktopMediaTaskStoreCodec.encode(json)

        assertNotEquals(json, encoded)
        assertFalse(encoded.contains("example.invalid"))
        val decoded = DesktopMediaTaskStoreCodec.decode(encoded)
        assertEquals(json, decoded.json)
        assertFalse(decoded.wasPlaintext)
    }

    @Test
    fun legacyJsonIsDetectedForImmediateMigration() {
        val decoded = DesktopMediaTaskStoreCodec.decode("  []  ")
        assertEquals("[]", decoded.json)
        assertTrue(decoded.wasPlaintext)
    }
}
