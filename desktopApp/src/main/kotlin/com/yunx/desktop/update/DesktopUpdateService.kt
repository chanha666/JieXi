package com.yunx.desktop.update

import com.yunx.desktop.util.DesktopLog
import com.yunx.desktop.core.DesktopBuildInfo
import com.yunx.app.data.network.NetworkProxyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Protocol
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit

data class DesktopUpdateAsset(val name: String, val url: String, val sha256: String)
data class DesktopRelease(val version: String, val notes: String, val publishedAt: String, val assets: List<DesktopUpdateAsset>)

class DesktopUpdateService(
    private val client: OkHttpClient? = null,
    private val proxyConfig: () -> NetworkProxyConfig = { NetworkProxyConfig() }
) {
    private val config = Properties().apply {
        DesktopUpdateService::class.java.classLoader.getResourceAsStream("update.properties")?.use(::load)
    }

    val configured: Boolean get() = trustedHttps(config.getProperty("manifestUrl", "")) &&
        config.getProperty("publicKeyBase64", "").isNotBlank()
    val defaultFeedbackUrl: String? get() = config.getProperty("githubFeedbackUrl", "")
        .trim().takeIf(::trustedGitHubIssuesUrl)

    private fun networkClient(download: Boolean): OkHttpClient =
        (client?.newBuilder() ?: OkHttpClient.Builder().proxySelector(DesktopUpdateProxySelector(proxyConfig())))
            .connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(if (download) 900 else 35, TimeUnit.SECONDS)
            // Some Windows local proxies stall GitHub release bodies over HTTP/2.
            // Update traffic favors predictable streaming over multiplexing.
            .protocols(listOf(Protocol.HTTP_1_1))
            .followSslRedirects(false).build()

    suspend fun check(): DesktopRelease? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val request = Request.Builder().url(config.getProperty("manifestUrl"))
            .header("User-Agent", "JieXi-Desktop/${DesktopBuildInfo.version}").header("Cache-Control", "no-cache").build()
        networkClient(false).newCall(request).execute().use { response ->
            require(response.isSuccessful) { "更新服务返回 HTTP ${response.code}" }
            parseSignedEnvelope(JSONObject(response.body?.string().orEmpty()), config.getProperty("publicKeyBase64"))
        }
    }

    suspend fun download(asset: DesktopUpdateAsset, outputDirectory: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        require(trustedHttps(asset.url)) { "更新下载地址不安全" }
        require(asset.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "更新包缺少 SHA-256 校验值" }
        outputDirectory.mkdirs()
        require(outputDirectory.isDirectory && outputDirectory.canWrite()) { "更新目录不可写" }
        val finalFile = File(outputDirectory, safeFileName(asset.name))
        val temporary = File(outputDirectory, "${finalFile.name}.part")
        require(!Files.isSymbolicLink(finalFile.toPath()) && !Files.isSymbolicLink(temporary.toPath())) { "更新文件路径不安全" }
        if (finalFile.isFile && sha256(finalFile).equals(asset.sha256, true)) {
            onProgress(finalFile.length(), finalFile.length())
            return@withContext finalFile
        }
        if (temporary.isFile && temporary.length() > 0 && sha256(temporary).equals(asset.sha256, true)) {
            Files.move(temporary.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            onProgress(finalFile.length(), finalFile.length())
            return@withContext finalFile
        }
        val downloadClient = networkClient(true)
        var received = false
        var failure: IOException? = null
        for (attempt in 0..2) {
            try {
                val offset = temporary.takeIf(File::isFile)?.length() ?: 0L
                val builder = Request.Builder().url(asset.url)
                    .header("User-Agent", "JieXi-Desktop/${DesktopBuildInfo.version}")
                    .header("Accept-Encoding", "identity")
                if (offset > 0) builder.header("Range", "bytes=$offset-")
                downloadClient.newCall(builder.build()).execute().use { response ->
                    if (response.code == 416 && offset > 0) {
                        Files.deleteIfExists(temporary.toPath())
                        throw IOException("更新断点已失效，正在重新获取")
                    }
                    require(response.code == 200 || response.code == 206) { "下载更新失败：HTTP ${response.code}" }
                    val append = response.code == 206
                    if (append) require(response.header("Content-Range").orEmpty().startsWith("bytes $offset-")) { "更新服务器返回了错误的断点范围" }
                    val body = response.body ?: error("更新包为空")
                    val start = if (append) offset else 0L
                    val total = body.contentLength().takeIf { it >= 0 }?.plus(start) ?: -1L
                    var downloaded = start
                    var lastNotice = 0L
                    onProgress(downloaded, total)
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(temporary, append).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                val now = System.nanoTime()
                                if (now - lastNotice > 250_000_000L) { onProgress(downloaded, total); lastNotice = now }
                            }
                        }
                    }
                    if (total >= 0 && downloaded != total) throw IOException("更新下载中断，已保留断点")
                    require(downloaded > 0) { "更新包为空" }
                    onProgress(downloaded, total)
                }
                received = true
                break
            } catch (error: IOException) { failure = error }
        }
        if (!received) throw IOException("更新连接失败，请检查系统代理或软件网络设置；已保留下载断点。", failure)
        val actual = sha256(temporary)
        require(actual.equals(asset.sha256, true)) { temporary.delete(); "更新包校验失败，文件已删除" }
        Files.move(temporary.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        DesktopLog.d("Update", "verified update package ${finalFile.name}")
        finalFile
    }

    internal fun parseSignedEnvelope(envelope: JSONObject, publicKeyBase64: String): DesktopRelease {
        val payload = Base64.getDecoder().decode(envelope.getString("payload"))
        val signature = Base64.getDecoder().decode(envelope.getString("signature"))
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
        require(Signature.getInstance("SHA256withRSA").run { initVerify(key); update(payload); verify(signature) }) {
            "更新清单签名无效"
        }
        val json = JSONObject(String(payload, Charsets.UTF_8))
        val assets = buildList {
            json.optJSONArray("assets")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    if (trustedHttps(url)) add(DesktopUpdateAsset(item.optString("name"), url, item.optString("sha256")))
                }
            }
        }
        return DesktopRelease(json.getString("tag_name"), json.optString("body"), json.optString("published_at"), assets)
    }

    companion object {
        internal fun verifiedFile(asset: DesktopUpdateAsset, file: File): Boolean =
            file.isFile && !Files.isSymbolicLink(file.toPath()) && sha256(file).equals(asset.sha256, true)

        fun compareVersions(left: String, right: String): Int {
            val a = left.trimStart('v').split('.')
            val b = right.trimStart('v').split('.')
            for (i in 0 until maxOf(a.size, b.size)) {
                val delta = (a.getOrNull(i)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0) -
                    (b.getOrNull(i)?.takeWhile(Char::isDigit)?.toIntOrNull() ?: 0)
                if (delta != 0) return delta
            }
            return 0
        }

        fun windowsInstaller(release: DesktopRelease): DesktopUpdateAsset? = release.assets.firstOrNull {
            it.name.endsWith(".exe", true) && (it.name.contains("安装") || it.name.contains("setup", true))
        } ?: release.assets.firstOrNull { it.name.endsWith(".exe", true) }

        private fun trustedHttps(value: String): Boolean = runCatching {
            val uri = URI(value); uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)

        private fun trustedGitHubIssuesUrl(value: String): Boolean = runCatching {
            val uri = URI(value); uri.scheme == "https" && uri.host.equals("github.com", true) && uri.path.contains("/issues")
        }.getOrDefault(false)

        private fun safeFileName(value: String): String = value.substringAfterLast('/').replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .takeIf(String::isNotBlank) ?: "解析-update.exe"

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
