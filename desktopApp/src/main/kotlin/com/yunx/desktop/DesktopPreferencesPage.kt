package com.yunx.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunx.desktop.core.DesktopAppController

@Composable
internal fun desktopColorScheme(mode: String, hex: String, revision: Int = 0): ColorScheme {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val accent = Color(0xFF000000 or (hex.toLongOrNull(16) ?: 0xB65326))
    return if(dark) darkColorScheme(
        primary = androidx.compose.ui.graphics.lerp(accent, Color.White, .48f), onPrimary = Color(0xFF302018),
        primaryContainer = androidx.compose.ui.graphics.lerp(accent, Color(0xFF201C18), .65f),
        secondary = Color(0xFFD6B9A8), onSecondary = Color(0xFF302018),
        secondaryContainer = Color(0xFF49392E), onSecondaryContainer = Color(0xFFF3E2D6),
        tertiary = Color(0xFFA8CBB6), tertiaryContainer = Color(0xFF294135), onTertiaryContainer = Color(0xFFD4EEDD),
        background = Color(0xFF1C1B19), onBackground = Color(0xFFECE6DE),
        surface = Color(0xFF23211E), onSurface = Color(0xFFECE6DE),
        surfaceVariant = Color(0xFF292622), onSurfaceVariant = Color(0xFFC9C0B5),
        outline = Color(0xFF958B80), outlineVariant = Color(0xFF49423A)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = androidx.compose.ui.graphics.lerp(accent, Color.White, .88f), onPrimaryContainer = Color(0xFF71330F),
        secondary = Color(0xFF755746), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E5D9), onSecondaryContainer = Color(0xFF473428),
        tertiary = Color(0xFF426853), tertiaryContainer = Color(0xFFDCEBE1), onTertiaryContainer = Color(0xFF253E30),
        background = Color(0xFFFCFAF7), onBackground = Color(0xFF292621),
        surface = Color(0xFFFFFDFB), onSurface = Color(0xFF292621),
        surfaceVariant = Color(0xFFF5F1EA), onSurfaceVariant = Color(0xFF746E66),
        outline = Color(0xFF8C8278), outlineVariant = Color(0xFFE8DFD5)
    )
}

@Composable
internal fun DesktopPreferencesPage(controller: DesktopAppController, appearance: Boolean) {
    var mode by remember { mutableStateOf(controller.settings.themeMode) }
    var accent by remember { mutableStateOf(controller.settings.accentHex) }
    var scale by remember { mutableFloatStateOf(controller.settings.fontScale) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("外观", style = MaterialTheme.typography.headlineMedium)
        Text("立即预览，自动保存。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("显示主题", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("light" to "暖白", "dark" to "深色", "system" to "跟随系统").forEach { (key, label) ->
                FilterChip(selected = mode == key, onClick = { mode = key; controller.applyAppearance(mode, accent, scale) }, label = { Text(label) })
            }
        }
        Text("强调色", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("B65326" to "杏橙", "356A57" to "松绿", "3764A0" to "雾蓝", "765294" to "藤紫").forEach { (key, label) ->
                FilterChip(selected = accent == key, onClick = { accent = key; controller.applyAppearance(mode, accent, scale) }, label = { Text(label) })
            }
        }
        Text("文字大小 · ${(scale * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        Slider(scale, { scale = it }, valueRange = .9f..1.3f, steps = 3,
            onValueChangeFinished = { controller.applyAppearance(mode, accent, scale) })
        Text("预览：文件准备好了，开始下一次下载。", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { mode = "light"; accent = "B65326"; scale = 1f; controller.applyAppearance(mode, accent, scale) }) { Text("恢复默认外观") }
    }
}

@Composable
internal fun DesktopDownloadPreferences(controller: DesktopAppController) {
    val prefs = controller.settings
    var concurrent by remember { mutableStateOf(prefs.maxConcurrentTasks.toString()) }
    var speed by remember { mutableStateOf((prefs.speedLimitBytes / 1024).toString()) }
    var retries by remember { mutableStateOf(prefs.retryLimit.toString()) }
    var quality by remember { mutableStateOf(prefs.mediaQuality) }
    var name by remember { mutableStateOf(prefs.mediaNameRule) }
    var wifi by remember { mutableStateOf(prefs.wifiOnly) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("下载与视频偏好", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(concurrent, { concurrent = it }, Modifier.weight(1f), label = { Text("同时任务数 1–8") }, singleLine = true)
            OutlinedTextField(speed, { speed = it }, Modifier.weight(1f), label = { Text("限速 KB/s · 0 不限") }, singleLine = true)
            OutlinedTextField(retries, { retries = it }, Modifier.weight(1f), label = { Text("重试次数 0–10") }, singleLine = true)
        }
        Text("默认视频画质（只选择平台开放的格式）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("best" to "最高", "1080" to "1080p", "720" to "720p", "audio" to "仅音频").forEach { (key,label) ->
                FilterChip(quality == key, { quality = key }, label = { Text(label) })
            }
        }
        Text("视频文件命名")
        Row {
            Checkbox(wifi, { wifi = it })
            Text("仅 Wi-Fi 下载（未连接时暂停，重新连接后点继续）")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("title-id" to "标题 + ID", "title" to "标题", "uploader-title" to "作者 + 标题").forEach { (key,label) ->
                FilterChip(name == key, { name = key }, label = { Text(label) })
            }
        }
        Button(onClick = {
            message = runCatching {
                val c = concurrent.toInt(); val s = speed.toLong(); val retry = retries.toInt()
                require(c in 1..8 && s in 0..1048576 && retry in 0..10) { "请填写有效范围内的整数" }
                prefs.maxConcurrentTasks = c; prefs.speedLimitBytes = s * 1024; prefs.retryLimit = retry
                prefs.mediaQuality = quality; prefs.mediaNameRule = name; prefs.wifiOnly = wifi
                "已保存；新设置应用于后续启动的任务"
            }.getOrElse { it.message ?: "设置无效" }
        }) { Text("保存下载偏好") }
        message?.let { Text(it) }
    }
}
