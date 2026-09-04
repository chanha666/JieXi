package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaTaskArtifactsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cleanupDeletesAllFragmentsAndNestedTemporaryFilesForOnlyThatTask() {
        val root = temporaryFolder.newFolder("downloads")
        val taskId = "11111111-2222-3333-4444-555555555555"
        val taskRoot = MediaTaskArtifacts.taskRoot(root, taskId).apply { mkdirs() }
        File(taskRoot, "video.mp4.part").writeBytes(ByteArray(13))
        File(taskRoot, "video.mp4.ytdl").writeBytes(ByteArray(7))
        File(taskRoot, "fragments").apply { mkdirs() }
            .resolve("fragment-42.part").writeBytes(ByteArray(29))
        val other = MediaTaskArtifacts.taskRoot(root, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
            .apply { mkdirs() }
        File(other, "keep.part").writeBytes(ByteArray(5))

        val result = MediaTaskArtifacts.cleanup(root, taskId)

        assertTrue(result.deleted)
        assertEquals(49L, result.bytesReleased)
        assertEquals(3, result.filesReleased)
        assertFalse(taskRoot.exists())
        assertTrue(File(other, "keep.part").isFile)
    }

    @Test
    fun cleanupIsIdempotent() {
        val root = temporaryFolder.newFolder("downloads")

        val result = MediaTaskArtifacts.cleanup(root, "11111111-2222-3333-4444-555555555555")

        assertTrue(result.deleted)
        assertEquals(0L, result.bytesReleased)
        assertEquals(0, result.filesReleased)
    }

    @Test
    fun taskIdCannotEscapeQueueDirectory() {
        val root = temporaryFolder.newFolder("downloads")
        assertThrows(IllegalArgumentException::class.java) {
            MediaTaskArtifacts.cleanup(root, "../another-task")
        }
    }
}
