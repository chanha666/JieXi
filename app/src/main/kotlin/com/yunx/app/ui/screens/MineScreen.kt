package com.yunx.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.BuildConfig
import com.yunx.app.ui.navigation.MainTab

/** Secondary destinations stay available without competing with the main action. */
@Composable
fun MineScreen(
    onNavigate: (MainTab) -> Unit,
    onBookmarks: () -> Unit,
    onTools: () -> Unit,
    onMediaSettings: () -> Unit,
    onTheme: () -> Unit,
    onSupport: () -> Unit,
    onAbout: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("按自己的习惯使用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("管理内容，也照顾每一次下载。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        MineSection("我的内容")
        MineRow(Icons.Outlined.Bookmarks, "收藏链接", "留住下次还会用到的内容", onBookmarks)
        MineRow(Icons.Outlined.History, "解析历史", "查看和再次使用网盘链接") { onNavigate(MainTab.Library) }
        MineRow(Icons.Outlined.Cloud, "网盘与账号", "浏览文件、管理登录状态") { onNavigate(MainTab.Drive) }
        Spacer(Modifier.height(20.dp))
        MineSection("工具与偏好")
        MineRow(Icons.Outlined.Tune, "媒体小工具", "图片去水印、视频截图与音频提取", onTools)
        MineRow(Icons.Outlined.Palette, "外观", "主题、色彩与显示", onTheme)
        MineRow(Icons.Outlined.Settings, "下载与应用设置", "保存位置、下载预设与反馈") { onNavigate(MainTab.Settings) }
        MineRow(Icons.Outlined.VideoLibrary, "视频下载偏好", "画质、命名与下载核心", onMediaSettings)
        MineRow(Icons.Outlined.Info, "平台状态与诊断", "遇到问题时，从这里开始") { onNavigate(MainTab.Status) }
        Spacer(Modifier.height(20.dp))
        MineSection("关于解析")
        MineRow(Icons.Outlined.FavoriteBorder, "支持作者", "如果解析帮到了你，欢迎随心赞赏", onSupport)
        MineRow(Icons.Outlined.Info, "关于与更新", "解析 ${BuildConfig.VERSION_NAME}", onAbout)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MineSection(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MineRow(icon: ImageVector, title: String, subtitle: String, action: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}
