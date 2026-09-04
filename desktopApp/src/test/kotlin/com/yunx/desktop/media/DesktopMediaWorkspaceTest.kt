package com.yunx.desktop.media

import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMediaWorkspaceTest {
    @Test
    fun `task workspace accepts only an exact UUID beneath the owned root`() = withTemporaryDirectory { download ->
        val id = UUID.randomUUID().toString()
        val directory = DesktopMediaWorkspace.taskDirectory(download, id)

        assertEquals(
            download.toPath().toAbsolutePath().normalize().resolve(".jiexi-media-tasks").resolve(id),
            directory.toPath()
        )
        assertFailsWith<IllegalArgumentException> {
            DesktopMediaWorkspace.taskDirectory(download, "..${File.separator}outside")
        }
        assertFailsWith<IllegalArgumentException> {
            DesktopMediaWorkspace.taskDirectory(download, "$id${File.separator}outside")
        }
    }

    @Test
    fun `deleting one task workspace leaves sibling and outside files untouched`() = withTemporaryDirectory { download ->
        val removedId = UUID.randomUUID().toString()
        val siblingId = UUID.randomUUID().toString()
        val removed = DesktopMediaWorkspace.taskDirectory(download, removedId).apply { mkdirs() }
        val sibling = DesktopMediaWorkspace.taskDirectory(download, siblingId).apply { mkdirs() }
        val removedPart = File(removed, "private-video.mp4.part").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val siblingPart = File(sibling, "keep.mp4.part").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val outside = File(download, "keep-user-video.mp4").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        DesktopMediaWorkspace.deleteTaskDirectory(download, removedId)

        assertFalse(removed.exists())
        assertFalse(removedPart.exists())
        assertTrue(siblingPart.isFile)
        assertTrue(outside.isFile)
        assertContentEquals(byteArrayOf(7, 8, 9), outside.readBytes())
    }

    @Test
    fun `completed output never overwrites an existing same-name file`() = withTemporaryDirectory { download ->
        val id = UUID.randomUUID().toString()
        val workspace = DesktopMediaWorkspace.taskDirectory(download, id).apply { mkdirs() }
        val existing = File(download, "same-name.mp4").apply { writeBytes(byteArrayOf(1, 1, 1)) }
        val source = File(workspace, "same-name.mp4").apply { writeBytes(byteArrayOf(2, 2, 2)) }

        val moved = DesktopMediaWorkspace.moveCompletedWithoutOverwrite(source, workspace, download)

        assertEquals("same-name (1).mp4", moved.name)
        assertContentEquals(byteArrayOf(1, 1, 1), existing.readBytes())
        assertContentEquals(byteArrayOf(2, 2, 2), moved.readBytes())
        assertFalse(source.exists())
    }

    @Test
    fun `output validation rejects a file outside the task workspace`() = withTemporaryDirectory { download ->
        val workspace = DesktopMediaWorkspace.taskDirectory(download, UUID.randomUUID().toString()).apply { mkdirs() }
        val outside = File(download, "outside.mp4").apply { writeText("user data") }

        assertFailsWith<IllegalArgumentException> {
            DesktopMediaWorkspace.requireOwnedOutput(workspace, outside)
        }
        assertTrue(outside.isFile)
    }

    @Test
    fun `a resumed task keeps its original root after the setting changes`() {
        withTwoTemporaryDirectories { original, changed ->
            val task = mediaTask()
            val boundOriginal = DesktopMediaWorkspace.bindTaskRoot(task, original)
            val originalWorkspace = DesktopMediaWorkspace.taskDirectory(boundOriginal, task.id).apply { mkdirs() }
            val partial = File(originalWorkspace, "resume.mp4.part").apply { writeText("partial") }
            val unrelatedNewRootFile = File(
                DesktopMediaWorkspace.taskDirectory(changed, UUID.randomUUID().toString()).apply { mkdirs() },
                "unrelated.part"
            ).apply { writeText("keep") }

            val resumedRoot = DesktopMediaWorkspace.bindTaskRoot(task, changed)
            assertEquals(boundOriginal, resumedRoot)
            val newTaskRoot = DesktopMediaWorkspace.bindTaskRoot(mediaTask(), changed)
            assertEquals(DesktopMediaWorkspace.normalizeRoot(changed), newTaskRoot)
            DesktopMediaWorkspace.deleteTaskDirectory(resumedRoot, task.id)

            assertFalse(partial.exists())
            assertTrue(unrelatedNewRootFile.isFile)
            assertTrue(changed.isDirectory)
        }
    }

    private fun mediaTask() = DesktopMediaTask(
        sourceUrl = "https://example.invalid/video",
        downloadUrl = "",
        title = "test",
        platform = "媒体",
        formatSelector = "best",
        formatLabel = "best",
        outputFormat = "mp4",
        embedSubtitles = false
    )

    private fun withTwoTemporaryDirectories(block: (File, File) -> Unit) {
        withTemporaryDirectory { first -> withTemporaryDirectory { second -> block(first, second) } }
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("jiexi-media-workspace-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
