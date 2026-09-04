package com.yunx.desktop.settings

import com.yunx.app.data.network.NetworkProxyConfig
import com.yunx.app.data.network.ProxyMode
import java.io.File
import java.util.prefs.Preferences

enum class DesktopPreset(val label: String, val concurrentTasks: Int, val connections: Int) {
    STABLE("稳定", 1, 4), BALANCED("均衡", 2, 8), TURBO("极速", 3, 24),
    SINGLE_TASK_MAX("单任务满速", 1, 48), CUSTOM("自定义", 0, 0);
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: SINGLE_TASK_MAX }
}

class DesktopSettings(
    private val preferences: Preferences = Preferences.userRoot().node("com/yunx/desktop/settings")
) {
    init {
        // 2.0 upgrades previous installations from the conservative 8/32-way
        // default to the new adaptive 64-way ceiling. This runs once and never
        // overwrites a choice the user makes afterwards.
        if (!preferences.getBoolean("adaptive_speed_v2_migrated", false)) {
            preferences.putInt("thread_count", 64)
            preferences.putBoolean("adaptive_speed_v2_migrated", true)
            preferences.flush()
        }
        if (preferences.get("download_preset", null) == null) {
            preferences.put("download_preset", DesktopPreset.SINGLE_TASK_MAX.name)
            preferences.putInt("max_concurrent_tasks", DesktopPreset.SINGLE_TASK_MAX.concurrentTasks)
            preferences.putInt("thread_count", DesktopPreset.SINGLE_TASK_MAX.connections)
            preferences.flush()
        }
        if (!preferences.getBoolean("single_task_max_v301_migrated", false)) {
            if (preferences.get("download_preset", DesktopPreset.SINGLE_TASK_MAX.name) == DesktopPreset.BALANCED.name) {
                preferences.put("download_preset", DesktopPreset.SINGLE_TASK_MAX.name)
                preferences.putInt("max_concurrent_tasks", DesktopPreset.SINGLE_TASK_MAX.concurrentTasks)
                preferences.putInt("thread_count", DesktopPreset.SINGLE_TASK_MAX.connections)
            }
            preferences.putBoolean("single_task_max_v301_migrated", true)
            preferences.flush()
        }
        if (!preferences.getBoolean("download_path_v3_migrated", false)) {
            val previous = preferences.get("download_directory", "")
            if (previous.equals("D:\\迅雷下载", ignoreCase = true)) {
                preferences.put("download_directory", "D:\\解析下载")
            }
            preferences.putBoolean("download_path_v3_migrated", true)
            preferences.flush()
        }
    }

    var downloadDirectory: File
        get() = File(preferences.get("download_directory", defaultDownloads().absolutePath))
        set(value) {
            preferences.put("download_directory", value.absolutePath)
            preferences.flush()
        }

    var threadCount: Int
        get() = preferences.getInt("thread_count", 64).coerceIn(1, 64)
        set(value) {
            preferences.putInt("thread_count", value.coerceIn(1, 64))
            preferences.flush()
        }

    var preset: DesktopPreset
        get() = DesktopPreset.from(preferences.get("download_preset", DesktopPreset.SINGLE_TASK_MAX.name))
        set(value) {
            preferences.put("download_preset", value.name)
            if (value != DesktopPreset.CUSTOM) {
                preferences.putInt("max_concurrent_tasks", value.concurrentTasks)
                preferences.putInt("thread_count", value.connections)
            }
            preferences.flush()
        }

    var maxConcurrentTasks: Int
        get() = preferences.getInt("max_concurrent_tasks", 1).coerceIn(1, 8)
        set(value) { preferences.putInt("max_concurrent_tasks", value.coerceIn(1, 8)); preferences.flush() }

    var speedLimitBytes: Long
        get() = preferences.getLong("speed_limit_bytes", 0L).coerceAtLeast(0)
        set(value) { preferences.putLong("speed_limit_bytes", value.coerceAtLeast(0)); preferences.flush() }

    var reduceMotion: Boolean
        get() = preferences.getBoolean("reduce_motion", false)
        set(value) { preferences.putBoolean("reduce_motion", value); preferences.flush() }

    var closeToTray: Boolean
        get() = preferences.getBoolean("close_to_tray", true)
        set(value) { preferences.putBoolean("close_to_tray", value); preferences.flush() }

    var notifyOnCompletion: Boolean
        get() = preferences.getBoolean("notify_on_completion", true)
        set(value) { preferences.putBoolean("notify_on_completion", value); preferences.flush() }

    var preventSleepWhileDownloading: Boolean
        get() = preferences.getBoolean("prevent_sleep", true)
        set(value) { preferences.putBoolean("prevent_sleep", value); preferences.flush() }

    var proxyMode: ProxyMode
        get() = runCatching { ProxyMode.valueOf(preferences.get("proxy_mode", ProxyMode.SYSTEM.name)) }.getOrDefault(ProxyMode.SYSTEM)
        set(value) { preferences.put("proxy_mode", value.name); preferences.flush() }

    var proxyHost: String
        get() = preferences.get("proxy_host", "").trim()
        set(value) { preferences.put("proxy_host", value.trim()); preferences.flush() }

    var proxyPort: Int
        get() = preferences.getInt("proxy_port", 7890).coerceIn(1, 65535)
        set(value) { preferences.putInt("proxy_port", value.coerceIn(1, 65535)); preferences.flush() }

    fun networkProxyConfig() = NetworkProxyConfig(proxyMode, proxyHost, proxyPort)

    var historyRetentionDays: Int
        get() = preferences.getInt("history_retention_days", 30).coerceIn(0, 365)
        set(value) { preferences.putInt("history_retention_days", value.coerceIn(0, 365)); preferences.flush() }

    /** 用户自己的 GitHub 仓库，用于问题反馈；未手动设置时使用默认仓库。 */
    var githubRepositoryUrl: String
        get() = preferences.get("github_repository_url", DEFAULT_FEEDBACK_REPO).trim()
        set(value) {
            preferences.put("github_repository_url", normalizeGitHubRepository(value))
            preferences.flush()
        }

    fun githubIssuesUrl(): String? = githubRepositoryUrl.takeIf(String::isNotBlank)?.let { "$it/issues/new" }

    private fun defaultDownloads(): File {
        val dDrive = File("D:\\解析下载")
        if ((File("D:\\").isDirectory) && (dDrive.exists() || dDrive.mkdirs()) && dDrive.canWrite()) return dDrive
        return File(System.getProperty("user.home"), "Downloads")
    }

    companion object {
        private const val DEFAULT_FEEDBACK_REPO = "https://github.com/chanha666/JieXi"
        fun normalizeGitHubRepository(value: String): String {
            val trimmed = value.trim().removeSuffix("/").removeSuffix(".git")
            if (trimmed.isBlank()) return ""
            require(Regex("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", RegexOption.IGNORE_CASE).matches(trimmed)) {
                "请输入完整 GitHub 仓库地址，例如 https://github.com/用户名/仓库名"
            }
            return trimmed
        }
    }
}
