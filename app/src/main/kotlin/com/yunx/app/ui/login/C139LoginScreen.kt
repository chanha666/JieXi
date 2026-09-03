package com.yunx.app.ui.login

import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yunx.app.data.network.C139Constants
import com.yunx.app.ui.viewmodel.C139AccountViewModel

@Composable
fun C139LoginScreen(
    viewModel: C139AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    EmbeddedCookieLoginScreen(
        title = "139网盘登录",
        loginUrl = C139Constants.LOGIN_URL,
        allowedDomains = setOf("139.com", "10086.cn", "cmpassport.com"),
        userAgent = WebSettings.getDefaultUserAgent(context),
        requiredCookieNames = setOf("Os_SSo_Sid", "RMKEY"),
        readCookie = { C139Constants.extractCookies { CookieManager.getInstance().getCookie(it) } },
        saveCookie = viewModel::saveC139Account,
        onBack = onBack,
        onSaved = onSaved
    )
}
