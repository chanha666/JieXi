package com.yunx.desktop.system

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticBundleExporterTest {
    @Test
    fun redactsCredentialsAndUrlQueries() {
        val output = DiagnosticBundleExporter.redact(
            "cookie=secret token:abc https://example.com/file?id=123&sign=xyz harmless=value"
        )
        assertFalse("secret" in output)
        assertFalse("abc" in output)
        assertFalse("id=123" in output)
        assertTrue("harmless=value" in output)
        assertTrue("[REDACTED]" in output)
    }
}
