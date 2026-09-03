package com.yunx.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class NetworkProxyConfigTest {
    @Test
    fun validatesManualProxyAddress() {
        val valid = NetworkProxyConfig(ProxyMode.SOCKS, "127.0.0.1", 7890)
        assertEquals(7890, valid.port)
        assertFails { NetworkProxyConfig(ProxyMode.HTTP, "", 7890) }
        assertFails { NetworkProxyConfig(ProxyMode.HTTP, "localhost", 70000) }
    }
}
