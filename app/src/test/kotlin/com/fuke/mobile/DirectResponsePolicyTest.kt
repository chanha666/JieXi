package com.fuke.mobile

import org.junit.Test
import org.junit.Assert.*

class DirectResponsePolicyTest {
    @Test fun rejectSuccessfulErrorPagesAndManifests() {
        for (mime in listOf("text/html; charset=utf-8", "TEXT/PLAIN", "application/json",
            "application/vnd.apple.mpegurl", "application/dash+xml")) {
            assertThrows(IllegalStateException::class.java) { DirectResponsePolicy.validate(200, mime) }
        }
    }
    @Test fun acceptMediaAndUntypedBinaryResponses() {
        for (mime in listOf(null, "", "video/mp4", "application/octet-stream", "audio/mpeg")) {
            DirectResponsePolicy.validate(200, mime)
            DirectResponsePolicy.validate(206, mime)
        }
    }
    @Test fun httpErrorsRetainStatusAndDoNotRetryPermanentFailures() {
        for (status in listOf(401, 403, 404, 410, 416)) {
            val error = assertThrows(MediaHttpException::class.java) { DirectResponsePolicy.validate(status, null) }
            assertEquals(status, error.status)
            assertFalse(DirectDownloadRetryPolicy.shouldRetry(error, 1, 4, false))
        }
        for (status in listOf(408, 429, 500, 502, 503)) {
            assertTrue(DirectDownloadRetryPolicy.shouldRetry(MediaHttpException(status), 1, 4, false))
        }
        assertFalse(DirectDownloadRetryPolicy.shouldRetry(RangeUnsupportedException(), 1, 4, false))
    }
}
