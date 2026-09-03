package com.yunx.app.data.download

import org.junit.Assert.*
import org.junit.Test

class DownloadErrorTest {
    @Test fun classifiesAuthenticationAndPasscodeFailures() {
        assertEquals("AUTH_EXPIRED", DownloadErrorClassifier.classify(IllegalStateException("HTTP 401 cookie expired")).code)
        assertEquals("PASSCODE_REQUIRED", DownloadErrorClassifier.classify(IllegalArgumentException("需要提取码")).code)
    }

    @Test fun transientFailuresAreRetryable() {
        val failure = DownloadErrorClassifier.classify(IllegalStateException("HTTP 503 timeout"))
        assertTrue(failure.retryable)
        assertEquals("NETWORK_RETRYABLE", failure.code)
    }

    @Test fun retryDelayGrowsAndIsBounded() {
        val first = DownloadErrorClassifier.backoffMillis(1, jitter = 0)
        val fourth = DownloadErrorClassifier.backoffMillis(4, jitter = 0)
        assertTrue(fourth > first)
        assertTrue(DownloadErrorClassifier.backoffMillis(20, jitter = 750) <= 60_000L)
    }
}
