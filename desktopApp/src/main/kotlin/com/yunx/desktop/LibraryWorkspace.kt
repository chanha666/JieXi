package com.yunx.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.yunx.desktop.core.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal fun LibraryWorkspace(controller: DesktopAppController) {
    var favorites by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<DesktopFavorite?>(null) }
    var link by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<DesktopFavorite?>(null) }
    var clear by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("历史与收藏",style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(favorites,{ favorites = true },label = { Text("收藏") })
            FilterChip(!favorites,{ favorites = false },label = { Text("历史") })
            Spacer(Modifier.weight(1f))
            if(favorites) Button(onClick = { link = ""; title = ""; group = ""; message = null; add = true }) { Text("添加收藏") }
            else TextButton(onClick = { clear = true }) { Text("清空历史") }
        }
        OutlinedTextField(query,{ query = it },Modifier.fillMaxWidth(),placeholder = { Text("搜索标题或链接") },singleLine = true)
        if(favorites) {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }) { Text("分类：$category") }
                DropdownMenu(menu,{ menu = false }) { (listOf("全部") + controller.favorites.map { it.category }.distinct()).forEach { c ->
                    DropdownMenuItem(text = { Text(c) },onClick = { category = c; menu = false })
                } }
            }
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(favorites) items(controller.favorites.filter { (category == "全部" || it.category == category) && (it.title + it.link).contains(query,true) },key = { it.id }) { item ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(item.title,style = MaterialTheme.typography.titleMedium)
                        Text(item.category + " · " + item.platform,style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(item.link,style = MaterialTheme.typography.bodyMedium) }
                        Row {
                            TextButton(onClick = { controller.resolveSaved(item.link) }) { Text("解析") }
                            TextButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.link),null) }) { Text("复制") }
                            TextButton(onClick = { edit = item; group = item.category }) { Text("修改分类") }
                            TextButton(onClick = { delete = item }) { Text("删除") }
                        }
                    }
                }
            }
            else items(controller.history.filter { (it.title + it.link).contains(query,true) },key = { it.id }) { item ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(item.title,style = MaterialTheme.typography.titleMedium)
                        Text(item.platform,style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { controller.resolveSaved(item.link) }) { Text("再次解析") }
                            TextButton(onClick = { link = item.link; title = item.title; group = ""; message = null; add = true }) { Text("收藏") }
                            TextButton(onClick = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(item.link),null) }) { Text("复制") }
                        }
                    }
                }
            }
        }
    }
    if(add || edit != null) AlertDialog(onDismissRequest = { add = false; edit = null },
        title = { Text(if(add) "添加收藏" else "修改分类") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if(add) {
                OutlinedTextField(link,{ link = it },label = { Text("链接或分享文案") })
                OutlinedTextField(title,{ title = it },label = { Text("标题（可选）") })
            }
            OutlinedTextField(group,{ group = it },label = { Text("分类（可选）") })
            message?.let { Text(it) }
        } },
        confirmButton = { Button(onClick = {
            runCatching { if(add) controller.saveFavorite(link,title,group) else controller.setFavoriteCategory(checkNotNull(edit),group) }
                .onSuccess { add = false; edit = null }.onFailure { message = it.message ?: "保存失败" }
        }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { add = false; edit = null }) { Text("取消") } })
    if(clear || delete != null) AlertDialog(onDismissRequest = { clear = false; delete = null },
        title = { Text(if(clear) "清空解析历史？" else "删除这个收藏？") },
        text = { Text("只删除本机记录，不删除已下载文件或网盘文件。") },
        confirmButton = { Button(onClick = { if(clear) controller.clearHistory() else delete?.let(controller::removeFavorite); clear = false; delete = null }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { clear = false; delete = null }) { Text("取消") } })
}
