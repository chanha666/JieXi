package com.yunx.app.data.security

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/** Clears only the selected provider's WebView cookies; other signed-in drives stay untouched. */
object PlatformCookieCleaner {
    internal fun cookieNames(header: String): Set<String> = header.split(';')
        .mapNotNull { it.substringBefore('=', "").trim().takeIf(String::isNotEmpty) }
        .toSet()

    internal fun candidateDomains(host: String): Set<String> {
        val normalized = host.lowercase().trim().trimStart('.').trimEnd('.')
        if (normalized.isBlank()) return emptySet()
        val labels = normalized.split('.')
        val registrable = labels.takeLast(2).joinToString(".")
        return setOf(normalized, registrable)
    }

    suspend fun clear(urls: Collection<String>) = withContext(Dispatchers.Main.immediate) {
        val manager = CookieManager.getInstance()
        urls.distinct().forEach { url ->
            val host = runCatching { URI(url).host }.getOrNull().orEmpty()
            val header = manager.getCookie(url).orEmpty()
            cookieNames(header).forEach { name ->
                manager.setCookie(url, "$name=; Max-Age=0; Path=/; Secure; SameSite=Lax")
                candidateDomains(host).forEach { domain ->
                    manager.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$domain; Secure; SameSite=Lax")
                }
            }
        }
        manager.flush()
    }
}
