package com.yunx.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 3.0 主导航。竖屏允许横向压缩标签，横屏使用导航轨道。
 */
enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Resolve("解析", Icons.Filled.Link, Icons.Outlined.Link),
    Mine("我的", Icons.Filled.Person, Icons.Outlined.PersonOutline),
    Drive("网盘", Icons.Filled.Cloud, Icons.Outlined.Cloud),
    Download("下载", Icons.Filled.Download, Icons.Outlined.Download),
    Library("记录", Icons.Filled.History, Icons.Outlined.History),
    Status("诊断", Icons.Filled.Hub, Icons.Outlined.Hub),
    Settings("设置", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        val primary = listOf(Resolve, Download, Mine)
    }
}
