package com.yunx.desktop.system

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SingleInstanceGuardTest {
    @Test
    fun rejectsSecondInstanceAndAllowsRestartAfterClose() {
        val root = Files.createTempDirectory("jiexi-instance-").toFile()
        val first = assertNotNull(SingleInstanceGuard.acquire(root))
        try {
            assertNull(SingleInstanceGuard.acquire(root))
        } finally {
            first.close()
        }
        assertNotNull(SingleInstanceGuard.acquire(root)).close()
        root.deleteRecursively()
    }
}
