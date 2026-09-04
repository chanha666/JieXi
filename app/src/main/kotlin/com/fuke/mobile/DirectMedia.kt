package com.fuke.mobile

import java.net.URI
import java.net.URLDecoder

object DirectMedia {
    private val videoExtensions = setOf("mp4", "webm", "mov", "m4v", "mkv")

    fun isVideo(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        return path.substringAfterLast('.', "").lowercase() in videoExtensions
    }

    fun analyze(url: String): VideoPreview {
        val rawName = runCatching { URI(url).path.substringAfterLast('/') }.getOrDefault("")
        val title = runCatching { URLDecoder.decode(rawName, Charsets.UTF_8.name()) }
            .getOrDefault(rawName)
            .substringBeforeLast('.')
            .ifBlank { "直链视频" }
        return VideoPreview(
            url = url,
            downloadUrl = url,
            title = title,
            uploader = "",
            platform = "直链视频",
            durationSeconds = 0,
            thumbnail = "",
            formats = listOf(
                FormatChoice("best", "原始文件 · 无转码"),
                FormatChoice("bestaudio/best", "仅音频 MP3")
            )
        )
    }
}
