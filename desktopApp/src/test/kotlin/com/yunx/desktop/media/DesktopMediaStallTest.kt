package com.yunx.desktop.media

import com.yunx.desktop.settings.DesktopSettings
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import javax.swing.SwingUtilities
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMediaStallTest {
    @Test fun `unresponsive source leaves downloading state and keeps resumable workspace`() = verifyStall(null)
    @Test fun `pause wins over a stalled transfer and preserves the task`() = verifyStall("pause")
    @Test fun `cancel wins over a stalled transfer`() = verifyStall("cancel")

    private fun verifyStall(action: String?) {
        val engine = DesktopMediaEngine()
        assumeTrue(engine.tools.ready)
        val base = File("D:/CodexCache/JieXi/stall-tests").apply { mkdirs() }
        val root = Files.createTempDirectory(base.toPath(), "case-").toFile()
        val prefs = Preferences.userRoot().node("com/yunx/test/stall/${UUID.randomUUID()}")
        val server = MockWebServer().apply { enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); start() }
        val controller = DesktopMediaController(DesktopSettings(prefs).apply { downloadDirectory = root; wifiOnly = false }, engine, File(root, "state.json"), 1500)
        val url = server.url("/stall.mp4").toString()
        val task = DesktopMediaTask(url, url, "stall", "媒体直链", "best", "原画", "mp4", false)
        try {
            SwingUtilities.invokeAndWait { controller.tasks.add(task); controller.resume(task) }
            val expected = when (action) { "pause" -> MediaTaskState.PAUSED; "cancel" -> MediaTaskState.CANCELLED; else -> MediaTaskState.FAILED }
            val deadline = System.nanoTime() + 15_000_000_000L
            var failed = false
            var requested = false
            while (!failed && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait {
                    if (action != null && !requested && task.state == MediaTaskState.DOWNLOADING) {
                        if (action == "pause") controller.pause(task) else controller.cancel(task)
                        requested = true
                    }
                    failed = task.state == expected
                }
                Thread.sleep(25)
            }
            SwingUtilities.invokeAndWait {
                assertEquals(expected, task.state)
                if (action == null) assertTrue(task.error.orEmpty().contains("保留断点"), task.error)
                assertTrue(DesktopMediaWorkspace.taskDirectory(root, task.id).exists())
            }
        } finally {
            SwingUtilities.invokeAndWait { controller.cancel(task) }
            server.shutdown()
            prefs.removeNode()
            root.deleteRecursively()
        }
    }
}
