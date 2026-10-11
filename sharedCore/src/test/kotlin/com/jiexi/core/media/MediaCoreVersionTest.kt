package com.jiexi.core.media

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaCoreVersionTest {
    @Test fun `upgrade older and unknown cores`() {
        assertTrue(MediaCoreVersion.shouldInstallBundled("2026.08.19", "2026.10.10.233141"))
        assertTrue(MediaCoreVersion.shouldInstallBundled(null, "2026.10.10.233141"))
        assertTrue(MediaCoreVersion.shouldInstallBundled("unknown", "2026.10.10.233141"))
        assertTrue(MediaCoreVersion.shouldInstallBundled("2026.10.10", "2026.10.10.233141"))
    }
    @Test fun `preserve equal and newer manually updated cores`() {
        assertFalse(MediaCoreVersion.shouldInstallBundled("2026.10.10.233141", "2026.10.10.233141"))
        assertFalse(MediaCoreVersion.shouldInstallBundled("2026.10.11.001000", "2026.10.10.233141"))
        assertFalse(MediaCoreVersion.shouldInstallBundled("2026.11.01", "2026.10.10.233141"))
    }
}
