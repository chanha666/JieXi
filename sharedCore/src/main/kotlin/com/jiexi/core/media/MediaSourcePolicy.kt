package com.jiexi.core.media

import java.net.URI
import java.util.concurrent.CancellationException

/** Signed public CDN URLs must be refreshed, never reused after a failed refresh. */
object MediaSourcePolicy {
    fun resolve(platform: String, cachedUrl: String, refresh: () -> String): String {
        if (platform !in setOf("抖音", "X", "X / Twitter", "哔哩哔哩")) return cachedUrl
        return try {
            refresh().takeIf(::isHttpUrl).orEmpty()
        } catch (error: Exception) {
            if (error is CancellationException || error is InterruptedException) throw error
            "" // Empty means re-extract the original page, not retry an expired CDN URL.
        }
    }

    fun useDirectTransfer(url: String, outputFormat: String, embedSubtitles: Boolean): Boolean {
        if (!isHttpUrl(url) || outputFormat == "mp3" || embedSubtitles) return false
        val path = URI(url).path.orEmpty().lowercase()
        return !path.endsWith(".m3u8") && !path.endsWith(".m3u") && !path.endsWith(".mpd")
    }

    private fun isHttpUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme?.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
