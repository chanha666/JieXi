package com.fuke.mobile

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object Engine {
    private const val BUNDLED_YTDLP_VERSION = "2026.08.30.232658"
    private val _state = MutableStateFlow("正在准备下载核心")
    val state: StateFlow<String> = _state

    @Volatile
    private var ready = false

    @Volatile
    private var downloadToolsReady = false

    @Synchronized
    fun initialize(context: Context) {
        if (ready) return
        _state.value = "正在解压本机解析组件"
        val appContext = context.applicationContext
        YoutubeDL.getInstance().init(appContext)
        installBundledCore(appContext)
        ready = true
        _state.value = "解析核心已就绪"
    }

    @Synchronized
    fun initializeDownloadTools(context: Context) {
        initialize(context)
        if (downloadToolsReady) return
        _state.value = "正在准备下载与合并组件"
        val appContext = context.applicationContext
        FFmpeg.getInstance().init(appContext)
        Aria2c.getInstance().init(appContext)
        downloadToolsReady = true
        _state.value = "组件完整，可直接使用"
    }

    fun isReady(): Boolean = ready

    private fun installBundledCore(context: Context) {
        val directory = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp")
        val binary = File(directory, "yt-dlp")
        val preferences = context.getSharedPreferences("jiexi_media_engine", Context.MODE_PRIVATE)
        if (preferences.getString("bundled_version", "") != BUNDLED_YTDLP_VERSION || !binary.exists()) {
            directory.mkdirs()
            val temporary = File(directory, "yt-dlp.new")
            context.assets.open("yt-dlp-$BUNDLED_YTDLP_VERSION").use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (binary.exists() && !binary.delete()) error("无法替换旧下载核心。")
            if (!temporary.renameTo(binary)) error("无法安装新下载核心。")
            preferences.edit().putString("bundled_version", BUNDLED_YTDLP_VERSION).apply()
        }
        YoutubeDL.getInstance().init_ytdlp(context, directory)
    }
}
