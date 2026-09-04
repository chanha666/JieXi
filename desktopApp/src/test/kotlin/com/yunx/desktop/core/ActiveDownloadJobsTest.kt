package com.yunx.desktop.core

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveDownloadJobsTest {
    @Test
    fun cancelledGenerationKeepsSlotUntilItActuallyFinishes() {
        val registry = ActiveDownloadJobs<String>()
        val old = Job()
        val replacement = Job()

        assertTrue(registry.register("task", old))
        assertSame(old, registry.cancel("task"))
        assertFalse("resume must not overlap the cancelling job", registry.register("task", replacement))

        assertTrue(registry.finish("task", old))
        assertTrue(registry.register("task", replacement))

        (old as CompletableJob).complete()
        (replacement as CompletableJob).complete()
    }

    @Test
    fun oldGenerationCannotRemoveNewGeneration() {
        val registry = ActiveDownloadJobs<String>()
        val old = Job()
        val replacement = Job()

        assertTrue(registry.register("task", old))
        assertTrue(registry.finish("task", old))
        assertTrue(registry.register("task", replacement))
        assertFalse(registry.finish("task", old))
        assertFalse(registry.register("task", Job()))

        (old as CompletableJob).complete()
        (replacement as CompletableJob).complete()
    }
}
