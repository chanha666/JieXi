package com.yunx.desktop.core

import org.junit.Test
import kotlin.test.*

class DesktopFailureClassifierTest {
    @Test fun realAuthenticationFailuresRequireLogin() {
        assertEquals("AUTH_EXPIRED" to false, DesktopFailureClassifier.classify(IllegalStateException("HTTP 401 cookie expired")))
    }
    @Test fun networkErrorsDoNotDemandLoginBecauseOfUrlOrCookieWord() {
        assertEquals("NETWORK_RETRYABLE" to true, DesktopFailureClassifier.classify(
            IllegalStateException("HTTP 503 cookie read timeout https://cdn.example/?id=401")))
        assertNotEquals("AUTH_EXPIRED", DesktopFailureClassifier.classify(IllegalStateException("1401338.mp4 failed")).first)
    }
}
