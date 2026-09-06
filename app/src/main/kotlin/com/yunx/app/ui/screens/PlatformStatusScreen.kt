package com.yunx.app.ui.screens

import com.yunx.app.ui.i18n.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

data class PlatformCheck(val name: String, val endpoint: String, val configured: Boolean, val result: String = "未检测")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformStatusScreen(scrollBehavior: TopAppBarScrollBehavior, configured: Map<String, Boolean>) {
    val scope = rememberCoroutineScope()
    val initial = remember(configured) { listOf(
        PlatformCheck("夸克", "https://pan.quark.cn", configured["夸克"] == true), PlatformCheck("UC", "https://drive.uc.cn", configured["UC"] == true),
        PlatformCheck("迅雷", "https://pan.xunlei.com", configured["迅雷"] == true), PlatformCheck("百度", "https://pan.baidu.com", configured["百度"] == true),
        PlatformCheck("139", "https://yun.139.com", configured["139"] == true), PlatformCheck("123", "https://www.123pan.com", configured["123"] == true)
    ) }
    var checks by remember(initial) { mutableStateOf(initial) }
    var running by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.HealthAndSafety, tr("诊断中心"))
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(tr("诊断中心"), fontWeight = FontWeight.SemiBold); Text(tr("检测网络可达性与本机授权状态"), style = MaterialTheme.typography.bodySmall) }
                Button(enabled = !running, onClick = {
                    running = true; checks = checks.map { it.copy(result = "检测中") }
                    scope.launch { checks = checks.map { item -> item.copy(result = withContext(Dispatchers.IO) { runCatching { (URL(item.endpoint).openConnection().apply { connectTimeout = 5000; readTimeout = 5000 }).connect(); "网络正常" }.getOrDefault("网络不可达") }) }; running = false }
                }) { Text(if (running) tr("检测中…") else tr("开始检测")) }
            }
        }
        Text(tr("适配状态统一标记为“实验”，只有真实账号和样本验收后才会升级为“稳定”。"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(checks, key = { it.name }) { item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(tr("实验"), color = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(14.dp)); Text(if (item.configured) tr("账号待验证") else tr("未登录"), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(14.dp)); Text(item.result, style = MaterialTheme.typography.bodySmall)
                } }
            }
        }
    }
}
