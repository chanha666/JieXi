package com.yunx.desktop.browser

import com.yunx.desktop.security.CredentialKey
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID

/**
 * Opens a native WebView2 window owned by the application and imports all
 * cookies, including HttpOnly cookies, through WebView2's CookieManager.
 */
class EmbeddedLoginImporter(
    private val cookieImporter: ChromiumCookieImporter = ChromiumCookieImporter()
) {
    fun loginAndImport(key: CredentialKey, loginUrl: String): String {
        val helper = findHelper()
        val local = File(
            System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
            "解析/embedded-login"
        ).apply { mkdirs() }
        local.listFiles { file -> file.name.startsWith("cookies-") && file.extension == "json" }
            ?.forEach { stale ->
                check(deleteSensitiveFile(stale)) { "旧的登录授权临时文件无法清理，请关闭占用它的程序后重试" }
            }
        val output = File(local, "cookies-${key.name.lowercase()}-${UUID.randomUUID()}.json")
        val profile = File(local, "profile-${key.name.lowercase()}").apply { mkdirs() }
        try {
            val process = ProcessBuilder(
                helper.absolutePath,
                "--url", loginUrl,
                "--output", output.absolutePath,
                "--profile", profile.absolutePath,
                "--allowed-hosts", allowedHosts(key).joinToString(",")
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(20, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                error("登录窗口等待超时，请重新打开")
            }
            if (process.exitValue() != 0 || !output.isFile) error("登录已取消，未导入授权")
            return cookieHeaderFromJson(key, output.readText(Charsets.UTF_8))
        } finally {
            check(deleteSensitiveFile(output)) { "登录授权已导入，但临时文件无法安全删除，请立即关闭软件后重试" }
        }
    }

    internal fun cookieHeaderFromJson(key: CredentialKey, json: String): String {
        val array = JSONArray(json)
        val cookies = List(array.length()) { array.getJSONObject(it) }
        return cookieImporter.cookieHeaderFor(key, cookies)
    }

    internal fun allowedHosts(key: CredentialKey): Set<String> = when (key) {
        CredentialKey.QUARK_COOKIE -> setOf("quark.cn")
        CredentialKey.UC_COOKIE -> setOf("uc.cn")
        CredentialKey.BAIDU_COOKIE -> setOf("baidu.com")
        CredentialKey.C139_COOKIE -> setOf("139.com", "10086.cn", "cmpassport.com")
        else -> emptySet()
    }

    internal fun deleteSensitiveFile(file: File): Boolean {
        if (!file.exists()) return true
        if (!file.isFile) return false
        runCatching {
            file.outputStream().use { stream ->
                val zeros = ByteArray(8192)
                var remaining = file.length()
                while (remaining > 0) {
                    val count = minOf(remaining, zeros.size.toLong()).toInt()
                    stream.write(zeros, 0, count)
                    remaining -= count
                }
                stream.flush()
            }
        }
        return runCatching { file.delete() && !file.exists() }.getOrDefault(false)
    }

    private fun findHelper(): File {
        val launcher = ProcessHandle.current().info().command().orElse(null)?.let(::File)
        val candidates = listOfNotNull(
            launcher?.parentFile?.resolve("app/browser-helper/解析登录.exe"),
            System.getProperty("jpackage.app-path")?.let(::File)?.parentFile?.resolve("app/browser-helper/解析登录.exe"),
            File("desktopApp/browserHelper/publish-net48-x64/解析登录.exe").absoluteFile
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("内置登录组件缺失，请重新安装完整版")
    }
}
