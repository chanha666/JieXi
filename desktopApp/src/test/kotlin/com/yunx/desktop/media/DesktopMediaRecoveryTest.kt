package com.yunx.desktop.media

import com.yunx.desktop.settings.DesktopSettings
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import javax.swing.SwingUtilities
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopMediaRecoveryTest {
    @Test
    fun `retry recovers a verified existing video without any source request`() {
        val engine = DesktopMediaEngine()
        assumeTrue("Windows bundled toolchain required", engine.tools.ready)
        val base = File("D:/CodexCache/JieXi/recovery-tests").apply { mkdirs() }
        val directory = Files.createTempDirectory(base.toPath(), "case-").toFile()
        val preferences = Preferences.userRoot().node("com/yunx/test/recovery/${UUID.randomUUID()}")
        val server = MockWebServer().apply { start() }
        var controller: DesktopMediaController? = null
        try {
            val settings = DesktopSettings(preferences).apply { downloadDirectory = directory; wifiOnly = false }
            val task = DesktopMediaTask(server.url("/must-not-download.mp4").toString(), "", "本地恢复", "媒体直链",
                "best", "原画", "mp4", false).apply {
                workspaceRoot = directory
                state = MediaTaskState.FAILED
                error = "媒体核心返回了工作目录之外的文件。"
            }
            val workspace = DesktopMediaWorkspace.taskDirectory(directory, task.id).apply { mkdirs() }
            val source = File(workspace, "中文 视频.mp4")
            engine.runFfmpeg(listOf("-f", "lavfi", "-i", "color=c=black:s=64x64:r=24:d=0.25", "-c:v", "mpeg4", source.absolutePath), 30)
            val expected = source.readBytes()
            val done = CountDownLatch(1)
            val instance = DesktopMediaController(settings, engine, File(directory, "isolated-state.json"))
            controller = instance
            instance.onTaskNotification = { _, _ -> done.countDown() }
            SwingUtilities.invokeAndWait { instance.tasks.add(task); instance.resume(task) }
            assertTrue(done.await(20, TimeUnit.SECONDS), "Recovery timed out")
            SwingUtilities.invokeAndWait {
                assertEquals(MediaTaskState.COMPLETED, task.state, task.error)
                assertEquals(100, task.progress)
                assertContentEquals(expected, task.outputFile!!.readBytes())
            }
            assertEquals(0, server.requestCount, "Recovery must not download or refresh the source")
        } finally {
            controller?.let { instance -> SwingUtilities.invokeAndWait { instance.tasks.forEach(instance::cancel) } }
            server.shutdown()
            preferences.removeNode()
            directory.deleteRecursively()
        }
    }
}
