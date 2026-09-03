package com.yunx.app.data.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

enum class ProxyMode { SYSTEM, DIRECT, HTTP, SOCKS }

data class NetworkProxyConfig(
    val mode: ProxyMode = ProxyMode.SYSTEM,
    val host: String = "",
    val port: Int = 0
) {
    init {
        if (mode == ProxyMode.HTTP || mode == ProxyMode.SOCKS) {
            require(host.isNotBlank()) { "代理服务器不能为空" }
            require(port in 1..65535) { "代理端口必须在 1 到 65535 之间" }
        }
    }
}

/**
 * 全局 HTTP 客户端管理：
 * - [apiClient]：平台 API（登录/解析/直链）、HLS 下载、更新检查共用，超时宽松；
 * - [downloadClient]：分片下载专用，大 Dispatcher 保障分片并发（默认实例 maxRequestsPerHost=5 会锁死并发）。
 * 所有构建都使用系统证书链和 OkHttp 主机名校验，不提供进程内绕过开关。
 */
object HttpClients {

    private val lock = Any()

    @Volatile
    private var apiCache: OkHttpClient? = null

    @Volatile
    private var downloadCache: OkHttpClient? = null

    @Volatile
    private var proxyConfig = NetworkProxyConfig()

    /** 应用新的代理配置并关闭旧连接；后续 API 与下载请求统一生效。 */
    fun configure(config: NetworkProxyConfig) = synchronized(lock) {
        if (config == proxyConfig) return@synchronized
        apiCache?.connectionPool?.evictAll()
        downloadCache?.connectionPool?.evictAll()
        apiCache = null
        downloadCache = null
        proxyConfig = config
    }

    /** 普通 API 客户端（各平台 API、HLS、更新检查） */
    fun apiClient(): OkHttpClient {
        apiCache?.let { return it }
        synchronized(lock) {
            apiCache?.let { return it }
            return buildApi().also { apiCache = it }
        }
    }

    /** 下载专用客户端：大 Dispatcher + 长超时，不锁死分片并发 */
    fun downloadClient(): OkHttpClient {
        downloadCache?.let { return it }
        synchronized(lock) {
            downloadCache?.let { return it }
            return buildDownload().also { downloadCache = it }
        }
    }

    private fun buildApi(): OkHttpClient {
        return applyProxy(OkHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun buildDownload(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 64 // 与桌面极速模式上限对齐
        }
        return applyProxy(OkHttpClient.Builder())
            .dispatcher(dispatcher)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 96,
                    keepAliveDuration = 5,
                    timeUnit = TimeUnit.MINUTES
                )
            )
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun applyProxy(builder: OkHttpClient.Builder): OkHttpClient.Builder = when (val config = proxyConfig) {
        NetworkProxyConfig(ProxyMode.SYSTEM) -> builder
        else -> when (config.mode) {
            ProxyMode.SYSTEM -> builder
            ProxyMode.DIRECT -> builder.proxy(Proxy.NO_PROXY)
            ProxyMode.HTTP -> builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
            ProxyMode.SOCKS -> builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port)))
        }
    }
}
