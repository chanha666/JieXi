package com.fuke.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDiagnosticsTest {
    @Test
    fun summaryContainsOnlyAllowlistedTaskFieldsAndRedactsErrorSecrets() {
        val task = DownloadTask(
            id = "11111111-2222-3333-4444-abcdef123456",
            url = "https://source.example/private/video?id=secret-source",
            resolvedUrl = "https://cdn.example/file.mp4?sign=secret-signature",
            title = "private title",
            platform = "抖音",
            status = TaskStatus.FAILED,
            progress = 57,
            fileUri = "content://downloads/private/42",
            error = "读取 C:\\Users\\name\\private.bin 失败 https://cdn.example/a?sign=hidden Cookie=session-secret"
        )

        val summary = MediaDiagnostics.taskSummary(listOf(task))

        assertTrue(summary.contains("#123456"))
        assertTrue(summary.contains("平台=抖音"))
        assertTrue(summary.contains("状态=FAILED"))
        assertTrue(summary.contains("进度=57%"))
        listOf("secret-source", "secret-signature", "private title", "content://", "Users", "hidden", "session-secret")
            .forEach { assertFalse("leaked $it", summary.contains(it)) }
    }
}
