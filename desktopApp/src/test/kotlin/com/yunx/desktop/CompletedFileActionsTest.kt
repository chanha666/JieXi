package com.yunx.desktop

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class CompletedFileActionsTest {
    @Test fun `recycle target must be an absolute regular file not a directory`() {
        val dir = Files.createTempDirectory("jiexi-output-test").toFile()
        val output = File(dir,"clip.mp4").apply { writeText("test") }
        try {
            assertTrue(recyclableOutput(output))
            assertFalse(recyclableOutput(dir))
            assertFalse(recyclableOutput(File(dir,"missing.mp4")))
            assertFalse(recyclableOutput(File("relative.mp4")))
        } finally { output.delete(); dir.delete() }
    }
}
