package com.yunx.app.data.security

import com.yunx.app.data.db.BookmarkEntity

/** Share passcodes are session input and must never be persisted with bookmarks. */
internal object BookmarkSecurityPolicy {
    private val URL = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun sanitizeLink(value: String): String {
        val url = URL.find(value.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: return ""
        val fragment = url.substringAfter('#', "").let { if (it.isBlank()) "" else "#$it" }
        val beforeFragment = url.substringBefore('#')
        val base = beforeFragment.substringBefore('?')
        val safeQuery = beforeFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotBlank() && !it.substringBefore('=').equals("pwd", ignoreCase = true) }
            .joinToString("&")
        return base + (if (safeQuery.isBlank()) "" else "?$safeQuery") + fragment
    }

    fun withoutStoredPasscode(bookmark: BookmarkEntity): BookmarkEntity {
        val safeLink = sanitizeLink(bookmark.link)
        return if (bookmark.pwd.isEmpty() && bookmark.link == safeLink) bookmark
        else bookmark.copy(link = safeLink, pwd = "")
    }
}
