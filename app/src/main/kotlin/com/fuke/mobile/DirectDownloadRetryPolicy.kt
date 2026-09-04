package com.fuke.mobile

import java.io.IOException

internal object DirectDownloadRetryPolicy {
    fun maxAttempts(configuredRetries: Int): Int = configuredRetries.coerceIn(0, 10) + 1

    fun shouldRetry(
        error: Throwable,
        attempt: Int,
        maxAttempts: Int,
        stopRequested: Boolean
    ): Boolean = !stopRequested && error is IOException && attempt < maxAttempts

    fun backoffMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 4)
        return (400L shl exponent).coerceAtMost(4_000L)
    }
}
