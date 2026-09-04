package com.yunx.app.data.download

/**
 * Per-task compare-and-set identity gate.
 *
 * A [Lease] identifies exactly one invocation of a task. Pause/resume advances
 * the generation, so a completion/finally block from the old invocation cannot
 * clear or commit state for the resumed invocation. Removal is a permanent
 * tombstone because Room task ids are never reused.
 */
internal class TaskRunGeneration {
    class Lease internal constructor(
        internal val generation: Long,
        private val identity: Any = Any()
    )

    class Revision internal constructor(
        internal val generation: Long,
        internal val removed: Boolean
    )

    private var generation = 0L
    private var removed = false
    private var current: Lease? = null

    /** Starts only when no invocation is already current and the task was not removed. */
    @Synchronized
    fun tryStart(): Lease? {
        if (removed || current != null) return null
        return Lease(++generation).also { current = it }
    }

    /** Invalidates the active invocation and records a new paused-command revision. */
    @Synchronized
    fun pause(): Revision? {
        if (removed) return null
        current = null
        return Revision(++generation, removed = false)
    }

    /** Invalidates the active invocation and permanently rejects continuation. */
    @Synchronized
    fun remove(): Revision {
        removed = true
        current = null
        return Revision(++generation, removed = true)
    }

    @Synchronized
    fun isCurrent(lease: Lease): Boolean = !removed && current === lease && lease.generation == generation

    @Synchronized
    fun isCurrent(revision: Revision): Boolean =
        generation == revision.generation && removed == revision.removed && current == null

    /** Identity-CAS release: an old finally block cannot clear a newer lease. */
    @Synchronized
    fun release(lease: Lease): Boolean {
        if (!isCurrent(lease)) return false
        current = null
        return true
    }
}
