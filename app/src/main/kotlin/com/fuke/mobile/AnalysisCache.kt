package com.fuke.mobile

import java.util.LinkedHashMap

object AnalysisCache {
    private const val MAX_ENTRIES = 24
    private const val MAX_AGE_MS = 5 * 60 * 1000L
    private data class Entry(val preview: VideoPreview, val createdAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): VideoPreview? {
        val key = url.trim()
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > MAX_AGE_MS) {
            entries.remove(key)
            return null
        }
        return entry.preview
    }

    @Synchronized
    fun put(url: String, preview: VideoPreview) {
        entries[url.trim()] = Entry(preview, System.currentTimeMillis())
    }
}
