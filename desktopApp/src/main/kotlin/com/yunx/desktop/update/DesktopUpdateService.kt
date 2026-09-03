package com.yunx.desktop.update

import com.yunx.desktop.util.DesktopLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
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
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
) {
    private val config = Properties().apply {
        DesktopUpdateService::class.java.classLoader.getResourceAsStream("update.properties")?.use(::load)
    }

    val configured: Boolean get() = trustedHttps(config.getProperty("manifestUrl", "")) &&
        config.getProperty("publicKeyBase64", "").isNotBlank()
    val defaultFeedbackUrl: String? get() = config.getProperty("githubFeedbackUrl", "")
        .trim().takeIf(::trustedGitHubIssuesUrl)

    suspend fun check(): DesktopRelease? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        val request = Request.Builder().url(config.getProperty("manifestUrl")).header("User-Agent", "JieXi-Desktop/3.0.2").build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "更新服务返回 HTTP ${response.code}" }
            parseSignedEnvelope(JSONObject(response.body?.string().orEmpty()), config.getProperty("publicKeyBase64"))
        }
    }

    suspend fun download(asset: DesktopUpdateAsset, outputDirectory: File): File = withContext(Dispatchers.IO) {
        require(trustedHttps(asset.url)) { "更新下载地址不安全" }
        require(asset.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "更新包缺少 SHA-256 校验值" }
        outputDirectory.mkdirs()
        require(outputDirectory.isDirectory && outputDirectory.canWrite()) { "更新目录不可写" }
        val finalFile = File(outputDirectory, safeFileName(asset.name))
        val temporary = File(outputDirectory, "${finalFile.name}.part")
        val request = Request.Builder().url(asset.url).header("User-Agent", "JieXi-Desktop/3.0.2").build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "下载更新失败：HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> temporary.outputStream().use(input::copyTo) }
                ?: error("更新包为空")
        }
        val actual = sha256(temporary)
        require(actual.equals(asset.sha256, true)) { temporary.delete(); "更新包校验失败，文件已删除" }
        if (finalFile.exists()) finalFile.delete()
        require(temporary.renameTo(finalFile)) { "无法保存更新包" }
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

        private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
    }
}
