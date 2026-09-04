package com.yunx.desktop.media

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Comparator
import java.util.UUID

/**
 * Owns yt-dlp's resumable files for exactly one media task.
 *
 * The UUID is parsed instead of merely sanitized so a restored/corrupt task
 * can never turn this cleanup routine into an arbitrary-path deletion primitive.
 */
internal object DesktopMediaWorkspace {
    private const val ROOT_NAME = ".jiexi-media-tasks"

    fun bindTaskRoot(task: DesktopMediaTask, currentDownloadDirectory: File): File {
        task.workspaceRoot?.let { return normalizeRoot(it) }
        return normalizeRoot(currentDownloadDirectory).also { task.workspaceRoot = it }
    }

    fun normalizeRoot(directory: File): File = directory.toPath().toAbsolutePath().normalize().toFile()

    fun taskDirectory(downloadDirectory: File, taskId: String): File {
        val canonicalId = runCatching { UUID.fromString(taskId).toString() }
            .getOrElse { throw IllegalArgumentException("媒体任务编号无效。") }
        require(canonicalId.equals(taskId, ignoreCase = true)) { "媒体任务编号无效。" }

        val root = downloadDirectory.toPath().toAbsolutePath().normalize().resolve(ROOT_NAME)
        val task = root.resolve(canonicalId).normalize()
        require(task.parent == root) { "媒体任务工作目录越界。" }
        return task.toFile()
    }

    fun requireOwnedOutput(taskDirectory: File, output: File): File {
        val root = taskDirectory.toPath().toAbsolutePath().normalize()
        val candidate = output.toPath().toAbsolutePath().normalize()
        require(candidate.startsWith(root) && candidate != root) { "媒体核心返回了工作目录之外的文件。" }
        require(Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(candidate)) {
            "媒体核心没有返回有效的成品文件。"
        }
        return candidate.toFile()
    }

    fun newestOwnedOutput(taskDirectory: File, since: Long): File? {
        val root = taskDirectory.toPath().toAbsolutePath().normalize()
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return null
        return Files.walk(root).use { paths ->
            paths.filter { path ->
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isSymbolicLink(path) &&
                    !path.fileName.toString().endsWith(".part", ignoreCase = true) &&
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis() >= since - 5_000L
            }.max(Comparator.comparingLong { path ->
                Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()
            }).orElse(null)?.toFile()
        }
    }

    /** Moves within the same download volume and never replaces user data. */
    fun moveCompletedWithoutOverwrite(source: File, taskDirectory: File, downloadDirectory: File): File {
        requireOwnedOutput(taskDirectory, source)
        val destinationRoot = downloadDirectory.toPath().toAbsolutePath().normalize()
        Files.createDirectories(destinationRoot)

        val name = source.name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val extension = dot?.let(name::substring).orEmpty()
        var index = 1
        while (true) {
            val candidateName = if (index == 1) name else "$stem (${index - 1})$extension"
            val target = destinationRoot.resolve(candidateName).normalize()
            require(target.parent == destinationRoot) { "媒体成品路径越界。" }
            try {
                return Files.move(source.toPath(), target).toFile()
            } catch (_: FileAlreadyExistsException) {
                index += 1
            }
        }
    }

    fun deleteTaskDirectory(downloadDirectory: File, taskId: String) {
        val directory = taskDirectory(downloadDirectory, taskId).toPath().toAbsolutePath().normalize()
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
        }
        val root = directory.parent
        runCatching {
            Files.newDirectoryStream(root).use { entries -> if (!entries.iterator().hasNext()) Files.deleteIfExists(root) }
        }
    }
}
