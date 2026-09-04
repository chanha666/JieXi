package com.yunx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(scrollBehavior: TopAppBarScrollBehavior, viewModel: HistoryViewModel, onResolve: (String) -> Unit) {
    val items by viewModel.items.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("最近解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::clear, enabled = items.isNotEmpty()) { Text("清空") }
        }
        if (items.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.History, "无历史记录", modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(10.dp)); Text("还没有解析记录"); Text("提取码不会保存在历史中", style = MaterialTheme.typography.bodySmall)
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onResolve(item.link) }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifBlank { "未命名分享" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${item.platform} · ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.createdAt))}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { viewModel.delete(item.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除历史记录") }
                    }
                }
            }
        }
    }
}
