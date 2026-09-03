package com.yunx.desktop.settings

import org.junit.Assert.*
import org.junit.Test

class DesktopPresetTest {
    @Test fun presetsAreOrderedByThroughput() {
        assertTrue(DesktopPreset.BALANCED.connections > DesktopPreset.STABLE.connections)
        assertTrue(DesktopPreset.TURBO.concurrentTasks > DesktopPreset.BALANCED.concurrentTasks)
        assertEquals(1, DesktopPreset.SINGLE_TASK_MAX.concurrentTasks)
        assertEquals(48, DesktopPreset.SINGLE_TASK_MAX.connections)
    }

    @Test fun invalidValueFallsBackToBalanced() {
        assertEquals(DesktopPreset.SINGLE_TASK_MAX, DesktopPreset.from("invalid"))
    }
}
