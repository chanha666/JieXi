package com.yunx.desktop.browser

import com.yunx.desktop.security.CredentialKey
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class ChromiumBrowser(val displayName: String) {
    CHROME("Chrome"),
    EDGE("Edge")
}

/**
 * Imports cookies through the browser's official DevTools protocol.
 *
 * A dedicated YunX browser profile is deliberately used. Modern Chromium does
 * not allow remote debugging against the user's normal profile, and directly
 * decrypting its cookie database would defeat the browser's security model.
 */
class ChromiumCookieImporter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build(),
    private val profileRoot: File = defaultProfileRoot()
) {
    private data class Session(
        val browser: ChromiumBrowser,
        val executable: File,
        val profileDirectory: File,
        val port: Int
    )

    private val sessions = ConcurrentHashMap<ChromiumBrowser, Session>()

    fun openLogin(browser: ChromiumBrowser, loginUrl: String) {
        val executable = findExecutable(browser)
            ?: error("未找到 ${browser.displayName}，请先安装浏览器")
        val profileDirectory = profileDirectory(browser)
        val existingPort = sessions[browser]?.port
            ?.takeIf { devToolsUrl(it) != null }
            ?: activePort(profileDirectory)?.takeIf { devToolsUrl(it) != null }
        if (existingPort != null) {
            sessions[browser] = Session(browser, executable, profileDirectory, existingPort)
            ProcessBuilder(
                executable.absolutePath,
                "--user-data-dir=${profileDirectory.absolutePath}",
                "--new-window",
                loginUrl
            ).start()
            return
        }

        ProcessBuilder(
            executable.absolutePath,
            "--remote-debugging-address=127.0.0.1",
            "--remote-debugging-port=0",
            "--remote-allow-origins=*",
            "--user-data-dir=${profileDirectory.absolutePath}",
            "--no-first-run",
            "--no-default-browser-check",
            "--new-window",
            loginUrl
        ).start()

        repeat(80) {
            val port = activePort(profileDirectory)
            if (port != null && devToolsUrl(port) != null) {
                sessions[browser] = Session(browser, executable, profileDirectory, port)
                return
            }
            Thread.sleep(100)
        }
        error("无法连接 ${browser.displayName}，请关闭刚打开的窗口后重试")
    }

    fun importCookieHeader(browser: ChromiumBrowser, key: CredentialKey): String {
        val session = sessions[browser]
            ?: error("请先点击“用 ${browser.displayName} 登录”")
        val debuggerUrl = devToolsUrl(session.port)
            ?: error("${browser.displayName} 登录窗口已关闭，请重新打开")
        val cookies = requestCookies(debuggerUrl)
        return cookieHeaderFor(key, cookies)
    }

    internal fun cookieHeaderFor(key: CredentialKey, cookies: List<JSONObject>): String {
        val acceptedSuffixes = when (key) {
            CredentialKey.QUARK_COOKIE -> listOf("quark.cn")
            CredentialKey.UC_COOKIE -> listOf("uc.cn")
            CredentialKey.BAIDU_COOKIE -> listOf("baidu.com")
            CredentialKey.C139_COOKIE -> listOf("139.com")
            else -> error("该平台使用 Token，不能通过 Cookie 导入")
        }
        val nowSeconds = System.currentTimeMillis() / 1000.0
        val matching = cookies.asSequence()
            .filter { cookie ->
                val domain = cookie.optString("domain").trimStart('.').lowercase()
                acceptedSuffixes.any { domain == it || domain.endsWith(".$it") }
            }
            .filter { cookie ->
                val expires = cookie.optDouble("expires", 0.0)
                expires <= 0.0 || expires > nowSeconds
            }
            .sortedWith(
                compareByDescending<JSONObject> { it.optString("domain").length }
                    .thenByDescending { it.optString("path").length }
            )
            .distinctBy { it.optString("name") }
            .mapNotNull { cookie ->
                val name = cookie.optString("name")
                if (name.isBlank()) null else "$name=${cookie.optString("value")}" 
            }
            .toList()
        if (matching.isEmpty()) {
            error("没有读取到该网盘的 Cookie，请确认已经在刚打开的网页登录成功")
        }
        if (key == CredentialKey.BAIDU_COOKIE && matching.none { it.startsWith("BDUSS=") }) {
            error("未读取到百度 BDUSS，请确认百度网盘已经登录")
        }
        return matching.joinToString("; ")
    }

    private fun requestCookies(debuggerUrl: String): List<JSONObject> {
        val result = AtomicReference<String?>()
        val failure = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        val request = Request.Builder().url(debuggerUrl).build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("{\"id\":1,\"method\":\"Storage.getCookies\"}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                if (json.optInt("id") == 1) {
                    result.set(text)
                    latch.countDown()
                    webSocket.close(1000, "done")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure.set(t)
                latch.countDown()
            }
        })
        if (!latch.await(8, TimeUnit.SECONDS)) {
            socket.cancel()
            error("读取浏览器 Cookie 超时，请重试")
        }
        failure.get()?.let { throw IllegalStateException("连接浏览器失败：${it.message}", it) }
        val response = JSONObject(result.get() ?: error("浏览器没有返回 Cookie"))
        response.optJSONObject("error")?.let { error(it.optString("message", "浏览器拒绝读取 Cookie")) }
        val array = response.optJSONObject("result")?.optJSONArray("cookies")
            ?: error("浏览器没有返回 Cookie 列表")
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun devToolsUrl(port: Int): String? = runCatching {
        client.newCall(
            Request.Builder().url("http://127.0.0.1:$port/json/version").build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@use null
            JSONObject(response.body?.string().orEmpty()).optString("webSocketDebuggerUrl")
                .takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    /**
     * Dedicated import profiles contain live login cookies. Clearing only the
     * encrypted app credential is not a logout, so remove these profiles too.
     * Returns false when a still-open browser keeps files locked.
     */
    fun clearLoginData(): Boolean {
        sessions.clear()
        return ChromiumBrowser.entries
            .map { File(profileRoot, it.name.lowercase()) }
            .all { EmbeddedLoginImporter.deleteOwnedTree(it, profileRoot) }
    }

    private fun profileDirectory(browser: ChromiumBrowser): File =
        File(profileRoot, browser.name.lowercase()).apply { mkdirs() }

    private fun activePort(profileDirectory: File): Int? = runCatching {
        File(profileDirectory, "DevToolsActivePort").useLines { lines -> lines.firstOrNull()?.trim()?.toInt() }
    }.getOrNull()

    private fun findExecutable(browser: ChromiumBrowser): File? {
        val local = System.getenv("LOCALAPPDATA")
        val programFiles = System.getenv("ProgramFiles")
        val programFilesX86 = System.getenv("ProgramFiles(x86)")
        val candidates = when (browser) {
            ChromiumBrowser.CHROME -> listOfNotNull(
                local?.let { File(it, "Google/Chrome/Application/chrome.exe") },
                programFiles?.let { File(it, "Google/Chrome/Application/chrome.exe") },
                programFilesX86?.let { File(it, "Google/Chrome/Application/chrome.exe") }
            )
            ChromiumBrowser.EDGE -> listOfNotNull(
                programFilesX86?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
                programFiles?.let { File(it, "Microsoft/Edge/Application/msedge.exe") },
                local?.let { File(it, "Microsoft/Edge/Application/msedge.exe") }
            )
        }
        return candidates.firstOrNull(File::isFile)
    }

    companion object {
        private fun defaultProfileRoot(): File = File(
            System.getenv("LOCALAPPDATA") ?: File(System.getProperty("user.home"), "AppData/Local").absolutePath,
            "JieXi Desktop/4.0.0 browser-import"
        )
    }
}
