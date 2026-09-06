package com.yunx.desktop.core

import kotlin.test.Test
import kotlin.test.assertTrue
import com.yunx.desktop.settings.DesktopSettings
import com.yunx.desktop.security.CredentialStore
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences

class DesktopAppControllerTest {
    @Test
    fun `removes a download task from the visible list`() {
        val temp = Files.createTempDirectory("jiexi-controller-test").toFile()
        val prefs = Preferences.userRoot().node("com/yunx/test/" + UUID.randomUUID())
        val controller = DesktopAppController(
            credentialStore = CredentialStore(prefs.node("credentials")), settings = DesktopSettings(prefs.node("settings")),
            stateStore = DesktopStateStore(File(temp,"state.bin")), mediaStoreFile = File(temp,"media.json"))
        val task = DesktopDownloadTask("sample.bin")
        controller.downloads += task

        controller.removeDownload(task)

        assertTrue(controller.downloads.isEmpty())
    }
}
