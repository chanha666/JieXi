package com.yunx.app.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRunGenerationTest {
    @Test
    fun staleInvocationCannotCommitOrReleaseResumedInvocation() {
        val gate = TaskRunGeneration()
        val first = requireNotNull(gate.tryStart())
        val pause = requireNotNull(gate.pause())
        val resumed = requireNotNull(gate.tryStart())

        assertFalse(gate.isCurrent(first))
        assertFalse(gate.isCurrent(pause))
        assertFalse(gate.release(first))
        assertTrue(gate.isCurrent(resumed))
    }

    @Test
    fun onlyLatestPauseRevisionMayCommitPausedState() {
        val gate = TaskRunGeneration()
        requireNotNull(gate.tryStart())
        val firstPause = requireNotNull(gate.pause())
        val secondPause = requireNotNull(gate.pause())

        assertFalse(gate.isCurrent(firstPause))
        assertTrue(gate.isCurrent(secondPause))
    }

    @Test
    fun removalIsPermanentTombstoneForSameTaskId() {
        val gate = TaskRunGeneration()
        val running = requireNotNull(gate.tryStart())
        val removal = gate.remove()

        assertFalse(gate.isCurrent(running))
        assertTrue(gate.isCurrent(removal))
        assertNull(gate.tryStart())
        assertFalse(gate.release(running))
        assertNull(gate.pause())
        assertTrue(gate.isCurrent(removal))
    }

    @Test
    fun duplicateStartIsRejectedUntilCurrentLeaseReleases() {
        val gate = TaskRunGeneration()
        val first = requireNotNull(gate.tryStart())

        assertNull(gate.tryStart())
        assertTrue(gate.release(first))
        assertNotNull(gate.tryStart())
    }
}
