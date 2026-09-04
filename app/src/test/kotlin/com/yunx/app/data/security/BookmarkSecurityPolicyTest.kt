package com.yunx.app.data.security

import com.yunx.app.data.db.BookmarkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BookmarkSecurityPolicyTest {
    @Test
    fun `removes legacy plaintext passcode before it reaches ui`() {
        val sanitized = BookmarkSecurityPolicy.withoutStoredPasscode(
            BookmarkEntity(
                link = "分享 https://pan.baidu.com/s/1Abc?pwd=1234 提取码：1234",
                pwd = "1234"
            )
        )
        assertEquals("", sanitized.pwd)
        assertEquals("https://pan.baidu.com/s/1Abc", sanitized.link)
    }

    @Test
    fun `does not copy already safe bookmark`() {
        val safe = BookmarkEntity(link = "https://example.invalid/share", pwd = "")
        assertSame(safe, BookmarkSecurityPolicy.withoutStoredPasscode(safe))
    }

    @Test
    fun `keeps nonsecret query and fragment while removing pwd`() {
        assertEquals(
            "https://example.invalid/share?from=app#folder",
            BookmarkSecurityPolicy.sanitizeLink("https://example.invalid/share?pwd=a1B2&from=app#folder")
        )
    }
}
