package com.fuke.mobile

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDownloadRetryPolicyTest {
    @Test
    fun configuredRetriesAreBoundedAndIncludeInitialAttempt() {
        assertEquals(1, DirectDownloadRetryPolicy.maxAttempts(-1))
        assertEquals(4, DirectDownloadRetryPolicy.maxAttempts(3))
        assertEquals(11, DirectDownloadRetryPolicy.maxAttempts(99))
    }

    @Test
    fun onlyTransientIoFailuresRetryWhileAttemptsRemain() {
        assertTrue(DirectDownloadRetryPolicy.shouldRetry(IOException("unexpected end"), 1, 4, false))
        assertFalse(DirectDownloadRetryPolicy.shouldRetry(IOException("unexpected end"), 4, 4, false))
        assertFalse(DirectDownloadRetryPolicy.shouldRetry(IOException("stopped"), 1, 4, true))
        assertFalse(DirectDownloadRetryPolicy.shouldRetry(IllegalStateException("bad state"), 1, 4, false))
    }

    @Test
    fun retryBackoffIsShortAndCapped() {
        assertEquals(400L, DirectDownloadRetryPolicy.backoffMillis(1))
        assertEquals(800L, DirectDownloadRetryPolicy.backoffMillis(2))
        assertEquals(4_000L, DirectDownloadRetryPolicy.backoffMillis(99))
    }
}
