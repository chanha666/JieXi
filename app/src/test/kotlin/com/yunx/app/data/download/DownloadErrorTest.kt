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

    @Test fun updateIntegrityFailuresAreNeverRetriedOrInstalled() {
        val failure = DownloadErrorClassifier.classify(IllegalStateException("更新包 SHA-256 安全校验失败"))
        assertEquals("UPDATE_INTEGRITY_FAILED", failure.code)
        assertEquals(DownloadStage.UPDATE_APP, failure.stage)
        assertFalse(failure.retryable)
        assertFalse(DownloadErrorClassifier.classify(IllegalStateException("更新包不是作者发行签名")).retryable)
    }

    @Test fun retryDelayGrowsAndIsBounded() {
        val first = DownloadErrorClassifier.backoffMillis(1, jitter = 0)
        val fourth = DownloadErrorClassifier.backoffMillis(4, jitter = 0)
        assertTrue(fourth > first)
        assertTrue(DownloadErrorClassifier.backoffMillis(20, jitter = 750) <= 60_000L)
    }
}
