package com.fuke.mobile

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in foreground-service transport checks, only in the isolated QA package. */
internal object MediaServiceSmoke {
    fun run(instrumentation: Instrumentation): String {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa")) { "Service smoke must not touch release data" }
        TaskStore.initialize(context)
        check(TaskStore.tasks.value.none { it.status in setOf(TaskStatus.RUNNING, TaskStatus.QUEUED) })
        val activity = instrumentation.startActivitySync(
            Intent(context, MediaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val sample = instrumentation.context.assets.open("sample.mp4").use { it.readBytes() }
        val large = ByteArray(9 * 1024 * 1024) { (it % 251).toByte() }
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val fullGets = ConcurrentHashMap<String, AtomicInteger>()
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val workers = Executors.newCachedThreadPool()
        val tasks = mutableListOf<DownloadTask>()
        workers.submit {
            while (!socket.isClosed) runCatching {
                val client = socket.accept()
                workers.submit {
                    client.use {
                        val reader = it.getInputStream().bufferedReader()
                        val first = reader.readLine().orEmpty()
                        val path = first.split(' ').getOrNull(1).orEmpty()
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = reader.readLine().orEmpty()
                            if (line.isBlank()) break
                            headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                        }
                        val count = counts.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                        val range = headers["range"]
                        val payload = if (path == "/range.mp4" || path == "/segmented.mp4") large else sample
                        val html = path == "/html.mp4"
                        val failure = if (path == "/retry.mp4" && count == 1) 503 else if (path == "/forbidden.mp4") 403 else 0
                        val acceptedRange = range != null && (range == "bytes=0-0" || path == "/segmented.mp4")
                        val interval = if (acceptedRange) {
                            val values = range!!.removePrefix("bytes=").split('-')
                            values[0].toInt()..(values[1].toIntOrNull() ?: payload.lastIndex)
                        } else null
                        val body = when {
                            failure > 0 -> byteArrayOf()
                            html -> "<html>Not a video</html>".toByteArray()
                            interval != null -> payload.copyOfRange(interval.first, interval.last + 1)
                            else -> payload
                        }
                        if (range == null) fullGets.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
                        val status = if (failure > 0) failure else if (interval != null && !html) 206 else 200
                        val output = it.getOutputStream()
                        val responseHeaders = buildString {
                            append("HTTP/1.1 $status Test\r\nContent-Length: ${body.size}\r\n")
                            append("Content-Type: ${if (html) "text/html" else "video/mp4"}\r\nConnection: close\r\n")
                            if (interval != null && !html && failure == 0) append("Content-Range: bytes ${interval.first}-${interval.last}/${payload.size}\r\n")
                            append("\r\n")
                        }
                        runCatching { output.write(responseHeaders.toByteArray()); output.write(body); output.flush() }
                    }
                }
            }
        }
        try {
            for (path in listOf("/retry.mp4", "/range.mp4", "/segmented.mp4", "/html.mp4", "/forbidden.mp4")) {
                val url = "http://127.0.0.1:${socket.localPort}$path"
                val task = DownloadTask(url = url, resolvedUrl = url, platform = "直链视频",
                    title = "JieXi QA ${System.nanoTime()}", formatSelector = "best")
                tasks += task
                check(TaskStore.add(task))
                instrumentation.runOnMainSync { DownloadService.start(context) }
                val deadline = System.currentTimeMillis() + 45_000
                var actual: DownloadTask
                while (true) {
                    actual = checkNotNull(TaskStore.get(task.id))
                    if (actual.status in setOf(TaskStatus.COMPLETED, TaskStatus.FAILED)) break
                    check(System.currentTimeMillis() < deadline) { "Service timeout: $path ${actual.stage}" }
                    Thread.sleep(50)
                }
                if (path == "/html.mp4" || path == "/forbidden.mp4") {
                    check(actual.status == TaskStatus.FAILED && actual.fileUri.isBlank()) { "Invalid payload was published: $path" }
                    check(counts[path]?.get() == 1) { "Permanent failure retried: $path" }
                } else {
                    check(actual.status == TaskStatus.COMPLETED) { "Service failed: $path ${actual.error}" }
                    val expected = if (path == "/range.mp4" || path == "/segmented.mp4") large else sample
                    val downloaded = context.contentResolver.openInputStream(Uri.parse(actual.fileUri))!!.use { it.readBytes() }
                    check(downloaded.contentEquals(expected)) { "Service output differs: $path" }
                    if (path == "/range.mp4") check(fullGets[path]?.get() == 1) { "Missing single-connection fallback" }
                    if (path == "/segmented.mp4") check((counts[path]?.get() ?: 0) >= 5 && fullGets[path] == null) { "Segmentation not exercised" }
                    if (path == "/retry.mp4") check((counts[path]?.get() ?: 0) >= 3) { "Probe retry not exercised" }
                }
            }
            return "PASS: probe retry, Range fallback, segmented merge, HTML rejection, permanent HTTP failure; byte-exact MediaStore outputs"
        } finally {
            context.stopService(Intent(context, DownloadService::class.java))
            tasks.forEach { task ->
                TaskStore.get(task.id)?.fileUri?.takeIf(String::isNotBlank)?.let {
                    context.contentResolver.delete(Uri.parse(it), null, null)
                }
                TaskStore.remove(task.id)
                MediaTaskArtifacts.cleanup(context, task.id)
            }
            socket.close()
            workers.shutdownNow()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
