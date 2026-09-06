package com.yunx.desktop.security

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences

enum class CredentialKey(val storageName: String) {
    QUARK_COOKIE("quark_cookie"),
    UC_COOKIE("uc_cookie"),
    XUNLEI_ACCESS_TOKEN("xunlei_access_token"),
    XUNLEI_REFRESH_TOKEN("xunlei_refresh_token"),
    XUNLEI_CAPTCHA_TOKEN("xunlei_captcha_token"),
    XUNLEI_DEVICE_ID("xunlei_device_id"),
    BAIDU_COOKIE("baidu_cookie"),
    C139_COOKIE("c139_cookie"),
    PAN123_TOKEN("pan123_token")
}

/** Stores credentials encrypted with Windows DPAPI and scoped to the current Windows user. */
class CredentialStore(
    private val preferences: Preferences = Preferences.userRoot().node("com/yunx/desktop/credentials")
) {
    fun get(key: CredentialKey): String? {
        val encoded = preferences.get(key.storageName, null) ?: return null
        return runCatching {
            val cipherText = Base64.getDecoder().decode(encoded)
            String(Crypt32Util.cryptUnprotectData(cipherText), StandardCharsets.UTF_8)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun put(key: CredentialKey, value: String) {
        if (value.isBlank()) {
            remove(key)
            return
        }
        val cipherText = Crypt32Util.cryptProtectData(value.toByteArray(StandardCharsets.UTF_8))
        preferences.put(key.storageName, Base64.getEncoder().encodeToString(cipherText))
        preferences.flush()
    }

    fun remove(key: CredentialKey) {
        preferences.remove(key.storageName)
        preferences.flush()
    }

    fun has(key: CredentialKey): Boolean = get(key) != null
}
