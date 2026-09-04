package com.fuke.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaFiles {
    fun publish(
        context: Context,
        source: File,
        displayName: String = source.name,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Uri {
        require(source.isFile) { "要保存的文件不存在" }
        val safeName = sanitizeDisplayName(displayName, source.extension)
        val mime = mimeForName(safeName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return publishLegacy(context, source, safeName, onProgress)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/解析")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("系统没有创建输出文件")
        try {
            val total = source.length().coerceAtLeast(0L)
            val stream = resolver.openOutputStream(uri, "w") ?: error("系统无法写入输出文件")
            stream.buffered(1024 * 1024).use { output ->
                source.inputStream().buffered(1024 * 1024).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    var copied = 0L
                    var lastUpdate = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 350L || copied == total) {
                            onProgress(copied, total)
                            lastUpdate = now
                        }
                    }
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    fun copyToCache(context: Context, uri: Uri, prefix: String): File {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "mp4"
        val safePrefix = prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40).ifBlank { "media" }
        val output = File(context.cacheDir, "$safePrefix-${System.currentTimeMillis()}.$extension")
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: error("系统无法读取所选文件")
            stream.use { input -> FileOutputStream(output).use { input.copyTo(it) } }
            return output
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }

    fun downloadToCache(context: Context, url: String, suffix: String): File {
        val output = File(context.cacheDir, "resource-${System.currentTimeMillis()}$suffix")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("资源请求失败（$code）")
            connection.inputStream.use { input -> FileOutputStream(output).use { input.copyTo(it) } }
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun runFfmpeg(context: Context, arguments: List<String>): String {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        check(ffmpeg.exists()) { "FFmpeg 组件不存在" }
        val command = mutableListOf(ffmpeg.absolutePath, "-y", "-nostdin", "-hide_banner")
        command.addAll(arguments)
        val process = ProcessBuilder(command).redirectErrorStream(true).apply {
            environment()["LD_LIBRARY_PATH"] = listOf(
                nativeDir.absolutePath,
                File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib").absolutePath
            ).joinToString(":")
        }.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        if (code != 0) error(output.takeLast(1500).ifBlank { "FFmpeg 退出代码 $code" })
        return output
    }

    fun mimeType(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: mimeForName(uri.lastPathSegment.orEmpty())

    private fun publishLegacy(
        context: Context,
        source: File,
        displayName: String,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ): Uri {
        val publicDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "解析")
        val fallbackDirectory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "解析"
        )
        val publicWritable = runCatching {
            (publicDirectory.mkdirs() || publicDirectory.isDirectory) && publicDirectory.canWrite()
        }.getOrDefault(false)
        check(fallbackDirectory.mkdirs() || fallbackDirectory.isDirectory) { "无法创建下载目录" }
        val directories = if (publicWritable) listOf(publicDirectory, fallbackDirectory) else listOf(fallbackDirectory)
        var lastError: Throwable? = null
        directories.forEach { directory ->
            val target = availableFile(directory, displayName)
            try {
                val total = source.length().coerceAtLeast(0L)
                source.inputStream().buffered(1024 * 1024).use { input ->
                    FileOutputStream(target).buffered(1024 * 1024).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var copied = 0L
                        var lastUpdate = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= 350L || copied == total) {
                                onProgress(copied, total)
                                lastUpdate = now
                            }
                        }
                    }
                }
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
            } catch (error: Throwable) {
                target.delete()
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("无法保存输出文件")
    }

    private fun sanitizeDisplayName(name: String, fallbackExtension: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(180)
        if (cleaned.isNotBlank()) return cleaned
        val extension = fallbackExtension.trim('.').takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) }
        return "解析文件-${System.currentTimeMillis()}${extension?.let { ".$it" }.orEmpty()}"
    }

    private fun availableFile(directory: File, displayName: String): File {
        var candidate = File(directory, displayName)
        if (!candidate.exists()) return candidate
        val extension = displayName.substringAfterLast('.', "").takeIf { '.' in displayName }
        val base = if (extension == null) displayName else displayName.removeSuffix(".$extension")
        var index = 1
        while (candidate.exists()) {
            val name = "$base ($index)${extension?.let { ".$it" }.orEmpty()}"
            candidate = File(directory, name)
            index++
        }
        return candidate
    }

    private fun mimeForName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "flac" -> "audio/flac"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }
    }
}
