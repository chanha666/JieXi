package com.yunx.app.data.network

import java.security.MessageDigest
import java.util.prefs.Preferences
import kotlin.random.Random

/** Windows/JVM version of the persistent Xunlei device fingerprint. */
object XunleiDeviceFingerprint {
    private const val KEY_ID = "device_id"
    private const val KEY_PEER = "peer_id"
    private const val KEY_SIGN = "device_sign"
    private const val PACKAGE_NAME = "com.xunlei.downloadprovider"
    private const val APPID = "40"
    private const val APP_KEY = "34a062aaa22f906fca4fefe9fb3a3021"
    private const val HEX = "0123456789abcdef"

    private val prefs = Preferences.userRoot().node("com/yunx/desktop/xunlei")
    private val deviceIdValue: String by lazy {
        prefs.get(KEY_ID, null) ?: randomHex(32).also { id ->
            prefs.put(KEY_ID, id)
            prefs.put(KEY_PEER, randomHex(32))
            prefs.put(KEY_SIGN, buildDeviceSign(id))
            prefs.flush()
        }
    }

    fun init() { deviceIdValue }
    fun deviceId(): String = deviceIdValue
    fun peerId(): String = prefs.get(KEY_PEER, XunleiConstants.PEER_ID)
    fun deviceSign(): String = prefs.get(KEY_SIGN, buildDeviceSign(deviceIdValue))

    private fun buildDeviceSign(id: String): String {
        val sha1 = digest("SHA-1", id + PACKAGE_NAME + APPID + APP_KEY)
        return "div101.$id${digest("MD5", sha1)}"
    }

    private fun randomHex(length: Int): String = buildString {
        repeat(length) { append(HEX[Random.nextInt(HEX.length)]) }
    }

    private fun digest(algorithm: String, input: String): String =
        MessageDigest.getInstance(algorithm).digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
