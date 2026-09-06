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
    fun `update installation refuses active tasks and revalidates downloaded bytes`() {
        val temp = Files.createTempDirectory("jiexi-install-flow-test").toFile()
        val prefs = Preferences.userRoot().node("com/yunx/test/" + UUID.randomUUID())
        try {
            val controller = DesktopAppController(
                credentialStore = CredentialStore(prefs.node("credentials")), settings = DesktopSettings(prefs.node("settings")),
                stateStore = DesktopStateStore(File(temp, "state.bin")), mediaStoreFile = File(temp, "media.json"))
            val file = File(temp, "setup.exe").apply { writeText("verified test payload") }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val asset = com.yunx.desktop.update.DesktopUpdateAsset(file.name, "https://example.invalid/setup.exe", hash)
            val task = DesktopDownloadTask("active.bin").apply { state = TaskState.DOWNLOADING }
            javax.swing.SwingUtilities.invokeAndWait {
                controller.downloadedUpdate = file
                controller.updateRelease = com.yunx.desktop.update.DesktopRelease("v9.0.0", "", "", listOf(asset))
                controller.downloads += task
                controller.installUpdate { error("Must not exit while a task is active") }
                assertTrue(controller.updateMessage.orEmpty().contains("请先暂停"))
                task.state = TaskState.PAUSED
            }
            val ready = java.util.concurrent.CountDownLatch(1)
            javax.swing.SwingUtilities.invokeAndWait { controller.installUpdate { ready.countDown() } }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(com.yunx.desktop.update.DesktopUpdateService.verifiedFile(asset, file))
            file.writeText("tampered")
            assertTrue(!com.yunx.desktop.update.DesktopUpdateService.verifiedFile(asset, file))
        } finally {
            prefs.removeNode()
            temp.deleteRecursively()
        }
    }

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
