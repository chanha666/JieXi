package com.yunx.desktop.update

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.yunx.app.data.network.NetworkProxyConfig
import com.yunx.app.data.network.ProxyMode
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** Reads the current user's proxy; never changes OS settings or silently bypasses a selected proxy. */
internal class DesktopUpdateProxySelector(
    private val config: NetworkProxyConfig,
    private val systemSetting: () -> String? = ::windowsProxy,
    private val fallback: ProxySelector? = ProxySelector.getDefault()
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        if (uri.host in setOf("localhost", "127.0.0.1", "::1", "[::1]")) return listOf(Proxy.NO_PROXY)
        return when (config.mode) {
            ProxyMode.DIRECT -> listOf(Proxy.NO_PROXY)
            ProxyMode.HTTP, ProxyMode.SOCKS -> listOf(Proxy(
                if (config.mode == ProxyMode.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved(config.host, config.port)
            ))
            ProxyMode.SYSTEM -> parseSystemProxy(systemSetting(), uri.scheme)?.let(::listOf)
                ?: fallback?.select(uri)?.takeIf { it.isNotEmpty() } ?: listOf(Proxy.NO_PROXY)
        }
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        // Do not retry directly if a user-selected proxy fails.
    }

    companion object {
        private fun windowsProxy(): String? = runCatching {
            if (!System.getProperty("os.name").startsWith("Windows", true)) return null
            val key = "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
            if (Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, key, "ProxyEnable") != 1) return null
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "ProxyServer")
        }.getOrNull()

        internal fun parseSystemProxy(value: String?, scheme: String): Proxy? = runCatching {
            val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
            val entries = text.split(';')
            val selected = if ('=' in text) {
                entries.firstOrNull { it.substringBefore('=').trim().equals(scheme, true) }
                    ?: entries.firstOrNull { it.substringBefore('=').trim().equals("socks", true) }
                    ?: return null
            } else text
            val socks = selected.substringBefore('=').equals("socks", true) || selected.startsWith("socks", true)
            val address = selected.substringAfter('=').trim()
            val parsed = URI(if ("://" in address) address else "http://$address")
            require(!parsed.host.isNullOrBlank() && parsed.port in 1..65535 && parsed.userInfo == null)
            Proxy(if (socks) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved(parsed.host, parsed.port))
        }.getOrNull()
    }
}
