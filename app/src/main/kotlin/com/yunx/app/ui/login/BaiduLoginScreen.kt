package com.yunx.app.ui.login

import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel

@Composable
fun BaiduLoginScreen(
    viewModel: BaiduAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) = EmbeddedCookieLoginScreen(
    title = "百度网盘登录",
    loginUrl = BaiduConstants.LOGIN_URL,
    allowedDomains = setOf("baidu.com"),
    userAgent = BaiduConstants.UA_WEB,
    requiredCookieNames = setOf("BDUSS"),
    readCookie = { CookieManager.getInstance().getCookie(BaiduConstants.COOKIE_DOMAIN).orEmpty() },
    saveCookie = viewModel::saveBaiduAccount,
    onBack = onBack,
    onSaved = onSaved
)
