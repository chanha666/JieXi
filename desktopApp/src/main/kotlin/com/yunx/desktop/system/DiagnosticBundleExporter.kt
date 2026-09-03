package com.yunx.desktop.system

import com.yunx.desktop.core.DesktopDownloadTask
import com.yunx.desktop.util.DesktopLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticBundleExporter {
    private val secret = Regex("(?i)(cookie|token|authorization|password|pwd|sign|credential)\\s*[:=]\\s*[^\\s,;]+")
    private val urlQuery = Regex("(https?://[^\\s?]+)\\?[^\\s]+", RegexOption.IGNORE_CASE)

    fun export(destination: File, appVersion: String, tasks: List<DesktopDownloadTask>): File {
        destination.mkdirs()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault()).format(Instant.now())
        val output = File(destination, "解析-诊断包-$stamp.zip")
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            val summary = JSONObject()
                .put("generatedAt", Instant.now().toString())
                .put("appVersion", appVersion)
                .put("os", System.getProperty("os.name"))
                .put("osVersion", System.getProperty("os.version"))
                .put("java", System.getProperty("java.version"))
                .put("tasks", JSONArray(tasks.map {
                    JSONObject().put("state", it.state.name).put("platform", it.platform)
                        .put("errorCode", it.errorCode).put("retryCount", it.retryCount)
                }))
            zip.putNextEntry(ZipEntry("system.json"))
            zip.write(summary.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            DesktopLog.directory.listFiles { file -> file.isFile && file.extension.equals("log", true) }
                .orEmpty().sortedByDescending(File::lastModified).take(3).forEach { log ->
                    zip.putNextEntry(ZipEntry("logs/${log.name}"))
                    zip.write(redact(log.readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
        }
        return output
    }

    internal fun redact(value: String): String = value.replace(secret) { "${it.groupValues[1]}=[REDACTED]" }.replace(urlQuery, "$1?[REDACTED]")
}
