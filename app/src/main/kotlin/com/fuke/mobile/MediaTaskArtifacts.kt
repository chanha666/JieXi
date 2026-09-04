package com.fuke.mobile

import android.content.Context
import android.os.Environment
import java.io.File

/** Owns the app-private download fragments for one media-queue task. */
internal object MediaTaskArtifacts {
    data class CleanupResult(val deleted: Boolean, val bytesReleased: Long, val filesReleased: Int)

    fun taskRoot(context: Context, taskId: String): File {
        val rootParent = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        return taskRoot(rootParent, taskId)
    }

    fun cleanup(context: Context, taskId: String): CleanupResult = cleanup(taskRoot(context, taskId))

    /** Pure entry point used by JVM tests and by the service/UI removal path. */
    fun cleanup(rootParent: File, taskId: String): CleanupResult = cleanup(taskRoot(rootParent, taskId))

    internal fun taskRoot(rootParent: File, taskId: String): File {
        require(SAFE_TASK_ID.matches(taskId)) { "Invalid media task id" }
        return File(File(rootParent, "queue"), taskId)
    }

    private fun cleanup(taskRoot: File): CleanupResult {
        if (!taskRoot.exists()) return CleanupResult(deleted = true, bytesReleased = 0L, filesReleased = 0)
        val files = taskRoot.walkTopDown().filter(File::isFile).toList()
        val bytes = files.sumOf(File::length)
        val deleted = taskRoot.deleteRecursively() && !taskRoot.exists()
        return CleanupResult(deleted, bytes, files.size)
    }

    private val SAFE_TASK_ID = Regex("[A-Za-z0-9_-]{1,128}")
}
