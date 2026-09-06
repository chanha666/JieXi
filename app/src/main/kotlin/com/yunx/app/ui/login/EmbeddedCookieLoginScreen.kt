package com.yunx.app.ui.login

import com.yunx.app.ui.i18n.tr

import android.graphics.Bitmap
import android.content.Intent
import android.os.Build
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import kotlinx.coroutines.launch

internal object CookieLoginPolicy {
    fun hasRequiredCookies(cookie: String, requiredNames: Set<String>): Boolean {
        if (cookie.isBlank() || requiredNames.isEmpty()) return false
        val names = cookie.split(';')
            .mapNotNull { part -> part.substringBefore('=', missingDelimiterValue = "").trim().takeIf(String::isNotEmpty) }
            .toSet()
        return requiredNames.all(names::contains)
    }

    fun isAllowedNavigation(url: String, allowedDomains: Set<String>): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return allowedDomains.any { domain ->
            val normalized = domain.lowercase().trim().trimStart('.').trimEnd('.')
            host == normalized || host.endsWith(".$normalized")
        }
    }
}

/**
 * Android 内置网页登录：用户只需从网盘卡片点一次“登录”。
 * WebView 识别到必要 Cookie 后自动校验、落库并返回；“完成”仅作网页未触发跳转时的兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmbeddedCookieLoginScreen(
    title: String,
    loginUrl: String,
    allowedDomains: Set<String>,
    userAgent: String,
    requiredCookieNames: Set<String>,
    readCookie: () -> String,
    saveCookie: suspend (String) -> Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentSaveCookie by rememberUpdatedState(saveCookie)
    val currentOnSaved by rememberUpdatedState(onSaved)
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var lastAttemptedCookie by remember { mutableStateOf("") }

    fun captureLogin(showMissingMessage: Boolean) {
        if (isSaving) return
        CookieManager.getInstance().flush()
        val cookie = readCookie().trim()
        if (!CookieLoginPolicy.hasRequiredCookies(cookie, requiredCookieNames)) {
            if (showMissingMessage) SnackbarController.show("尚未检测到登录，请先在当前页面完成登录")
            return
        }
        if (!showMissingMessage && cookie == lastAttemptedCookie) return
        lastAttemptedCookie = cookie
        scope.launch {
            isSaving = true
            val saved = runCatching { currentSaveCookie(cookie) }.getOrDefault(false)
            isSaving = false
            if (saved) {
                SnackbarController.show("登录成功，授权已自动保存")
                currentOnSaved()
            } else if (showMissingMessage) {
                SnackbarController.show("登录信息校验失败，请刷新后重试")
            }
        }
    }

    val webView = remember(loginUrl, userAgent) {
        CookieManager.getInstance().setAcceptCookie(true)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = userAgent
            setInitialScale(0)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
                    captureLogin(showMissingMessage = false)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url?.toString() ?: return true
                    if (CookieLoginPolicy.isAllowedNavigation(target, allowedDomains)) return false
                    if (request.isForMainFrame && request.url.scheme.equals("https", ignoreCase = true)) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        SnackbarController.show("已在外部浏览器打开非网盘页面")
                    }
                    return true
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    isLoading = false
                    SnackbarController.show("登录页面刚刚中断，正在自动恢复")
                    view?.post { view.reload() }
                    return true
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(loginUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    BackHandler(enabled = !isSaving) {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    val snackbarHostState = rememberGlobalSnackbarHostState()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isSaving) {
                            if (webView.canGoBack()) webView.goBack() else onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("返回"))
                    }
                },
                actions = {
                    TextButton(onClick = { captureLogin(showMissingMessage = true) }, enabled = !isSaving) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(tr("完成"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val VIEWPORT_SCRIPT =
    "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
        "var c='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
        "if(m){m.setAttribute('content',c);}else{var n=document.createElement('meta');" +
        "n.name='viewport';n.content=c;document.head.appendChild(n);}window.dispatchEvent(new Event('resize'));})()"
