package com.fuke.mobile

import java.io.IOException

internal object DirectDownloadRetryPolicy {
    fun maxAttempts(configuredRetries: Int): Int = configuredRetries.coerceIn(0, 10) + 1

    fun shouldRetry(
        error: Throwable,
        attempt: Int,
        maxAttempts: Int,
        stopRequested: Boolean
    ): Boolean = !stopRequested && error is IOException && attempt < maxAttempts &&
        error !is RangeUnsupportedException &&
        (error !is MediaHttpException || error.status == 408 || error.status == 429 || error.status in 500..599)

    fun backoffMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 4)
        return (400L shl exponent).coerceAtMost(4_000L)
    }
}
