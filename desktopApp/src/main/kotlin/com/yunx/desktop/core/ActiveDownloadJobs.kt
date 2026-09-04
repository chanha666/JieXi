package com.yunx.desktop.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a task registered until its coroutine has actually finished.
 *
 * Removing a cancelled job immediately creates a pause -> resume race: a new
 * coroutine can start while the old one is still closing network calls and
 * files. Completion is identity checked so an old coroutine can never remove
 * a newer generation registered under the same task id.
 */
internal class ActiveDownloadJobs<K> {
    private val jobs = ConcurrentHashMap<K, Job>()

    val size: Int get() = jobs.size
    val isNotEmpty: Boolean get() = jobs.isNotEmpty()

    fun contains(key: K): Boolean = jobs.containsKey(key)

    fun register(key: K, job: Job): Boolean = jobs.putIfAbsent(key, job) == null

    fun cancel(key: K, cause: CancellationException? = null): Job? = jobs[key]?.also { job ->
        if (cause == null) job.cancel() else job.cancel(cause)
    }

    fun finish(key: K, job: Job): Boolean = jobs.remove(key, job)
}
