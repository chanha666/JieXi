package com.yunx.desktop.media

import java.util.concurrent.ConcurrentHashMap

/**
 * A remove tombstone that cannot be consumed before the worker's final commit.
 * The commit callback and remove decision are serialized for the same task.
 */
internal class DesktopMediaRemovalRegistry {
    private class Entry(var removed: Boolean = false)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun begin(taskId: String) {
        entries.computeIfAbsent(taskId) { Entry() }
    }

    fun markRemoved(taskId: String) {
        val entry = entries.computeIfAbsent(taskId) { Entry() }
        synchronized(entry) { entry.removed = true }
    }

    fun isRemoved(taskId: String): Boolean {
        val entry = entries[taskId] ?: return false
        return synchronized(entry) { entry.removed }
    }

    /** Returns false without invoking [commit] when remove won the race. */
    fun commitIfActive(taskId: String, commit: () -> Unit): Boolean {
        val entry = entries.computeIfAbsent(taskId) { Entry() }
        return synchronized(entry) {
            if (entry.removed) false else {
                commit()
                true
            }
        }
    }

    fun finish(taskId: String) {
        entries[taskId]?.let { entry -> entries.remove(taskId, entry) }
    }
}
