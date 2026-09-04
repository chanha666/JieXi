package com.yunx.desktop.core

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Persistent user data must not live beneath the per-user install directory. */
internal object DesktopDataPaths {
    private const val DATA_RELATIVE_PATH = "JieXi/Data"
    private const val LEGACY_RELATIVE_PATH = "解析"
    private val MIGRATABLE_FILES = setOf("state-v3.bin", "state-v3.bin.bak", "media-tasks-v1.json")

    fun localAppDataRoot(): File = File(
        System.getenv("LOCALAPPDATA")
            ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath
    ).absoluteFile.normalize()

    fun dataDirectory(localAppData: File = localAppDataRoot()): File =
        File(localAppData.absoluteFile.normalize(), DATA_RELATIVE_PATH).normalize()

    fun legacyDirectory(localAppData: File = localAppDataRoot()): File =
        File(localAppData.absoluteFile.normalize(), LEGACY_RELATIVE_PATH).normalize()

    fun stateFile(localAppData: File = localAppDataRoot()): File {
        val state = migrateLegacyFile(localAppData, "state-v3.bin")
        migrateLegacyFile(localAppData, "state-v3.bin.bak")
        return state
    }

    fun mediaTasksFile(localAppData: File = localAppDataRoot()): File =
        migrateLegacyFile(localAppData, "media-tasks-v1.json")

    /**
     * Copies once and never replaces a new-store file. Keeping the legacy copy
     * makes installer/application migrations independently recoverable.
     */
    internal fun migrateLegacyFile(localAppData: File, fileName: String): File {
        require(fileName in MIGRATABLE_FILES) { "数据文件名无效。" }
        val target = File(dataDirectory(localAppData), fileName)
        if (target.exists()) return target
        val legacy = File(legacyDirectory(localAppData), fileName)
        if (!legacy.isFile) return target

        runCatching {
            target.parentFile.mkdirs()
            val temporary = Files.createTempFile(target.parentFile.toPath(), ".${target.name}.migrate-", ".tmp")
            try {
                Files.copy(legacy.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                try {
                    // No REPLACE_EXISTING: a concurrent/new store always wins.
                    Files.move(temporary, target.toPath())
                } catch (_: FileAlreadyExistsException) {
                    // Another process completed the same one-time migration.
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        return target
    }
}
