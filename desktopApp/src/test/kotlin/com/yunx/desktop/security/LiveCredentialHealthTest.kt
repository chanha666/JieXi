package com.yunx.desktop.security

import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in diagnostic for the credential saved by the installed desktop app.
 * It never prints or copies the credential. Enable only for a local health check.
 */
class LiveCredentialHealthTest {
    @Test
    fun savedBaiduCredentialSurvivesRestartAndIsAcceptedByOfficialApi() = runBlocking {
        assumeTrue(System.getenv("YUNX_LIVE_CREDENTIAL_CHECK") == "1")

        val firstRead = CredentialStore().get(CredentialKey.BAIDU_COOKIE)
        assertNotNull("百度授权没有保存到 Windows 凭据存储", firstRead)
        val credential = firstRead!!
        assertTrue("百度授权缺少 BDUSS", credential.split(';').any { it.trim().startsWith("BDUSS=") })

        val secondRead = CredentialStore().get(CredentialKey.BAIDU_COOKIE)
        assertTrue("软件重启后无法恢复百度授权", credential == secondRead)
        val request = Request.Builder()
            .url("https://pan.baidu.com/api/gettemplatevariable?clienttype=0&app_id=250528&web=1&fields=%5B%22username%22%2C%22bdstoken%22%5D")
            .header("Cookie", credential)
            .header("User-Agent", BaiduConstants.UA_NETDISK)
            .get()
            .build()
        val result = OkHttpClient().newCall(request).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            Triple(response.code, json.optInt("errno", Int.MIN_VALUE), json.optJSONObject("result"))
        }
        assertTrue("百度接口 HTTP=${result.first} errno=${result.second}", result.first in 200..299 && result.second == 0)
        assertTrue("百度授权有效但没有返回登录账号", !result.third?.optString("username").isNullOrBlank())
        assertTrue("百度授权有效但没有返回 bdstoken", !result.third?.optString("bdstoken").isNullOrBlank())
        assertNotNull("百度官方接口已拒绝当前授权", BaiduApi().fetchNickname(credential))
    }
}
