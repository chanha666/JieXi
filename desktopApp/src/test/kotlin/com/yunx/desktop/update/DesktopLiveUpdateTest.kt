package com.yunx.desktop.update

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Explicit opt-in only: downloads an author-signed public release, never installs it. */
class DesktopLiveUpdateTest {
    @Test fun `live signed release can be checked and downloaded using system proxy`() {
        assumeTrue("Opt-in live network test", System.getenv("JIEXI_LIVE_UPDATE_TEST") == "1")
        runBlocking {
            val service = DesktopUpdateService()
            val release = assertNotNull(service.check())
            val asset = assertNotNull(DesktopUpdateService.windowsInstaller(release))
            val file = service.download(asset, File("D:/CodexBuilds/JieXi/update-network-qa"))
            assertTrue(file.isFile && file.length() > 0)
            println("Verified public update: ${release.version} / ${file.name} / ${file.length()} bytes")
        }
    }
}
