package com.fuke.mobile

import java.util.UUID

enum class TaskStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELED }

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val resolvedUrl: String = "",
    val title: String = "等待获取视频标题",
    val platform: String = "视频",
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Int = 0,
    val qualityLabel: String = "匿名最高画质",
    val formatSelector: String = "bestvideo+bestaudio/best",
    val outputFormat: String = "mp4",
    val embedSubtitles: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val stage: String = "等待中",
    val speed: String = "",
    val eta: String = "",
    val fileUri: String = "",
    val fileName: String = "",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FormatChoice(
    val selector: String,
    val label: String,
    val bytes: Long = 0L
)

data class VideoPreview(
    val url: String,
    val downloadUrl: String = "",
    val title: String,
    val uploader: String,
    val platform: String,
    val durationSeconds: Int,
    val thumbnail: String,
    val formats: List<FormatChoice>
)

data class AppSettings(
    val defaultQuality: String = "best",
    val retries: Int = 3,
    val wifiOnly: Boolean = false,
    val fileNameRule: String = "title-id"
)
