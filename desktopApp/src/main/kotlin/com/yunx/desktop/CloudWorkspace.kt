package com.yunx.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.*
import com.yunx.desktop.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun CloudWorkspace(controller: DesktopAppController) {
    var platform by remember { mutableStateOf(SharePlatform.QUARK) }
    var platformMenu by remember { mutableStateOf(false) }
    var path by remember(platform) { mutableStateOf(listOf(DesktopCloudService.root(platform) to "根目录")) }
    var files by remember(platform) { mutableStateOf(emptyList<ShareFile>()) }
    var selected by remember(platform) { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var quota by remember(platform) { mutableStateOf<QuotaInfo?>(null) }
    var query by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var action by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(7) }
    var shareResult by remember { mutableStateOf<ShareInfo?>(null) }
    val scope = rememberCoroutineScope()
    val service = controller.cloud
    val picked = files.filter { it.fid in selected }
    fun operate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); selected = emptySet(); reload++; message = "操作已提交，请以刷新后的文件列表为准" }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message = e.message ?: "操作失败" }
            finally { busy = false; action = "" }
        }
    }
    LaunchedEffect(platform, path, reload) {
        busy = true; message = null; selected = emptySet()
        try {
            files = service.list(platform, path.last().first)
            quota = runCatching { service.quota(platform) }.getOrNull()
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { files = emptyList(); message = e.message ?: "加载失败" }
        finally { busy = false }
    }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我的网盘文件", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { controller.page = AppPage.ACCOUNTS }) { Text("管理账号") }
            Box {
                OutlinedButton(onClick = { platformMenu = true }, enabled = !busy) { Text(DesktopResolver.platformName(platform)) }
                DropdownMenu(platformMenu, { platformMenu = false }) {
                    SharePlatform.entries.forEach { p -> DropdownMenuItem(
                        text = { Text(DesktopResolver.platformName(p)) },
                        onClick = { platform = p; platformMenu = false }) }
                }
            }
        }
        quota?.let { Text("已用 ${cloudBytes(it.used)} / 共 ${cloudBytes(it.total)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { path = path.dropLast(1) }, enabled = path.size > 1 && !busy) { Text("上一级") }
            Text(path.joinToString(" / ") { it.second }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { reload++ }, enabled = !busy) { Text("刷新") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索当前目录") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected.isNotEmpty() && selected.size == files.size, { selected = if(it) files.map { f -> f.fid }.toSet() else emptySet() }, enabled = !busy)
            Text("已选 ${selected.size}", Modifier.weight(1f))
            TextButton(onClick = { operate { controller.downloadCloud(platform, service.collectFiles(platform,picked)) } }, enabled = picked.isNotEmpty() && !busy) { Text("下载") }
            TextButton(onClick = { value = picked.single().fname; action = "rename" }, enabled = picked.size == 1 && !busy) { Text("重命名") }
            TextButton(onClick = { action = "move" }, enabled = picked.isNotEmpty() && !busy) { Text("移动") }
            TextButton(onClick = { value = ""; action = "share" }, enabled = picked.isNotEmpty() && !busy) { Text("分享") }
            TextButton(onClick = { action = "delete" }, enabled = picked.isNotEmpty() && !busy) { Text("删除") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (!busy && files.isEmpty() && message == null) Text("此目录暂无文件")
        LazyColumn(Modifier.weight(1f)) {
            items(files.filter { it.fname.contains(query, true) }, key = { it.fid }) { file ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    if (file.isdir) path = path + (DesktopCloudService.directory(platform, file) to file.fname)
                    else selected = if(file.fid in selected) selected - file.fid else selected + file.fid
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(file.fid in selected, { selected = if(it) selected + file.fid else selected - file.fid }, enabled = !busy)
                    Column(Modifier.weight(1f)) {
                        Text(file.fname, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if(file.isdir) "文件夹 · 点击打开" else cloudBytes(file.fsize), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (action == "move") CloudFolderDialog(platform, service, { action = "" }) { dest ->
        operate { service.move(platform, picked, dest) }
    }
    if (action in setOf("rename", "share", "delete")) AlertDialog(
        onDismissRequest = { if(!busy) action = "" },
        title = { Text(when(action) { "rename" -> "重命名"; "share" -> "创建分享"; else -> "删除网盘文件" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if(action == "delete") Text("确定从网盘删除所选 ${picked.size} 项？这会影响远端文件，而不只是本地记录。")
                else {
                    OutlinedTextField(value, { value = it }, label = { Text(if(action == "rename") "新名称" else "4 位提取码（可选）") }, singleLine = true)
                    if(action == "share") {
                        Row { listOf(1,7,30,0).forEach { n -> TextButton(onClick = { days = n }) { Text((if(days == n) "✓ " else "") + if(n == 0) "永久" else "${n}天") } } }
                        Text("百度需要 4 位提取码；139 使用平台生成的提取码。")
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !busy, onClick = {
            when(action) {
                "rename" -> operate { service.rename(platform, picked.single(), value) }
                "share" -> operate { shareResult = service.share(platform, picked, days, value) }
                "delete" -> operate { service.delete(platform, picked) }
            }
        }) { Text(if(busy) "处理中…" else "确认") } },
        dismissButton = { TextButton(onClick = { action = "" }, enabled = !busy) { Text("取消") } }
    )
    shareResult?.let { result -> AlertDialog(onDismissRequest = { shareResult = null }, title = { Text("分享已创建") },
        text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(result.shareUrl + "\n提取码：" + result.passcode.ifBlank { "无" }) } },
        confirmButton = { Button(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(result.shareUrl + " 提取码：" + result.passcode), null) }) { Text("复制链接") } },
        dismissButton = { TextButton(onClick = { shareResult = null }) { Text("关闭") } }) }
}

@Composable
internal fun CloudFolderDialog(platform: SharePlatform, service: DesktopCloudService, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var path by remember(platform) { mutableStateOf(listOf(DesktopCloudService.root(platform) to "根目录")) }
    var folders by remember { mutableStateOf(emptyList<ShareFile>()) }
    var busy by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        busy = true; error = null
        try { folders = service.list(platform, path.last().first).filter { it.isdir } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { error = e.message ?: "读取失败" }
        finally { busy = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择目标文件夹") },
        text = { Column(Modifier.heightIn(max = 380.dp)) {
            Text(path.joinToString(" / ") { it.second })
            TextButton(onClick = { path = path.dropLast(1) }, enabled = path.size > 1 && !busy) { Text("上一级") }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it) }
            LazyColumn { items(folders, key = { it.fid }) { f -> TextButton(onClick = { path = path + (DesktopCloudService.directory(platform, f) to f.fname) }, enabled = !busy) { Text(f.fname) } } }
        } },
        confirmButton = { Button(onClick = { onChoose(path.last().first); onDismiss() }, enabled = !busy && error == null) { Text("选择此文件夹") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun cloudBytes(bytes: Long): String = if(bytes < 0) "大小未知" else when {
    bytes >= 1073741824 -> "%.1f GB".format(bytes / 1073741824.0)
    bytes >= 1048576 -> "%.1f MB".format(bytes / 1048576.0)
    else -> "%.1f KB".format(bytes / 1024.0)
}
