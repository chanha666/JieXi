package com.yunx.app.data.download

import org.junit.Assert.*
import org.junit.Test

class DownloadPresetTest {
    @Test fun builtInPresetsHaveSafeIncreasingLimits() {
        assertEquals(1, DownloadPreset.STABLE.concurrentTasks)
        assertTrue(DownloadPreset.BALANCED.connections > DownloadPreset.STABLE.connections)
        assertTrue(DownloadPreset.TURBO.concurrentTasks > DownloadPreset.BALANCED.concurrentTasks)
        assertEquals(1, DownloadPreset.SINGLE_TASK_MAX.concurrentTasks)
        assertEquals(48, DownloadPreset.SINGLE_TASK_MAX.connections)
    }

    @Test fun unknownStoredPresetFallsBackToBalanced() {
        assertEquals(DownloadPreset.SINGLE_TASK_MAX, DownloadPreset.from("removed-preset"))
    }
}
