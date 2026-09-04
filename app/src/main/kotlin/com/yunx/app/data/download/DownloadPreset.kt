package com.yunx.app.data.download

enum class DownloadPreset(val label: String, val concurrentTasks: Int, val connections: Int) {
    STABLE("稳定", 1, 4),
    BALANCED("均衡", 2, 8),
    TURBO("极速", 3, 24),
    SINGLE_TASK_MAX("单任务满速", 1, 48),
    CUSTOM("自定义", 0, 0);

    companion object {
        fun from(value: String?): DownloadPreset = entries.firstOrNull { it.name == value } ?: SINGLE_TASK_MAX
    }
}
