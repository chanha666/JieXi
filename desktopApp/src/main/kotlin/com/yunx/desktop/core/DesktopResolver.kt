package com.yunx.desktop.core

import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.XunleiDeviceFingerprint
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.desktop.security.CredentialKey
import com.yunx.desktop.security.CredentialStore

data class ResolvedShare(
    val platform: SharePlatform,
    val session: ShareSession,
    val files: List<ShareFile>,
    internal val credential: String,
    internal val repository: ShareResolveRepository
)

class DesktopResolver(private val credentials: CredentialStore) {
    suspend fun resolve(text: String, password: String?): ResolvedShare {
        val parsed = ShareLinkParser.parse(text)
            ?: throw IllegalArgumentException("无法识别分享链接")
        val credential = refreshedCredential(parsed.platform)
        val repository = repositoryFor(parsed.platform)
        val session = repository.createSession(text, password, credential).getOrThrow()
        val root = rootDirectory(parsed.platform)
        val files = repository.listFiles(session, root, credential).getOrThrow()
        return ResolvedShare(parsed.platform, session, files, credential, repository)
    }

    suspend fun listDirectory(current: ResolvedShare, directoryId: String): List<ShareFile> =
        current.repository.listFiles(current.session, directoryId, current.credential).getOrThrow()

    suspend fun getDownloadLink(current: ResolvedShare, file: ShareFile): DownloadLink {
        require(!file.isdir) { "文件夹不能直接下载，请先打开文件夹" }
        if (current.credential.isBlank() && !supportsAnonymousDownload(current.platform)) {
            throw IllegalStateException(
                "${platformName(current.platform)}允许匿名查看分享，但官方下载接口要求账号授权；" +
                    "如需下载，请在“云盘登录”中完成授权"
            )
        }
        return current.repository.getShareDownloadLink(current.session, file, current.credential).getOrThrow()
    }

    suspend fun cleanup(current: ResolvedShare, link: DownloadLink) {
        link.cleanupDirFid?.let { current.repository.cleanupTempDir(it, current.credential) }
    }

    fun downloadHeaders(platform: SharePlatform, credential: String): Map<String, String> = when (platform) {
        SharePlatform.XUNLEI -> mapOf("User-Agent" to XunleiConstants.APP_UA)
        SharePlatform.BAIDU -> mapOf(
            "Cookie" to credential,
            "User-Agent" to BaiduConstants.UA_NETDISK
        )
        SharePlatform.C139 -> mapOf("User-Agent" to C139Constants.PC_UA)
        SharePlatform.PAN123 -> mapOf(
            "User-Agent" to Pan123Constants.WEB_UA,
            "Referer" to Pan123Constants.DOWNLOAD_REFERER
        )
        SharePlatform.UC -> mapOf(
            "Cookie" to credential,
            "User-Agent" to UCConstants.USER_AGENT,
            "Referer" to UCConstants.DOWNLOAD_REFERER,
            "Origin" to UCConstants.WEB_ORIGIN
        )
        SharePlatform.QUARK -> mapOf(
            "Cookie" to credential,
            "User-Agent" to QuarkConstants.API_USER_AGENT,
            "Referer" to QuarkConstants.DOWNLOAD_REFERER
        )
    }.filterValues { it.isNotBlank() }

    private suspend fun refreshedCredential(platform: SharePlatform): String {
        val key = credentialKey(platform)
        val stored = credentials.get(key).orEmpty()
        if (stored.isBlank()) {
            if (platform == SharePlatform.XUNLEI) {
                throw IllegalStateException("迅雷官方分享接口要求账号令牌，暂不支持匿名解析")
            }
            return ""
        }
        val refreshed = when (platform) {
            SharePlatform.QUARK -> QuarkApi().refreshSession(stored)
            SharePlatform.UC -> UCApi().refreshSession(stored)
            else -> null
        }
        if (!refreshed.isNullOrBlank() && refreshed != stored) credentials.put(key, refreshed)
        return refreshed ?: stored
    }

    private fun repositoryFor(platform: SharePlatform): ShareResolveRepository = when (platform) {
        SharePlatform.QUARK -> QuarkResolveRepository(QuarkApi())
        SharePlatform.UC -> UCResolveRepository(UCApi())
        SharePlatform.BAIDU -> BaiduResolveRepository(BaiduApi())
        SharePlatform.C139 -> C139ResolveRepository(C139Api())
        SharePlatform.PAN123 -> Pan123ResolveRepository(Pan123Api()) {
            credentials.get(CredentialKey.PAN123_TOKEN)
        }
        SharePlatform.XUNLEI -> {
            val api = XunleiApi()
            XunleiResolveRepository(
                api = api,
                accountProvider = { credentials.get(CredentialKey.XUNLEI_ACCESS_TOKEN) },
                deviceIdProvider = { XunleiDeviceFingerprint.deviceId() },
                captchaProvider = { credentials.get(CredentialKey.XUNLEI_CAPTCHA_TOKEN) },
                refreshProvider = {
                    val refresh = credentials.get(CredentialKey.XUNLEI_REFRESH_TOKEN)
                    if (refresh == null) null else {
                        api.refreshToken(refresh, XunleiDeviceFingerprint.deviceId())?.also { (access, nextRefresh) ->
                            credentials.put(CredentialKey.XUNLEI_ACCESS_TOKEN, access)
                            credentials.put(CredentialKey.XUNLEI_REFRESH_TOKEN, nextRefresh)
                        }
                    }
                }
            )
        }
    }

    private fun credentialKey(platform: SharePlatform): CredentialKey = when (platform) {
        SharePlatform.QUARK -> CredentialKey.QUARK_COOKIE
        SharePlatform.UC -> CredentialKey.UC_COOKIE
        SharePlatform.XUNLEI -> CredentialKey.XUNLEI_ACCESS_TOKEN
        SharePlatform.BAIDU -> CredentialKey.BAIDU_COOKIE
        SharePlatform.C139 -> CredentialKey.C139_COOKIE
        SharePlatform.PAN123 -> CredentialKey.PAN123_TOKEN
    }

    private fun rootDirectory(platform: SharePlatform): String = when (platform) {
        SharePlatform.BAIDU -> ""
        else -> "0"
    }

    companion object {
        fun supportsAnonymousBrowse(platform: SharePlatform): Boolean = platform != SharePlatform.XUNLEI

        fun supportsAnonymousDownload(platform: SharePlatform): Boolean = when (platform) {
            SharePlatform.UC, SharePlatform.C139 -> true
            else -> false
        }

        fun platformName(platform: SharePlatform): String = when (platform) {
            SharePlatform.QUARK -> "夸克网盘"
            SharePlatform.UC -> "UC 网盘"
            SharePlatform.XUNLEI -> "迅雷网盘"
            SharePlatform.BAIDU -> "百度网盘"
            SharePlatform.C139 -> "139 网盘"
            SharePlatform.PAN123 -> "123 云盘"
        }
    }
}
