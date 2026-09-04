package com.yunx.desktop.media

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopMediaRemovalRegistryTest {
    @Test
    fun `late remove after process exit prevents final commit deterministically`() {
        val registry = DesktopMediaRemovalRegistry()
        val taskId = "task"
        registry.begin(taskId)

        // This models remove arriving after the worker consumed pause/cancel,
        // but before it tries to publish the completed file.
        registry.markRemoved(taskId)
        var published = false
        val committed = registry.commitIfActive(taskId) { published = true }

        assertFalse(committed)
        assertFalse(published)
        assertTrue(registry.isRemoved(taskId))
    }

    @Test
    fun `remove cannot interleave inside an accepted commit`() {
        val registry = DesktopMediaRemovalRegistry()
        val taskId = "task"
        registry.begin(taskId)
        val commitEntered = CountDownLatch(1)
        val allowCommit = CountDownLatch(1)
        val removeReturned = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val commit = executor.submit<Boolean> {
                registry.commitIfActive(taskId) {
                    commitEntered.countDown()
                    assertTrue(allowCommit.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(commitEntered.await(5, TimeUnit.SECONDS))
            executor.submit {
                registry.markRemoved(taskId)
                removeReturned.countDown()
            }
            assertFalse(removeReturned.await(150, TimeUnit.MILLISECONDS))
            allowCommit.countDown()
            assertTrue(commit.get(5, TimeUnit.SECONDS))
            assertTrue(removeReturned.await(5, TimeUnit.SECONDS))
            assertTrue(registry.isRemoved(taskId))
        } finally {
            executor.shutdownNow()
        }
    }
}
