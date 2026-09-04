package com.yunx.app.ui.login

import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import com.yunx.app.data.network.UCConstants
import com.yunx.app.ui.viewmodel.UCAccountViewModel

@Composable
fun UCLoginScreen(
    viewModel: UCAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) = EmbeddedCookieLoginScreen(
    title = "UC网盘登录",
    loginUrl = UCConstants.LOGIN_URL,
    allowedDomains = setOf("uc.cn"),
    userAgent = UCConstants.USER_AGENT,
    requiredCookieNames = setOf("__pus", "__puus"),
    readCookie = { CookieManager.getInstance().getCookie(UCConstants.COOKIE_DOMAIN).orEmpty() },
    saveCookie = viewModel::saveUCAccount,
    onBack = onBack,
    onSaved = onSaved
)
