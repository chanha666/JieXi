package com.yunx.desktop.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

object DesktopLog {
    private val logger = Logger.getLogger("YunXDesktop")
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val directory: File = File(
        System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
        "解析/logs"
    )

    init {
        runCatching {
            directory.mkdirs()
            logger.useParentHandlers = false
            FileHandler(File(directory, "解析-%g.log").absolutePath, 1_048_576, 3, true).apply {
                formatter = SimpleFormatter()
                logger.addHandler(this)
            }
        }
    }

    fun d(tag: String, message: String): Int = write(Level.INFO, tag, message)
    fun w(tag: String, message: String): Int = write(Level.WARNING, tag, message)
    fun e(tag: String, message: String): Int = write(Level.SEVERE, tag, message)

    private fun write(level: Level, tag: String, message: String): Int {
        // 日志只记录调用方提供的状态文本；认证信息和完整分享链接不得传入。
        logger.log(level, "${LocalDateTime.now().format(stamp)} [$tag] $message")
        return 0
    }
}
