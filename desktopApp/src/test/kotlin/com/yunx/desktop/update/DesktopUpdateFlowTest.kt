package com.yunx.desktop.update

import com.yunx.app.data.network.NetworkProxyConfig
import com.yunx.app.data.network.ProxyMode
import com.yunx.desktop.core.DesktopAppController
import com.yunx.desktop.core.DesktopBuildInfo
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class DesktopUpdateFlowTest {
    @Test fun `runtime display updater and packaging share one version`() {
        assertEquals(System.getProperty("jiexi.test.expectedVersion"), DesktopBuildInfo.version)
        assertEquals(DesktopBuildInfo.version, DesktopAppController.APP_VERSION)
        assertEquals(0, DesktopUpdateService.compareVersions("v${DesktopBuildInfo.version}", DesktopAppController.APP_VERSION))
    }

    @Test fun `system and explicit proxy selections do not silently become direct`() {
        val uri = URI("https://github.com/chanha666/JieXi")
        val selector = DesktopUpdateProxySelector(NetworkProxyConfig(), { "127.0.0.1:10808" }, null)
        val selected = selector.select(uri).single()
        assertEquals(Proxy.Type.HTTP, selected.type())
        assertEquals(10808, (selected.address() as InetSocketAddress).port)
        assertEquals(Proxy.NO_PROXY, DesktopUpdateProxySelector(NetworkProxyConfig(ProxyMode.DIRECT)).select(uri).single())
        assertEquals(Proxy.Type.SOCKS, DesktopUpdateProxySelector(NetworkProxyConfig(ProxyMode.SOCKS, "localhost", 9999)).select(uri).single().type())
        assertEquals(8443, (DesktopUpdateProxySelector.parseSystemProxy("http=proxy:8080;https=proxy:8443", "https")!!.address() as InetSocketAddress).port)
        assertEquals(Proxy.NO_PROXY, selector.select(URI("https://127.0.0.1/test")).single())
    }

    @Test fun `HTTPS update resumes a partial file verifies bytes and reuses verified cache`() = withServer { server, client, directory ->
        runBlocking {
            val content = ByteArray(131072) { (it % 251).toByte() }
            val asset = asset(server, content)
            File(directory, "${asset.name}.part").writeBytes(content.copyOfRange(0, 8192))
            server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 8192-131071/131072")
                .setBody(Buffer().write(content.copyOfRange(8192, content.size))))
            val progress = mutableListOf<Long>()
            val service = DesktopUpdateService(client)
            val output = service.download(asset, directory) { bytes, _ -> progress.add(bytes) }
            assertContentEquals(content, output.readBytes())
            assertEquals("bytes=8192-", server.takeRequest().getHeader("Range"))
            assertEquals(content.size.toLong(), progress.last())
            assertEquals(output, service.download(asset, directory))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `server ignoring Range replaces only partial data and corrupt package preserves existing installer`() = withServer { server, client, directory ->
        runBlocking {
            val content = "new verified installer".toByteArray()
            val asset = asset(server, content)
            File(directory, "${asset.name}.part").writeText("old partial")
            server.enqueue(MockResponse().setBody(Buffer().write(content)))
            assertContentEquals(content, DesktopUpdateService(client).download(asset, directory).readBytes())
            val previous = File(directory, asset.name).apply { writeText("keep previous installer") }
            server.enqueue(MockResponse().setBody("corrupted response"))
            assertFails { DesktopUpdateService(client).download(asset, directory) }
            assertEquals("keep previous installer", previous.readText())
            assertFalse(File(directory, "${asset.name}.part").exists())
        }
    }

    private fun asset(server: MockWebServer, bytes: ByteArray) = DesktopUpdateAsset("JieXi-Test-Setup.exe",
        server.url("/setup.exe").newBuilder().host("127.0.0.1").build().toString(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })

    private fun withServer(block: (MockWebServer, OkHttpClient, File) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer().apply { useHttps(serverCertificates.sslSocketFactory(), false); start() }
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager).build()
        val directory = Files.createTempDirectory("jiexi-update-test-").toFile()
        try { block(server, client, directory) } finally { server.shutdown(); directory.deleteRecursively() }
    }
}
