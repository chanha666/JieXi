package com.jiexi.core.media

import org.junit.Test
import kotlin.test.*

class CloudAccessFailurePolicyTest {
    @Test fun recognizesRealAuthenticationFailures() {
        for (text in listOf("HTTP 401", "401 Unauthorized", "cookie expired", "invalid token",
            "Cookie 未配置，请先登录", "登录已失效", "not logged in", "需要重新登录")) {
            assertTrue(CloudAccessFailurePolicy.isAuthenticationFailure(text), text)
        }
    }
    @Test fun doesNotTurnUnrelatedNetworkFailuresIntoLoginFailures() {
        for (text in listOf("HTTP 503 timeout https://cdn.example/a?cookie=401",
            "connection reset, cookie transport", "download 1401338.mp4 failed", "cookie read timeout",
            "HTTP 403 signed URL expired")) {
            assertFalse(CloudAccessFailurePolicy.isAuthenticationFailure(text), text)
        }
    }
}
