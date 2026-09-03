package com.yunx.desktop.system

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/** 防止同一 Windows 用户重复启动，避免两个进程同时写任务状态。 */
class SingleInstanceGuard private constructor(
    private val file: RandomAccessFile,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { file.close() }
    }

    companion object {
        fun acquire(rootOverride: File? = null): SingleInstanceGuard? {
            val root = rootOverride ?: File(
                System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
                "解析"
            ).apply { mkdirs() }
            root.mkdirs()
            val file = RandomAccessFile(File(root, "app.lock"), "rw")
            val lock = runCatching { file.channel.tryLock() }.getOrNull()
            if (lock == null) {
                file.close()
                return null
            }
            file.setLength(0)
            file.writeBytes(ProcessHandle.current().pid().toString())
            return SingleInstanceGuard(file, lock)
        }
    }
}
