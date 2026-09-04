package com.fuke.mobile

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TaskStore {
    private const val PREFS = "jiexi_media_tasks_v2"
    private const val LEGACY_PREFS = "fuke_tasks_v1"
    private const val KEY_TASKS = "tasks"
    private val gson = Gson()
    private lateinit var preferences: SharedPreferences
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()
    private var lastPersistAt = 0L

    @Synchronized
    fun initialize(appContext: Context) {
        if (::preferences.isInitialized) return
        val context = appContext.applicationContext
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = preferences.getString(KEY_TASKS, null)
            ?: legacy.getString(KEY_TASKS, "[]")
            ?: "[]"
        val type = object : TypeToken<List<DownloadTask>>() {}.type
        val now = System.currentTimeMillis()
        val restored = runCatching { gson.fromJson<List<DownloadTask>>(raw, type) }
            .getOrNull()
            .orEmpty()
            .mapNotNull { task -> runCatching { MediaTaskPolicy.restore(task, now) }.getOrNull() }
            .filter { it.url.isNotBlank() }
        _tasks.value = restored.takeLast(MAX_TASKS)
        persist(force = true)
        if (legacy.contains(KEY_TASKS)) legacy.edit().remove(KEY_TASKS).apply()
    }

    @Synchronized
    fun add(task: DownloadTask): Boolean {
        if (task.url.isBlank() || MediaTaskPolicy.hasActiveDuplicate(_tasks.value, task.url)) return false
        _tasks.value = (_tasks.value + MediaTaskPolicy.restore(task, System.currentTimeMillis())).takeLast(MAX_TASKS)
        persist(force = true)
        return true
    }

    @Synchronized
    fun addAll(newTasks: List<DownloadTask>): Int {
        if (newTasks.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val result = _tasks.value.toMutableList()
        var count = 0
        newTasks.forEach { task ->
            if (task.url.isNotBlank() && !MediaTaskPolicy.hasActiveDuplicate(result, task.url)) {
                result += MediaTaskPolicy.restore(task, now)
                count++
            }
        }
        if (count > 0) {
            _tasks.value = result.takeLast(MAX_TASKS)
            persist(force = true)
        }
        return count
    }

    @Synchronized
    fun update(id: String, block: (DownloadTask) -> DownloadTask) {
        var forcePersist = false
        var changed = false
        val now = System.currentTimeMillis()
        _tasks.value = _tasks.value.map { previous ->
            if (previous.id != id) return@map previous
            val updated = MediaTaskPolicy.restore(block(previous).copy(updatedAt = now), now, recoverRunning = false)
            changed = updated != previous
            forcePersist = previous.status != updated.status || updated.status !in ACTIVE_STATUSES
            updated
        }
        if (changed) persist(force = forcePersist)
    }

    fun get(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }
    fun nextQueued(): DownloadTask? = _tasks.value.firstOrNull { it.status == TaskStatus.QUEUED }
    fun hasQueued(): Boolean = _tasks.value.any { it.status == TaskStatus.QUEUED }

    /** Atomically reserves a queued task so the UI cannot remove it while the service starts it. */
    @Synchronized
    fun claimNextQueued(): DownloadTask? {
        val task = _tasks.value.firstOrNull { it.status == TaskStatus.QUEUED } ?: return null
        val now = System.currentTimeMillis()
        val claimed = task.copy(status = TaskStatus.RUNNING, stage = "正在连接", speed = "", error = "", updatedAt = now)
        _tasks.value = _tasks.value.map { if (it.id == task.id) claimed else it }
        persist(force = true)
        return claimed
    }

    @Synchronized
    fun queueForRetry(id: String): Boolean {
        val task = get(id) ?: return false
        if (task.status !in setOf(TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.CANCELED)) return false
        update(id) { it.copy(status = TaskStatus.QUEUED, stage = "等待中", speed = "", eta = "", error = "") }
        return true
    }

    @Synchronized
    fun pauseIfQueued(id: String): Boolean {
        val task = get(id) ?: return false
        if (task.status != TaskStatus.QUEUED) return false
        update(id) { it.copy(status = TaskStatus.PAUSED, stage = "已暂停", speed = "", error = "任务已暂停，点击继续可断点续传。") }
        return true
    }

    @Synchronized
    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist(force = true)
    }

    @Synchronized
    fun clearFinished() {
        _tasks.value = _tasks.value.filterNot { it.status in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELED) }
        persist(force = true)
    }

    @Synchronized
    fun markInterrupted(id: String, message: String = "下载服务已停止，点击继续可断点续传。") {
        update(id) { task ->
            if (task.status == TaskStatus.RUNNING) {
                task.copy(status = TaskStatus.PAUSED, stage = "已暂停", speed = "", error = message)
            } else task
        }
    }

    @Synchronized
    private fun persist(force: Boolean = false) {
        if (!::preferences.isInitialized) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < PROGRESS_PERSIST_INTERVAL_MS) return
        preferences.edit().putString(KEY_TASKS, gson.toJson(_tasks.value)).apply()
        lastPersistAt = now
    }

    private val ACTIVE_STATUSES = setOf(TaskStatus.QUEUED, TaskStatus.RUNNING, TaskStatus.PAUSED)
    private const val MAX_TASKS = 1000
    private const val PROGRESS_PERSIST_INTERVAL_MS = 1_000L
}

object AppPrefs {
    private const val PREFS = "jiexi_media_settings_v2"
    private const val LEGACY_PREFS = "fuke_settings_v1"

    fun read(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.all.isEmpty()) {
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                prefs.edit().apply {
                    legacy.all.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Boolean -> putBoolean(key, value)
                        }
                    }
                }.apply()
                legacy.edit().clear().apply()
            }
        }
        return AppSettings(
            defaultQuality = prefs.getString("default_quality", "best") ?: "best",
            retries = prefs.getInt("retries", 3).coerceIn(1, 10),
            wifiOnly = prefs.getBoolean("wifi_only", false),
            fileNameRule = prefs.getString("filename_rule", "title-id") ?: "title-id"
        )
    }

    fun write(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("default_quality", settings.defaultQuality)
            .putInt("retries", settings.retries)
            .putBoolean("wifi_only", settings.wifiOnly)
            .putString("filename_rule", settings.fileNameRule)
            .apply()
    }
}
