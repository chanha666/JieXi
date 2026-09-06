package com.yunx.app.ui.screens

import com.yunx.app.ui.i18n.tr

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.UnifiedLinkClassifier
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.resolve.DownloadLinkDialog
import com.yunx.app.ui.resolve.ShareDetailScreen
import com.yunx.app.ui.viewmodel.*
import com.fuke.mobile.TaskStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ResolveViewModel,
    quarkCloudViewModel: QuarkCloudViewModel,
    xunleiCloudViewModel: XunleiCloudViewModel,
    baiduCloudViewModel: BaiduCloudViewModel,
    c139CloudViewModel: C139CloudViewModel,
    ucCloudViewModel: UCCoudViewModel,
    pan123CloudViewModel: Pan123CloudViewModel,
    modifier: Modifier = Modifier,
    onMediaResolve: (String) -> Unit = {},
    onDownloads: () -> Unit = {},
    onAccounts: () -> Unit = {}
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    // Passcodes are deliberately kept out of Android saved instance state.
    var pwd by remember { mutableStateOf("") }
    val parsed = remember(link) { ShareLinkParser.parse(link) }

    LaunchedEffect(viewModel.downloadError) {
        viewModel.downloadError?.let { SnackbarController.show(it); viewModel.consumeDownloadError() }
    }
    BackHandler(enabled = state is ResolveUiState.Detail || state is ResolveUiState.Error) {
        if (state is ResolveUiState.Detail) viewModel.navigateBack() else viewModel.backToInput()
    }

    when (val s = state) {
        is ResolveUiState.Detail -> ShareDetailScreen(
            session = s.session, files = s.files, viewModel = viewModel,
            quarkCloudViewModel = quarkCloudViewModel, xunleiCloudViewModel = xunleiCloudViewModel,
            baiduCloudViewModel = baiduCloudViewModel, c139CloudViewModel = c139CloudViewModel,
            ucCloudViewModel = ucCloudViewModel, pan123CloudViewModel = pan123CloudViewModel,
            scrollBehavior = scrollBehavior,
            onExit = { viewModel.backToInput() }, onBack = { viewModel.navigateBack() }
        )
        is ResolveUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
                Text(tr("正在读取分享内容…"), style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> Column(
            modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(tr("一个链接，\n从这里开始。"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(tr("视频或网盘分享，粘贴后自动识别。"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { link = it; pwd = "" },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr("粘贴链接，也可以粘贴整段分享文字")) },
                minLines = 3, maxLines = 6,
                shape = MaterialTheme.shapes.large,
                trailingIcon = {
                    if (link.isNotEmpty()) IconButton(onClick = { link = ""; pwd = "" }) {
                        Icon(Icons.Outlined.Close, tr("清空链接"))
                    }
                }
            )
            if (parsed != null) OutlinedTextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = { Text(tr("提取码（可选）")) },
                placeholder = { Text(parsed.pwd?.let { tr("已从链接识别") } ?: tr("分享有提取码时填写")) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = runCatching {
                            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        }.getOrNull()
                        if (text.isNullOrBlank()) SnackbarController.show("剪贴板里还没有链接")
                        else { link = text; pwd = "" }
                    },
                    modifier = Modifier.heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium
                ) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("粘贴")) }
                Button(
                    enabled = link.isNotBlank(),
                    onClick = {
                        val detected = UnifiedLinkClassifier.classifyText(link).firstOrNull()
                        when {
                            detected == null -> SnackbarController.show("请粘贴完整的 http 或 https 链接")
                            detected.kind == LinkKind.CLOUD_SHARE -> viewModel.startResolve(link, pwd.ifBlank { parsed?.pwd.orEmpty() })
                            else -> onMediaResolve(link)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium
                ) { Text(tr("开始解析")) }
            }
            if (s is ResolveUiState.Error) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(s.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        if (s.message.contains("登录") || s.message.contains("账号")) {
                            TextButton(onClick = onAccounts) { Text(tr("管理对应网盘账号")) }
                        }
                    }
                }
            }
            RecentDownloads(onDownloads)
        }
    }

    if (viewModel.isFetchingDownloadLink) AlertDialog(
        onDismissRequest = {}, confirmButton = {},
        title = { Text(tr("准备下载")) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(tr("正在获取可用的下载链接…"))
            }
        }
    )
    viewModel.downloadLink?.let { result ->
        DownloadLinkDialog(link = result, onDownload = { viewModel.startDownload(result) }, onDismiss = { viewModel.dismissDownloadDialog() })
    }
}

@Composable
private fun RecentDownloads(onDownloads: () -> Unit) {
    val context = LocalContext.current
    val flow = remember { AppDatabase.get(context).downloadTaskDao().observeAll() }
    val cloud by flow.collectAsState(initial = emptyList())
    val media by TaskStore.tasks.collectAsState()
    val recent = (cloud.map {
        Triple(it.createTime, it.fileName.substringAfterLast('/'), DownloadTaskEntity.statusText(it.status))
    } + media.map {
        Triple(it.createdAt, it.title, when (it.status) {
            com.fuke.mobile.TaskStatus.QUEUED -> "等待中"
            com.fuke.mobile.TaskStatus.RUNNING -> "下载中"
            com.fuke.mobile.TaskStatus.PAUSED -> "已暂停"
            com.fuke.mobile.TaskStatus.COMPLETED -> "已完成"
            com.fuke.mobile.TaskStatus.FAILED -> "失败"
            com.fuke.mobile.TaskStatus.CANCELED -> "已取消"
        })
    }).sortedByDescending { it.first }.take(3)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("最近任务"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onDownloads) { Text(tr("查看全部")) }
    }
    if (recent.isEmpty()) {
        Text(tr("还没有下载任务。添加第一个链接，就从上面开始。"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else recent.forEach { (_, title, status) ->
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(12.dp))
                Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
