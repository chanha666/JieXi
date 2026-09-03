package com.yunx.desktop.core

import com.sun.jna.platform.win32.Crypt32Util
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

data class PersistedTask(
    val id: String,
    val fileName: String,
    val sourceLink: String,
    val platform: String,
    val fileId: String,
    val fileSize: Long,
    val parentId: String,
    val fidToken: String,
    val modifyTime: String,
    val state: String,
    val priority: Int,
    val downloaded: Long,
    val total: Long,
    val outputPath: String,
    val errorCode: String,
    val errorMessage: String,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class DesktopHistory(val id: String, val link: String, val title: String, val platform: String, val createdAt: Long)
data class DesktopFavorite(val id: String, val link: String, val title: String, val platform: String, val createdAt: Long)

class DesktopStateStore(
    private val file: File = File(
        System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
        "解析/state-v3.bin"
    )
) {
    data class State(
        val tasks: List<PersistedTask> = emptyList(),
        val history: List<DesktopHistory> = emptyList(),
        val favorites: List<DesktopFavorite> = emptyList()
    )

    @Synchronized
    fun load(): State {
        val backup = File(file.parentFile, "${file.name}.bak")
        return listOf(file, backup).firstNotNullOfOrNull { candidate -> runCatching { loadFile(candidate) }.getOrNull() } ?: State()
    }

    private fun loadFile(source: File): State {
        require(source.isFile)
        val encrypted = Base64.getDecoder().decode(source.readText(Charsets.UTF_8))
        val json = JSONObject(String(Crypt32Util.cryptUnprotectData(encrypted), StandardCharsets.UTF_8))
        return State(
            tasks = json.optJSONArray("tasks").objects().map(::parseTask),
            history = json.optJSONArray("history").objects().map(::parseHistory),
            favorites = json.optJSONArray("favorites").objects().map(::parseFavorite)
        )
    }

    @Synchronized
    fun save(state: State) {
        val json = JSONObject()
            .put("version", 3)
            .put("tasks", JSONArray(state.tasks.map(::taskJson)))
            .put("history", JSONArray(state.history.map(::historyJson)))
            .put("favorites", JSONArray(state.favorites.map(::favoriteJson)))
        val encrypted = Crypt32Util.cryptProtectData(json.toString().toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(encoded, Charsets.UTF_8)
        if (file.isFile) runCatching {
            Files.copy(file.toPath(), File(file.parentFile, "${file.name}.bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        runCatching {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        List(length()) { index -> optJSONObject(index) }.filterNotNull()

    private fun parseTask(j: JSONObject) = PersistedTask(
        j.optString("id"), j.optString("fileName"), j.optString("sourceLink"), j.optString("platform"),
        j.optString("fileId"), j.optLong("fileSize"), j.optString("parentId"), j.optString("fidToken"),
        j.optString("modifyTime"), j.optString("state"), j.optInt("priority"),
        j.optLong("downloaded"), j.optLong("total", -1), j.optString("outputPath"),
        j.optString("errorCode"), j.optString("errorMessage"), j.optInt("retryCount"), j.optLong("createdAt"), j.optLong("updatedAt")
    )

    private fun taskJson(t: PersistedTask) = JSONObject()
        .put("id", t.id).put("fileName", t.fileName).put("sourceLink", t.sourceLink).put("platform", t.platform)
        .put("fileId", t.fileId).put("fileSize", t.fileSize).put("parentId", t.parentId).put("fidToken", t.fidToken)
        .put("modifyTime", t.modifyTime).put("state", t.state).put("priority", t.priority)
        .put("downloaded", t.downloaded).put("total", t.total).put("outputPath", t.outputPath)
        .put("errorCode", t.errorCode).put("errorMessage", t.errorMessage).put("retryCount", t.retryCount)
        .put("createdAt", t.createdAt).put("updatedAt", t.updatedAt)

    private fun parseHistory(j: JSONObject) = DesktopHistory(j.optString("id"), j.optString("link"), j.optString("title"), j.optString("platform"), j.optLong("createdAt"))
    private fun historyJson(h: DesktopHistory) = JSONObject().put("id", h.id).put("link", h.link).put("title", h.title).put("platform", h.platform).put("createdAt", h.createdAt)
    private fun parseFavorite(j: JSONObject) = DesktopFavorite(j.optString("id"), j.optString("link"), j.optString("title"), j.optString("platform"), j.optLong("createdAt"))
    private fun favoriteJson(f: DesktopFavorite) = JSONObject().put("id", f.id).put("link", f.link).put("title", f.title).put("platform", f.platform).put("createdAt", f.createdAt)
}
