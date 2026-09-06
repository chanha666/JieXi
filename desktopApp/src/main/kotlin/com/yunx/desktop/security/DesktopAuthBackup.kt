package com.yunx.desktop.security

import com.yunx.app.data.network.XunleiDeviceFingerprint
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Wire-compatible with Android YUNX_AUTH_V2. Plain credentials never go to a file. */
object DesktopAuthBackup {
    private val fields = mapOf(
        "quark" to mapOf("cookie" to CredentialKey.QUARK_COOKIE),
        "uc" to mapOf("cookie" to CredentialKey.UC_COOKIE),
        "baidu" to mapOf("cookie" to CredentialKey.BAIDU_COOKIE),
        "c139" to mapOf("cookie" to CredentialKey.C139_COOKIE),
        "pan123" to mapOf("accessToken" to CredentialKey.PAN123_TOKEN),
        "xunlei" to mapOf("accessToken" to CredentialKey.XUNLEI_ACCESS_TOKEN,
            "refreshToken" to CredentialKey.XUNLEI_REFRESH_TOKEN,
            "captchaToken" to CredentialKey.XUNLEI_CAPTCHA_TOKEN,
            "deviceId" to CredentialKey.XUNLEI_DEVICE_ID)
    )
    fun export(store: CredentialStore, password: String): String {
        val values = CredentialKey.entries.mapNotNull { key -> store.get(key)?.let { key to it } }.toMap().toMutableMap()
        if(values.containsKey(CredentialKey.XUNLEI_ACCESS_TOKEN)) values.putIfAbsent(CredentialKey.XUNLEI_DEVICE_ID, XunleiDeviceFingerprint.deviceId())
        return encode(values, password)
    }
    fun import(store: CredentialStore, content: String, password: String): Int {
        val values = decode(content, password) // Validate and decrypt the entire document before any writes.
        val previous = values.keys.associateWith { store.get(it) }
        try { values.forEach { (k,v) -> store.put(k,v) } }
        catch(e: Exception) {
            previous.forEach { (k,v) -> if(v == null) store.remove(k) else store.put(k,v) }
            throw e
        }
        return fields.values.count { mapping -> mapping.values.any { it in values } }
    }
    fun encode(values: Map<CredentialKey,String>, password: String): String {
        require(password.length >= 8) { "备份密码至少 8 位" }
        val accounts = JSONArray()
        fields.forEach { (platform,mapping) ->
            if(mapping.values.any { !values[it].isNullOrBlank() }) {
                accounts.put(JSONObject().put("platform",platform).put("nickname","").put("updatedAt",System.currentTimeMillis()).apply {
                    mapping.forEach { (field,key) -> put(field,values[key].orEmpty()) }
                })
            }
        }
        require(accounts.length() > 0) { "没有可导出的网盘账号" }
        val plain = JSONObject().put("app","yunx_auth_backup").put("version",1).put("accounts",accounts).toString()
        return encrypt(plain,password)
    }
    fun decode(content: String, password: String): Map<CredentialKey,String> {
        require(content.length <= 2_000_000) { "备份文件过大" }
        val json = JSONObject(decrypt(content,password))
        require(json.optString("app") == "yunx_auth_backup" && json.optInt("version") == 1) { "不是受支持的认证备份" }
        val accounts = json.getJSONArray("accounts")
        require(accounts.length() <= 6) { "备份账号数异常" }
        val result = mutableMapOf<CredentialKey,String>()
        val seen = mutableSetOf<String>()
        for(i in 0 until accounts.length()) {
            val account = accounts.getJSONObject(i)
            val platform = account.getString("platform")
            require(seen.add(platform)) { "备份中存在重复平台" }
            val mapping = fields[platform] ?: error("备份包含未知平台")
            mapping.forEach { (field,key) ->
                var value = account.optString(field)
                if(platform == "c139" && field == "cookie" && account.optString("authorization").isNotBlank() && !value.contains("authorization="))
                    value += "; authorization=" + account.getString("authorization")
                require(value.length <= 64_000) { "认证字段过大" }
                if(value.isNotBlank()) result[key] = value
            }
        }
        require(result.isNotEmpty()) { "备份没有有效账号" }
        return result
    }
    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(),salt,iterations,256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
        finally { spec.clearPassword() }
    }
    internal fun encrypt(plain: String, password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt,210000),GCMParameterSpec(128,iv))
        return Base64.getEncoder().encodeToString("YUNX_AUTH_V2".toByteArray() + salt + iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }
    private fun decrypt(content: String, password: String): String {
        val bytes = Base64.getDecoder().decode(content.trim())
        require(bytes.size >= 55) { "备份文件已损坏" }
        val magic = String(bytes.copyOfRange(0,12),Charsets.US_ASCII)
        val iterations = when(magic) { "YUNX_AUTH_V2" -> 210000; "YUNX_AUTH_V1" -> 10000; else -> error("不是加密备份文件") }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password,bytes.copyOfRange(12,28),iterations),GCMParameterSpec(128,bytes.copyOfRange(28,40)))
        return String(cipher.doFinal(bytes.copyOfRange(40,bytes.size)),Charsets.UTF_8)
    }
}
