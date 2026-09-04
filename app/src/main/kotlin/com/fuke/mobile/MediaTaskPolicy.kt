package com.fuke.mobile

/** Pure task-state rules shared by persistence and the download service. */
internal object MediaTaskPolicy {
    private val activeStatuses = setOf(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.PAUSED)

    fun hasActiveDuplicate(tasks: List<DownloadTask>, url: String): Boolean {
        val normalized = url.trim()
        return tasks.any { it.url.trim() == normalized && it.status in activeStatuses }
    }

    fun directOutputTemplate(title: String): String {
        val safeTitle = title
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(80)
            .ifBlank { "解析视频" }
        return "$safeTitle.%(ext)s"
    }

    fun restore(task: DownloadTask, now: Long, recoverRunning: Boolean = true): DownloadTask {
        val downloaded = task.downloadedBytes.coerceAtLeast(0L)
        val total = task.totalBytes.coerceAtLeast(0L).let { if (it > 0L) maxOf(it, downloaded) else it }
        val recovered = recoverRunning && task.status == TaskStatus.RUNNING
        val status = if (recovered) TaskStatus.PAUSED else task.status
        val progress = when (status) {
            TaskStatus.COMPLETED -> 100
            else -> task.progress.coerceIn(0, 99)
        }
        return task.copy(
            url = task.url.trim(),
            status = status,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
            stage = if (recovered) "已暂停" else task.stage,
            speed = if (recovered) "" else task.speed,
            error = if (recovered) "上次关闭软件时任务已暂停，点击继续即可断点续传。" else task.error,
            updatedAt = if (recovered) now else task.updatedAt
        )
    }
}
