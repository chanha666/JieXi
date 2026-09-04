package com.yunx.app.ui.login

import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.ui.viewmodel.QuarkAccountViewModel

@Composable
fun QuarkLoginScreen(
    viewModel: QuarkAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) = EmbeddedCookieLoginScreen(
    title = "夸克网盘登录",
    loginUrl = QuarkConstants.LOGIN_URL,
    allowedDomains = setOf("quark.cn"),
    userAgent = QuarkConstants.USER_AGENT,
    requiredCookieNames = setOf("__pus", "__puus"),
    readCookie = { CookieManager.getInstance().getCookie(QuarkConstants.COOKIE_DOMAIN).orEmpty() },
    saveCookie = viewModel::saveQuarkAccount,
    onBack = onBack,
    onSaved = onSaved
)
