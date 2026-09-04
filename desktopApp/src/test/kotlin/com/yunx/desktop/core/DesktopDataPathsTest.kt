package com.yunx.desktop.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDataPathsTest {
    @Test
    fun `persistent data directory is separate from the legacy install directory`() = withLocalAppData { root ->
        val data = DesktopDataPaths.dataDirectory(root)
        val legacy = DesktopDataPaths.legacyDirectory(root)

        assertEquals(root.resolve("JieXi/Data").normalize(), data)
        assertEquals(root.resolve("解析").normalize(), legacy)
        assertFalse(data.toPath().startsWith(legacy.toPath()))
        assertFalse(legacy.toPath().startsWith(data.toPath()))
    }

    @Test
    fun `state and media stores migrate once from legacy files`() = withLocalAppData { root ->
        val legacy = DesktopDataPaths.legacyDirectory(root).apply { mkdirs() }
        val oldState = legacy.resolve("state-v3.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val oldStateBackup = legacy.resolve("state-v3.bin.bak").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val oldMedia = legacy.resolve("media-tasks-v1.json").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        val newState = DesktopDataPaths.stateFile(root)
        val newMedia = DesktopDataPaths.mediaTasksFile(root)

        assertContentEquals(oldState.readBytes(), newState.readBytes())
        assertContentEquals(
            oldStateBackup.readBytes(),
            DesktopDataPaths.dataDirectory(root).resolve("state-v3.bin.bak").readBytes()
        )
        assertContentEquals(oldMedia.readBytes(), newMedia.readBytes())
        assertTrue(oldState.isFile, "migration must retain the installer-era recovery copy")
        assertTrue(oldStateBackup.isFile, "migration must retain the installer-era backup recovery copy")
        assertTrue(oldMedia.isFile, "migration must retain the installer-era recovery copy")
    }

    @Test
    fun `migration never overwrites an existing new data file`() = withLocalAppData { root ->
        val legacy = DesktopDataPaths.legacyDirectory(root).apply { mkdirs() }
        legacy.resolve("state-v3.bin").writeText("legacy")
        legacy.resolve("state-v3.bin.bak").writeText("legacy backup")
        val data = DesktopDataPaths.dataDirectory(root).apply { mkdirs() }
        val current = data.resolve("state-v3.bin").apply { writeText("current") }
        val currentBackup = data.resolve("state-v3.bin.bak").apply { writeText("current backup") }

        val selected = DesktopDataPaths.stateFile(root)

        assertEquals(current, selected)
        assertEquals("current", selected.readText())
        assertEquals("current backup", currentBackup.readText())
    }

    @Test
    fun `unrelated files in the legacy install directory are never copied`() = withLocalAppData { root ->
        DesktopDataPaths.legacyDirectory(root).apply { mkdirs() }
            .resolve("application.exe").writeText("program")

        val target = DesktopDataPaths.stateFile(root)

        assertFalse(target.exists())
        assertFalse(DesktopDataPaths.dataDirectory(root).resolve("application.exe").exists())
    }

    private fun withLocalAppData(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("jiexi-localappdata-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
