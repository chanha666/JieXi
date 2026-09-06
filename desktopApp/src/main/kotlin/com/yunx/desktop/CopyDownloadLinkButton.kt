package com.yunx.desktop

import com.yunx.desktop.i18n.tr

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun CopyDownloadLinkButton(fetch: suspend () -> Pair<String, suspend () -> Unit>) {
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    fun close() {
        val cleanup = prepared?.second
        prepared = null; shown = false
        if(cleanup != null) scope.launch { withContext(NonCancellable + Dispatchers.IO) { runCatching { cleanup() } } }
    }
    DisposableEffect(Unit) { onDispose {
        val cleanup = prepared?.second
        prepared = null
        if(cleanup != null) scope.launch(NonCancellable + Dispatchers.IO) { runCatching { cleanup() } }
    } }
    TextButton(onClick = { shown = true; message = null }) { Text(tr("复制直链")) }
    if(shown) AlertDialog(onDismissRequest = { if(!busy) close() },title = { Text(tr("临时下载地址")) },
        text = { Text(message ?: tr("地址可能需要相应请求头，且会过期。部分网盘取链需要临时转存；外部下载结束后再关闭此窗口，关闭时会清理本次临时目录。不会复制 Cookie。")) },
        confirmButton = { Button(enabled = !busy,onClick = {
            busy = true
            scope.launch {
                try {
                    val value = prepared ?: withContext(Dispatchers.IO) { fetch() }.also { prepared = it }
                    check(value.first.startsWith("http://") || value.first.startsWith("https://")) { "此资源暂无可复制的单文件直链" }
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value.first),null)
                    message = "地址已复制。使用完毕后点击关闭并清理。分离音视频流仍建议在软件内下载合并。"
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { message = "获取失败：" + (e.message ?: "请检查账号或网络") }
                finally { busy = false }
            }
        }) { Text(if(busy) tr("正在获取…") else tr("获取并复制")) } },
        dismissButton = { TextButton(enabled = !busy,onClick = { close() }) { Text(tr("关闭并清理")) } })
}
