package com.fuke.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaErrorMessagesTest {
    @Test
    fun `maps common network failures to actionable messages`() {
        assertTrue(MediaErrorMessages.forAction(IllegalStateException("Unable to resolve host example.com: No address associated with hostname")).contains("域名解析失败"))
        assertTrue(MediaErrorMessages.forDownload(IllegalStateException("HTTP Error 404: Not Found")).contains("重新解析"))
        assertTrue(MediaErrorMessages.forDownload(IllegalStateException("视频文件请求失败（HTTP 403）。")).contains("拒绝了匿名访问"))
        assertTrue(MediaErrorMessages.forDownload(IllegalStateException("Read timed out")).contains("连接超时"))
        assertTrue(MediaErrorMessages.forAction(IllegalStateException("Cleartext HTTP traffic to example.com not permitted")).contains("HTTPS"))
    }

    @Test
    fun `reads nested causes and keeps anonymous access guidance`() {
        val error = IllegalStateException("wrapper", java.net.UnknownHostException("No address associated with hostname"))
        assertTrue(MediaErrorMessages.forDownload(error).contains("域名解析失败"))
        assertEquals(
            "网站没有向匿名访问提供这个视频。",
            MediaErrorMessages.forAction(IllegalStateException("Sign in to confirm your age"))
        )
    }

    @Test
    fun `sanitizes unknown multiline failures`() {
        val message = MediaErrorMessages.forAction(IllegalStateException("first line\nsecond\tline"))
        assertFalse(message.contains('\n'))
        assertFalse(message.contains('\t'))
    }
}
