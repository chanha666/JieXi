package com.fuke.mobile

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Dependency-free, explicit device smoke runner; installed in the isolated .qa package. */
class MediaSmokeInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        val results = Bundle()
        val root = File(targetContext.cacheDir, "media-smoke-${System.currentTimeMillis()}").apply { mkdirs() }
        val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = Executors.newSingleThreadExecutor()
        try {
            val sample = context.assets.open("sample.mp4").use { it.readBytes() }
            worker.submit {
                while (!server.isClosed) runCatching {
                    server.accept().use { socket ->
                        val reader = socket.getInputStream().bufferedReader()
                        val first = reader.readLine().orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${sample.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        if (!first.startsWith("HEAD")) output.write(sample)
                        output.flush()
                    }
                }
            }
            Engine.initializeDownloadTools(targetContext)
            val url = "http://127.0.0.1:${server.localPort}/sample.mp4"
            val info = Engine.getInfoBounded(YoutubeDLRequest(url).apply {
                addOption("--no-playlist"); addOption("--skip-download"); addOption("--proxy", "")
            })
            check(!info.title.isNullOrBlank()) { "Metadata is empty" }
            results.putString("metadata", "PASS: bounded extractor and real JSON mapper")
            val progress = AtomicInteger()
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist"); addOption("--progress"); addOption("--newline")
                addOption("--socket-timeout", 10); addOption("--retries", 0); addOption("--proxy", "")
                addOption("-o", File(root, "sample.mp4").absolutePath)
            }
            YoutubeDL.getInstance().execute(request, "media-smoke-download") { _, _, _ -> progress.incrementAndGet() }
            val actual = MediaTaskArtifacts.completedOutput(root, "mp4")
            check(actual.readBytes().contentEquals(sample)) { "Downloaded bytes differ" }
            check(progress.get() > 0) { "No progress callbacks" }
            results.putString("download", "PASS: ${actual.length()} bytes, ${progress.get()} callbacks")
            if (arguments.getString("live") == "1") {
                val live = Engine.getInfoBounded(YoutubeDLRequest("https://www.youtube.com/watch?v=JXZ_CUfTweo").apply {
                    addOption("--skip-download"); addOption("--no-playlist"); addOption("--socket-timeout", 12)
                    addOption("--extractor-retries", 0); addOption("--retries", 0)
                })
                check(live.formats.orEmpty().any { it.height > 0 }) { "No real YouTube formats" }
                results.putString("youtube", "PASS: ${live.title}; height=${live.formats.orEmpty().maxOfOrNull { it.height }}")
            }
            results.putString("stream", "MEDIA_SMOKE_OK\n$results")
            finish(Activity.RESULT_OK, results)
        } catch (error: Throwable) {
            results.putString("stream", "MEDIA_SMOKE_FAILED: ${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED, results)
        } finally {
            server.close(); worker.shutdownNow(); root.deleteRecursively()
        }
    }
}
