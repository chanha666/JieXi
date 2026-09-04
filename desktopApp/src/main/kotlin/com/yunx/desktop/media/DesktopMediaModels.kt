package com.yunx.desktop.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

enum class MediaTaskState {
    WAITING,
    ANALYZING,
    DOWNLOADING,
    PAUSED,
    INTERRUPTED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaFormatChoice(
    val selector: String,
    val label: String,
    val estimatedBytes: Long = 0L
)

data class DesktopMediaPreview(
    val sourceUrl: String,
    val downloadUrl: String = "",
    val title: String,
    val uploader: String = "",
    val platform: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String = "",
    val formats: List<MediaFormatChoice>,
    val engine: String,
    val warning: String? = null
)

class DesktopMediaTask(
    val sourceUrl: String,
    downloadUrl: String,
    title: String,
    platform: String,
    val formatSelector: String,
    val formatLabel: String,
    val outputFormat: String,
    val embedSubtitles: Boolean,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis()
) {
    var downloadUrl by mutableStateOf(downloadUrl)
    var title by mutableStateOf(title)
    var platform by mutableStateOf(platform)
    var state by mutableStateOf(MediaTaskState.WAITING)
    var progress by mutableStateOf(0)
    var speed by mutableStateOf("")
    var eta by mutableStateOf("")
    var stage by mutableStateOf("等待中")
    var error by mutableStateOf<String?>(null)
    var outputFile by mutableStateOf<File?>(null)
    /** The immutable download root for this task's current resumable run. */
    var workspaceRoot: File? = null
    var updatedAt by mutableStateOf(System.currentTimeMillis())
}

data class MediaToolStatus(
    val ytDlpVersion: String,
    val ffmpegVersion: String,
    val denoVersion: String,
    val wechatPlugin: Boolean,
    val ready: Boolean,
    val detail: String
)
