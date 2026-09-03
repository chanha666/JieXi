package com.yunx.app.data.update

import android.content.Context
import com.yunx.app.BuildConfig
import com.yunx.app.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Update discovery backed only by the author's RSA-signed manifest. */
object UpdateChecker {
    data class Asset(val name: String, val downloadUrl: String, val sha256: String = "")
    data class Release(val tagName: String, val body: String, val assets: List<Asset>, val publishedAt: String)

    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.trimStart('v').split(".")
        val parts2 = v2.trimStart('v').split(".")
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val a = parts1.getOrNull(i)?.toIntOrNull() ?: 0
            val b = parts2.getOrNull(i)?.toIntOrNull() ?: 0
            if (a != b) return a - b
        }
        return 0
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    /** Blank configuration deliberately disables update checks instead of trusting an upstream/third-party mirror. */
    suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.UPDATE_MANIFEST_URL.trim()
        val publicKey = BuildConfig.UPDATE_PUBLIC_KEY.trim()
        if (!isTrustedHttpsUrl(endpoint) || publicKey.isBlank()) return@withContext null
        runCatching {
            val request = Request.Builder().url(endpoint).header("User-Agent", "JieXi/3.0.2").get().build()
            val envelope = HttpClients.apiClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                JSONObject(response.body?.string() ?: return@runCatching null)
            }
            parseSignedManifest(envelope, publicKey)
        }.getOrNull()
    }

    internal fun parseSignedManifest(envelope: JSONObject, publicKeyBase64: String): Release? {
        val payloadBase64 = envelope.optString("payload")
        val signatureBase64 = envelope.optString("signature")
        if (payloadBase64.isBlank() || signatureBase64.isBlank()) return null
        val payload = payloadBase64.decodeBase64()?.toByteArray() ?: return null
        val keyBytes = publicKeyBase64.decodeBase64()?.toByteArray() ?: return null
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(key)
            update(payload)
            verify(signatureBase64.decodeBase64()?.toByteArray() ?: return null)
        }
        if (!valid) return null
        val json = JSONObject(payload.toString(Charsets.UTF_8))
        val tag = json.optString("tag_name").takeIf(String::isNotBlank) ?: return null
        val assets = buildList {
            json.optJSONArray("assets")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (!isTrustedHttpsUrl(url)) continue
                    add(Asset(item.optString("name"), url, item.optString("sha256")))
                }
            }
        }
        return Release(tag, json.optString("body"), assets, json.optString("published_at"))
    }

    internal fun isTrustedHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
