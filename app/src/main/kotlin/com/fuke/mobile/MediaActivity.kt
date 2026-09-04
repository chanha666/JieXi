package com.fuke.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.yunx.app.BuildConfig
import com.yunx.app.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MediaActivity : ComponentActivity() {
    private val incomingShare = MutableStateFlow(SharedPayload())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readSharedText(intent)
        setContent {
            FukeTheme {
                FukeRoot(incomingShare.collectAsState().value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        val text = when {
            intent == null -> ""
            intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            else -> intent.getStringExtra(EXTRA_SHARED_TEXT).orEmpty()
        }
        if (text.isNotBlank()) incomingShare.value = SharedPayload(text, System.nanoTime())
    }

    companion object {
        const val EXTRA_SHARED_TEXT = "com.yunx.app.extra.MEDIA_SHARED_TEXT"
    }
}

private data class SharedPayload(val text: String = "", val token: Long = 0L)

private enum class Screen(val label: String, val glyph: String) {
    HOME("下载", "↓"), TASKS("任务", "≡"), TOOLS("工具", "◇"), SETTINGS("设置", "⚙")
}

@Composable
private fun FukeRoot(shared: SharedPayload) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var showSponsor by rememberSaveable { mutableStateOf(false) }
    val tasks by TaskStore.tasks.collectAsState()
    val coreState by Engine.state.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT in 24..28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
    Scaffold(
        containerColor = Cream,
        topBar = { AppTopBar(screen, coreState) { showSponsor = true } },
        bottomBar = { BottomBar(screen) { screen = it } }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).imePadding()) {
            when (screen) {
                Screen.HOME -> DownloadScreen(shared.text, shared.token) { screen = Screen.TASKS }
                Screen.TASKS -> TasksScreen(tasks)
                Screen.TOOLS -> ToolsScreen()
                Screen.SETTINGS -> SettingsScreen(coreState)
            }
        }
    }
    if (showSponsor) SponsorDialog { showSponsor = false }
}

@Composable
private fun AppTopBar(screen: Screen, coreState: String, onSponsor: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cream).statusBarsPadding().height(64.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FukeMark(34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(if (screen == Screen.HOME) "解析 · 媒体" else screen.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (screen == Screen.HOME) "公开资源 · 最高开放画质" else coreState, fontSize = 10.sp, color = Muted, maxLines = 1)
        }
        if (screen == Screen.HOME) {
            TextButton(onClick = onSponsor, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("♡ 赞赏", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(BuildConfig.VERSION_NAME, fontSize = 10.sp, color = Orange, modifier = Modifier.border(1.dp, Line, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun SponsorDialog(onDismiss: () -> Unit) {
    var enlargedImage by remember { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = { if (enlargedImage != 0) enlargedImage = 0 else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (enlargedImage != 0) {
            Box(
                Modifier.fillMaxSize().background(Color(0xD93A2A20)).clickable { enlargedImage = 0 }.padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(enlargedImage),
                    contentDescription = "赞赏二维码大图",
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(.9f).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Fit
                )
                Text("点击空白处返回", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Paper),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("支持解析", color = Orange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(5.dp))
                                Text("如果你觉得软件好用，欢迎支持一下作者", fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
                            }
                            TextButton(onClick = onDismiss) { Text("关闭", color = Muted) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("你的鼓励会用于解析器维护、网站兼容更新和使用体验改进，让“解析”继续保持简单、稳定和好用。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("♡", color = Orange, fontSize = 20.sp)
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text("完全自愿", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("不会解锁额外功能，也不影响下载、画质或更新。", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                    item { SponsorCodeCard("支付宝", "打开支付宝扫一扫", R.drawable.donate_alipay, Color(0xFF1677FF)) { enlargedImage = R.drawable.donate_alipay } }
                    item { SponsorCodeCard("微信赞赏", "打开微信扫一扫", R.drawable.donate_wechat, Color(0xFFD4A92D)) { enlargedImage = R.drawable.donate_wechat } }
                    item {
                        Text("二维码在本机上时，可先点击放大并截图，再从支付应用的相册识别；也可以使用另一台设备扫码。", color = Muted, fontSize = 10.sp, lineHeight = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("感谢每一份支持，也感谢你愿意使用“解析”。", color = Orange, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorCodeCard(title: String, subtitle: String, image: Int, accent: Color, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent), contentAlignment = Alignment.Center) {
                    Text(if (title == "支付宝") "支" else "赏", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 10.sp, color = Muted) }
            }
            Spacer(Modifier.height(12.dp))
            Image(
                painter = painterResource(image),
                contentDescription = "$title 赞赏二维码",
                modifier = Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF8F8F8)).clickable(onClick = onOpen),
                contentScale = ContentScale.Fit
            )
            Text("点击二维码放大查看", fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun BottomBar(selected: Screen, onSelect: (Screen) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Paper).border(1.dp, Line).navigationBarsPadding().height(66.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screen.entries.forEach { item ->
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(item.glyph, fontSize = 18.sp, color = if (item == selected) Orange else Muted)
                Text(item.label, fontSize = 10.sp, fontWeight = if (item == selected) FontWeight.SemiBold else FontWeight.Normal, color = if (item == selected) Orange else Muted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadScreen(sharedText: String, sharedToken: Long, showTasks: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var input by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<VideoPreview?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf<FormatChoice?>(null) }
    var formatMenu by remember { mutableStateOf(false) }
    var embedSubtitles by rememberSaveable { mutableStateOf(false) }
    val settings = AppPrefs.read(context)

    LaunchedEffect(sharedToken) { if (sharedText.isNotBlank()) input = sharedText }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 22.dp, 18.dp, 42.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                FukeMark(58.dp)
                Spacer(Modifier.height(14.dp))
                Text("把喜欢的视频，清晰地留在手机。", fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("无需登录 · 自动获取匿名公开的最高视频与音频", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
            }
        }
        item {
            OutlineCard {
                Text("视频链接或分享文字", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("粘贴一个或多个链接；抖音可直接粘贴整段分享文字", fontSize = 12.sp) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(
                        onClick = { input = clipboard.getText()?.text.orEmpty() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp)
                    ) { Text("粘贴") }
                    PrimaryButton(
                        text = if (analyzing) "正在解析" else "解析链接",
                        enabled = !analyzing,
                        modifier = Modifier.weight(1.35f)
                    ) {
                        val url = extractUrls(input).firstOrNull()
                        if (url == null) { error = "没有检测到完整的视频网址。"; return@PrimaryButton }
                        analyzing = true; error = ""; preview = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { analyzeVideo(context, url) } }
                                .onSuccess { preview = it; selectedFormat = it.formats.firstOrNull() }
                                .onFailure { error = cleanUiError(it) }
                            analyzing = false
                        }
                    }
                }
                if (analyzing) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Orange, trackColor = OrangeSoft)
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = Danger, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        item {
            Text("常用平台", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("YouTube", "哔哩哔哩", "抖音", "X", "TikTok", "小红书", "微博", "直链流")) { platform -> PlatformChip(platform) }
            }
        }
        preview?.let { info ->
            item {
                OutlineCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = "视频封面",
                            modifier = Modifier.size(112.dp, 72.dp).clip(RoundedCornerShape(10.dp)).background(OrangeSoft)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.platform, color = Orange, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(info.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOf(info.uploader, formatDuration(info.durationSeconds), "${info.formats.size} 种画质").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 10.sp, color = Muted, maxLines = 1)
                            if (info.platform in setOf("抖音", "TikTok", "小红书", "微博", "X")) {
                                Text("✓ 已自动优先提取无水印源", color = Success, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                OutlineCard {
                    Text("下载配置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(onClick = { formatMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Text(selectedFormat?.label ?: "自动选择最高画质", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("⌄")
                        }
                        DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                            info.formats.forEach { choice ->
                                DropdownMenuItem(text = { Text(choice.label, fontSize = 12.sp) }, onClick = { selectedFormat = choice; formatMenu = false })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = embedSubtitles, onCheckedChange = { embedSubtitles = it })
                        Column {
                            Text("尝试嵌入中英文字幕", fontSize = 12.sp)
                            Text("只有网站公开提供字幕时才会生效", fontSize = 10.sp, color = Muted)
                        }
                    }
                }
            }
        }
        item {
            PrimaryButton(text = "加入队列并开始下载", modifier = Modifier.fillMaxWidth(), enabled = extractUrls(input).isNotEmpty()) {
                val urls = extractUrls(input)
                val chosen = selectedFormat ?: defaultChoice(settings.defaultQuality)
                val count = TaskStore.addAll(urls.map { url ->
                    DownloadTask(
                        url = url,
                        resolvedUrl = if (preview?.url == url) preview!!.downloadUrl else "",
                        title = if (preview?.url == url) preview!!.title else "等待获取视频标题",
                        platform = detectPlatform(url),
                        qualityLabel = chosen.label,
                        formatSelector = if (preview?.url == url && preview!!.downloadUrl.isNotBlank() && chosen.selector != "bestaudio/best") "best" else chosen.selector,
                        outputFormat = if (chosen.selector == "bestaudio/best") "mp3" else "mp4",
                        embedSubtitles = embedSubtitles
                    )
                })
                if (count > 0) {
                    DownloadService.start(context)
                    Toast.makeText(context, "已加入 $count 个任务", Toast.LENGTH_SHORT).show()
                    showTasks()
                } else Toast.makeText(context, "这些链接已经在任务中", Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Text("公开视频提供 4K 或 8K 时可以下载；登录专属、会员画质和 DRM 加密内容不会要求你登录。", fontSize = 10.sp, lineHeight = 16.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun TasksScreen(tasks: List<DownloadTask>) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    val filtered = tasks.filter { task ->
        val text = listOf(task.title, task.platform, task.url).joinToString(" ").lowercase(Locale.getDefault())
        val statusMatch = filter == "全部" || when (filter) {
            "进行中" -> task.status in setOf(TaskStatus.QUEUED, TaskStatus.RUNNING)
            "已暂停" -> task.status == TaskStatus.PAUSED
            "已完成" -> task.status == TaskStatus.COMPLETED
            "失败" -> task.status == TaskStatus.FAILED
            else -> true
        }
        statusMatch && (query.isBlank() || text.contains(query.lowercase(Locale.getDefault())))
    }.sortedWith(compareBy<DownloadTask> { statusRank(it.status) }.thenByDescending { it.updatedAt })

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("任务与历史", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("排队、暂停续传和已完成文件都在这里", fontSize = 11.sp, color = Muted)
            }
            TextButton(onClick = { DownloadService.clearFinished(context) }) { Text("清理完成") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("等待", tasks.count { it.status == TaskStatus.QUEUED }, Modifier.weight(1f))
            SummaryBox("下载中", tasks.count { it.status == TaskStatus.RUNNING }, Modifier.weight(1f))
            SummaryBox("已暂停", tasks.count { it.status == TaskStatus.PAUSED }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索标题、平台或链接") }, shape = RoundedCornerShape(11.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 9.dp)) {
            items(listOf("全部", "进行中", "已暂停", "已完成", "失败")) { label ->
                FilterChip(label, filter == label) { filter = label }
            }
        }
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("↓", fontSize = 34.sp, color = Orange)
                Text(if (tasks.isEmpty()) "还没有下载任务" else "没有符合条件的记录", fontWeight = FontWeight.SemiBold)
                Text("从下载页粘贴链接即可开始", color = Muted, fontSize = 11.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
                items(filtered, key = { it.id }) { task -> TaskCard(task, context) }
            }
        }
    }
}

@Composable
private fun TaskCard(task: DownloadTask, context: android.content.Context) {
    OutlineCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).border(1.dp, if (task.status == TaskStatus.FAILED) Danger.copy(alpha = .45f) else Line, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(statusGlyph(task.status), color = if (task.status == TaskStatus.COMPLETED) Success else Orange, fontSize = 18.sp)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(statusLabel(task.status), fontSize = 9.sp, color = statusColor(task.status), modifier = Modifier.border(1.dp, statusColor(task.status).copy(alpha = .3f), CircleShape).padding(horizontal = 7.dp, vertical = 3.dp))
                }
                Text(
                    listOf(task.platform, task.qualityLabel, task.stage, task.speed).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 10.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                if (task.status == TaskStatus.RUNNING && task.progress == 0 && task.totalBytes <= 0L) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = Orange, trackColor = OrangeSoft)
                } else {
                    LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = if (task.status == TaskStatus.FAILED) Danger else Orange, trackColor = OrangeSoft)
                }
                if (task.downloadedBytes > 0L) {
                    Text(
                        if (task.totalBytes > 0L) "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}" else formatBytes(task.downloadedBytes),
                        fontSize = 9.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                if (task.error.isNotBlank()) Text(task.error, fontSize = 10.sp, lineHeight = 14.sp, color = if (task.status == TaskStatus.FAILED) Danger else Muted, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (task.status) {
                        TaskStatus.RUNNING -> {
                            TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_PAUSE, task.id) }) { Text("暂停") }
                            TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_CANCEL, task.id) }) { Text("取消", color = Danger) }
                        }
                        TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.CANCELED -> {
                            TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_CONTINUE, task.id) }) { Text(if (task.status == TaskStatus.PAUSED) "继续" else "重试") }
                            TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_REMOVE, task.id) }) { Text("移除", color = Danger) }
                        }
                        TaskStatus.COMPLETED -> {
                            TextButton(onClick = { openMedia(context, task.fileUri) }) { Text("打开文件") }
                            TextButton(onClick = { shareMedia(context, task.fileUri) }) { Text("分享") }
                            TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_REMOVE, task.id) }) { Text("移除", color = Danger) }
                        }
                        TaskStatus.QUEUED -> TextButton(onClick = { DownloadService.action(context, DownloadService.ACTION_REMOVE, task.id) }) { Text("移除", color = Danger) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf("audio") }
    var busy by remember { mutableStateOf(false) }
    var screenshotSeconds by rememberSaveable { mutableStateOf("0.5") }
    var watermarkSource by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = MediaFiles.copyToCache(context, uri, "tool-input")
                    if (pending == "watermark") {
                        input
                    } else if (pending == "audio") {
                        Engine.initializeDownloadTools(context)
                        val output = File(context.cacheDir, "解析音频-${System.currentTimeMillis()}.mp3")
                        MediaFiles.runFfmpeg(context, listOf("-i", input.absolutePath, "-vn", "-c:a", "libmp3lame", "-q:a", "0", output.absolutePath))
                        MediaFiles.publish(context, output)
                    } else {
                        Engine.initializeDownloadTools(context)
                        val output = File(context.cacheDir, "解析截图-${System.currentTimeMillis()}.jpg")
                        val second = screenshotSeconds.toDoubleOrNull()?.coerceIn(0.0, 86400.0) ?: .5
                        MediaFiles.runFfmpeg(context, listOf("-ss", second.toString(), "-i", input.absolutePath, "-frames:v", "1", "-q:v", "2", output.absolutePath))
                        MediaFiles.publish(context, output)
                    }
                }
            }.onSuccess { result ->
                if (pending == "watermark") watermarkSource = result as File
                else Toast.makeText(context, "处理完成，已保存到下载/解析", Toast.LENGTH_LONG).show()
            }
                .onFailure { Toast.makeText(context, cleanUiError(it), Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("实用工具", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("下载之外常用的本地媒体处理", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(14.dp))
        OutlineCard {
            Text("截图时间点", fontSize = 11.sp, color = Muted)
            OutlinedTextField(value = screenshotSeconds, onValueChange = { screenshotSeconds = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, suffix = { Text("秒") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Spacer(Modifier.height(12.dp))
        val tools = listOf(
            Triple("⌫", "图片去水印", "框选图片水印，本地智能填充并保存"),
            Triple("♫", "提取 MP3 音频", "从本地视频导出高质量音频"),
            Triple("▧", "视频截图", "按上方时间截取 JPG 画面"),
            Triple("↗", "打开成品", "查看下载/解析中的全部文件"),
            Triple("i", "生成诊断报告", "导出组件、任务和设备信息")
        )
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(tools) { tool ->
                ToolCard(tool.first, tool.second, tool.third, busy && (tool.second.contains("音频") || tool.second.contains("截图"))) {
                    when {
                        tool.second.contains("去水印") -> { pending = "watermark"; picker.launch(arrayOf("image/*")) }
                        tool.second.contains("音频") -> { pending = "audio"; picker.launch(arrayOf("video/*")) }
                        tool.second.contains("截图") -> { pending = "screenshot"; picker.launch(arrayOf("video/*")) }
                        tool.second.contains("打开") -> openDownloads(context)
                        else -> scope.launch {
                            runCatching { withContext(Dispatchers.IO) { exportDiagnostics(context) } }
                                .onSuccess { Toast.makeText(context, "诊断报告已保存", Toast.LENGTH_LONG).show() }
                                .onFailure { Toast.makeText(context, cleanUiError(it), Toast.LENGTH_LONG).show() }
                        }
                    }
                }
            }
        }
    }
    watermarkSource?.let { source ->
        ImageWatermarkDialog(
            context = context,
            source = source,
            onDismiss = { source.delete(); watermarkSource = null },
            onSaved = {
                watermarkSource = null
                Toast.makeText(context, "去水印图片已保存到下载/解析", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
private fun SettingsScreen(coreState: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppPrefs.read(context)) }
    var qualityMenu by remember { mutableStateOf(false) }
    var retryMenu by remember { mutableStateOf(false) }
    var nameMenu by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 16.dp, 16.dp, 34.dp)) {
        Text("软件设置", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("管理默认画质、网络与下载核心", fontSize = 11.sp, color = Muted)
        Spacer(Modifier.height(14.dp))
        SectionLabel("下载")
        SettingsCard {
            SettingRow("清", "默认清晰度", qualityLabel(settings.defaultQuality)) { qualityMenu = true }
            DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                listOf("best" to "匿名最高画质", "1080" to "最高 1080p", "720" to "最高 720p", "audio" to "仅音频 MP3").forEach { item ->
                    DropdownMenuItem(text = { Text(item.second) }, onClick = { settings = settings.copy(defaultQuality = item.first); AppPrefs.write(context, settings); qualityMenu = false })
                }
            }
            HorizontalDivider(color = Line)
            SettingRow("试", "失败重试次数", "${settings.retries} 次") { retryMenu = true }
            DropdownMenu(expanded = retryMenu, onDismissRequest = { retryMenu = false }) {
                listOf(1, 3, 5, 10).forEach { count -> DropdownMenuItem(text = { Text("$count 次") }, onClick = { settings = settings.copy(retries = count); AppPrefs.write(context, settings); retryMenu = false }) }
            }
            HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingGlyph("网")
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) { Text("仅 Wi-Fi 下载", fontSize = 13.sp, fontWeight = FontWeight.Medium); Text("避免意外消耗移动数据", fontSize = 10.sp, color = Muted) }
                Switch(checked = settings.wifiOnly, onCheckedChange = { settings = settings.copy(wifiOnly = it); AppPrefs.write(context, settings) })
            }
            HorizontalDivider(color = Line)
            SettingRow("名", "文件命名", fileNameLabel(settings.fileNameRule)) { nameMenu = true }
            DropdownMenu(expanded = nameMenu, onDismissRequest = { nameMenu = false }) {
                listOf("title-id" to "标题 + 视频 ID", "title" to "仅标题", "uploader-title" to "作者 + 标题").forEach { item -> DropdownMenuItem(text = { Text(item.second) }, onClick = { settings = settings.copy(fileNameRule = item.first); AppPrefs.write(context, settings); nameMenu = false }) }
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("保存")
        SettingsCard {
            SettingRow("夹", "成品文件夹", "手机存储/下载/解析") { openDownloads(context) }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("支持")
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { SettingGlyph("核"); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text("下载核心", fontSize = 13.sp, fontWeight = FontWeight.Medium); Text(coreState, fontSize = 10.sp, color = Muted) } }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(onClick = {
                    updating = true
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { Engine.initialize(context); YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY) } }
                            .onSuccess { Toast.makeText(context, "下载核心已经是最新版本", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, cleanUiError(it), Toast.LENGTH_LONG).show() }
                        updating = false
                    }
                }, enabled = !updating, modifier = Modifier.fillMaxWidth()) { Text(if (updating) "正在更新…" else "检查并更新下载核心") }
            }
            HorizontalDivider(color = Line)
            SettingRow("邮", "问题反馈", "3316109338@qq.com") { openFeedback(context) }
            HorizontalDivider(color = Line)
            SettingRow("权", "系统权限", "通知权限与应用设置") { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel("关于")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                FukeMark(40.dp); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("解析 Android", fontWeight = FontWeight.SemiBold); Text("网盘、公开视频下载与本地媒体工具", fontSize = 10.sp, color = Muted) }; Text(BuildConfig.VERSION_NAME, color = Orange, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun OutlineCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Paper), border = androidx.compose.foundation.BorderStroke(1.dp, Line), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), content = content)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) = OutlineCard(content)

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(120, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)), label = "buttonPress")
    Button(onClick = onClick, enabled = enabled, modifier = modifier.scale(scale).height(50.dp), interactionSource = interaction, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun FukeMark(size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(R.drawable.icon),
        contentDescription = "解析",
        modifier = Modifier.size(size)
    )
}

@Composable
private fun PlatformChip(label: String) { Row(Modifier.border(1.dp, Line, RoundedCornerShape(11.dp)).background(Paper, RoundedCornerShape(11.dp)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Text(label.take(1), color = Orange, fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text(label, fontSize = 11.sp) } }

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) { Text(label, fontSize = 10.sp, color = if (active) Orange else Muted, modifier = Modifier.clip(CircleShape).background(if (active) OrangeSoft else Paper).border(1.dp, if (active) Orange.copy(alpha = .3f) else Line, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)) }

@Composable
private fun SummaryBox(label: String, count: Int, modifier: Modifier) { Column(modifier.border(1.dp, Line, RoundedCornerShape(11.dp)).background(Paper, RoundedCornerShape(11.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(count.toString(), color = Orange, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(label, color = Muted, fontSize = 9.sp) } }

@Composable
private fun ToolCard(glyph: String, title: String, body: String, busy: Boolean, onClick: () -> Unit) { val interaction = remember { MutableInteractionSource() }; val pressed by interaction.collectIsPressedAsState(); val scale by animateFloatAsState(if (pressed) .975f else 1f, tween(120, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)), label = "cardPress"); Column(Modifier.fillMaxWidth().height(172.dp).scale(scale).border(1.dp, Line, RoundedCornerShape(16.dp)).background(Paper, RoundedCornerShape(16.dp)).clickable(interactionSource = interaction, indication = null, enabled = !busy, onClick = onClick).padding(15.dp)) { Text(glyph, color = Orange, fontSize = 24.sp); Spacer(Modifier.height(18.dp)); Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, fontSize = 10.sp, lineHeight = 15.sp); Spacer(Modifier.weight(1f)); Text(if (busy) "处理中…" else "开始使用 →", color = Orange, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun SectionLabel(text: String) { Text(text, fontSize = 11.sp, color = Orange, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 3.dp, bottom = 7.dp)) }

@Composable
private fun SettingGlyph(text: String) { Box(Modifier.size(34.dp).border(1.dp, Line, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text(text, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }

@Composable
private fun SettingRow(glyph: String, title: String, value: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { SettingGlyph(glyph); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium); Text(value, fontSize = 10.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text("›", color = Orange, fontSize = 20.sp) } }

private fun extractUrls(text: String): List<String> = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE).findAll(text).map { it.value.trimEnd('，', '。', '；', '！', ')', '）', '】') }.distinct().toList()

private fun detectPlatform(url: String): String {
    val normalized = url.lowercase(Locale.ROOT)
    return when {
        "youtu" in normalized -> "YouTube"
        "bilibili" in normalized || "b23.tv" in normalized -> "哔哩哔哩"
        "douyin" in normalized -> "抖音"
        "x.com" in normalized || "twitter.com" in normalized -> "X"
        "tiktok" in normalized -> "TikTok"
        "xiaohongshu" in normalized || "xhslink" in normalized -> "小红书"
        "weibo" in normalized -> "微博"
        DirectMedia.isVideo(normalized) -> "直链视频"
        ".m3u8" in normalized || ".mpd" in normalized -> "直链流"
        else -> "其他网站"
    }
}

private fun defaultChoice(quality: String) = when (quality) {
    "1080" -> FormatChoice("bestvideo[height<=1080]+bestaudio/best[height<=1080]", "最高 1080p")
    "720" -> FormatChoice("bestvideo[height<=720]+bestaudio/best[height<=720]", "最高 720p")
    "audio" -> FormatChoice("bestaudio/best", "仅音频 MP3")
    else -> FormatChoice("bestvideo+bestaudio/best", "匿名最高画质")
}

private fun analyzeVideo(context: android.content.Context, url: String): VideoPreview {
    val startedAt = System.currentTimeMillis()
    AnalysisCache.get(url)?.let {
        Log.i("FukeAnalyze", "cache hit in ${System.currentTimeMillis() - startedAt}ms: ${detectPlatform(url)}")
        return it
    }
    fun finish(preview: VideoPreview): VideoPreview {
        AnalysisCache.put(url, preview)
        Log.i("FukeAnalyze", "completed in ${System.currentTimeMillis() - startedAt}ms: ${preview.platform}")
        return preview
    }
    val platform = detectPlatform(url)
    if (DirectMedia.isVideo(url)) {
        return finish(DirectMedia.analyze(url))
    }
    if (platform == "YouTube") {
        return finish(YouTubeFallback.analyze(url))
    }
    if (platform == "哔哩哔哩") {
        return finish(BilibiliPublicFallback.analyze(url))
    }
    if (platform == "抖音") {
        return finish(DouyinFallback.analyze(url))
    }
    if (platform == "X") {
        runCatching { XFallback.analyze(url) }.getOrNull()?.let {
            return finish(it)
        }
    }
    Engine.initialize(context)
    val request = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--skip-download")
        addOption("--no-check-formats")
        addOption("--no-warnings")
        addOption("--retries", 0)
        addOption("--socket-timeout", 12)
        addOption("--force-ipv4")
        if (platform == "哔哩哔哩") {
            addOption("--referer", "https://www.bilibili.com/")
            addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
        }
        if (platform == "X") addOption("--extractor-args", "twitter:api=syndication")
    }
    val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
    val detailed = info.formats.orEmpty()
        .filter { it.height > 0 && !it.vcodec.equals("none", true) }
        .sortedWith(compareByDescending<com.yausername.youtubedl_android.mapper.VideoFormat> { it.height }.thenByDescending { it.fps }.thenByDescending { it.fileSize.takeIf { size -> size > 0 } ?: it.fileSizeApproximate })
        .distinctBy { "${it.height}-${it.fps}-${it.vcodec}-${it.ext}" }
        .take(24)
        .mapNotNull { format ->
            val formatId = format.formatId ?: return@mapNotNull null
            val size = format.fileSize.takeIf { it > 0 } ?: format.fileSizeApproximate
            val label = buildList {
                add("${format.height}p")
                if (format.fps > 30) add("${format.fps}fps")
                format.vcodec?.takeIf { it.isNotBlank() }?.let { add(it.substringBefore('.').uppercase()) }
                format.ext?.let { add(it.uppercase()) }
                if (size > 0) add(formatBytes(size))
            }.joinToString(" · ")
            val selector = if (format.acodec.equals("none", true)) "$formatId+bestaudio/best" else formatId
            FormatChoice(selector, label, size)
        }
    val formats = listOf(defaultChoice("best"), defaultChoice("1080"), defaultChoice("720"), defaultChoice("audio")) + detailed
    return finish(VideoPreview(
        url = url,
        title = info.title ?: info.fulltitle ?: "未命名视频",
        uploader = info.uploader.orEmpty(),
        platform = if (detectPlatform(url) == "X") "X" else info.extractorKey ?: detectPlatform(url),
        durationSeconds = info.duration,
        thumbnail = info.thumbnail.orEmpty(),
        formats = formats
    ))
}

private fun statusRank(status: TaskStatus) = when (status) { TaskStatus.RUNNING -> 0; TaskStatus.QUEUED -> 1; TaskStatus.PAUSED -> 2; TaskStatus.FAILED -> 3; TaskStatus.COMPLETED -> 4; TaskStatus.CANCELED -> 5 }
private fun statusLabel(status: TaskStatus) = when (status) { TaskStatus.QUEUED -> "等待"; TaskStatus.RUNNING -> "下载中"; TaskStatus.PAUSED -> "已暂停"; TaskStatus.COMPLETED -> "已完成"; TaskStatus.FAILED -> "失败"; TaskStatus.CANCELED -> "已取消" }
private fun statusGlyph(status: TaskStatus) = when (status) { TaskStatus.COMPLETED -> "✓"; TaskStatus.FAILED -> "!"; TaskStatus.PAUSED -> "Ⅱ"; TaskStatus.RUNNING -> "↓"; else -> "▶" }
private fun statusColor(status: TaskStatus) = when (status) { TaskStatus.COMPLETED -> Success; TaskStatus.FAILED -> Danger; TaskStatus.PAUSED -> Color(0xFFA65A18); else -> Orange }
private fun formatDuration(seconds: Int) = if (seconds <= 0) "" else "%d:%02d".format(seconds / 60, seconds % 60)
private fun formatBytes(bytes: Long): String = if (bytes >= 1024L * 1024L * 1024L) "%.1f GB".format(bytes / 1073741824.0) else "%.0f MB".format(bytes / 1048576.0)
private fun qualityLabel(value: String) = when (value) { "1080" -> "最高 1080p"; "720" -> "最高 720p"; "audio" -> "仅音频 MP3"; else -> "匿名最高画质" }
private fun fileNameLabel(value: String) = when (value) { "title" -> "仅标题"; "uploader-title" -> "作者 + 标题"; else -> "标题 + 视频 ID" }

private fun cleanUiError(error: Throwable): String {
    return MediaErrorMessages.forAction(error)
}

private fun openMedia(context: android.content.Context, uriText: String) {
    if (uriText.isBlank()) {
        Toast.makeText(context, "这个任务没有可打开的成品文件", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = Uri.parse(uriText)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, MediaFiles.mimeType(context, uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure { Toast.makeText(context, "文件可能已被移动或删除", Toast.LENGTH_SHORT).show() }
}

private fun shareMedia(context: android.content.Context, uriText: String) {
    if (uriText.isBlank()) {
        Toast.makeText(context, "这个任务没有可分享的成品文件", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = Uri.parse(uriText)
        val share = Intent(Intent.ACTION_SEND)
            .setType(MediaFiles.mimeType(context, uri))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(share, "分享文件"))
    }.onFailure { Toast.makeText(context, "没有找到可分享文件的应用", Toast.LENGTH_SHORT).show() }
}

private fun openDownloads(context: android.content.Context) {
    runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "resource/folder")
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        context.startActivity(intent)
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
            .onFailure { Toast.makeText(context, "请在文件管理器中打开下载/解析", Toast.LENGTH_LONG).show() }
    }
}
private fun openFeedback(context: android.content.Context) { val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:3316109338@qq.com")).putExtra(Intent.EXTRA_SUBJECT, "解析 Android 问题反馈"); runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "反馈邮箱：3316109338@qq.com", Toast.LENGTH_LONG).show() } }

private fun exportDiagnostics(context: android.content.Context): Uri {
    val file = File(context.cacheDir, "解析Android故障报告-${System.currentTimeMillis()}.txt")
    val body = buildString {
        appendLine("解析 Android 媒体模块故障报告")
        appendLine("版本：${BuildConfig.VERSION_NAME}")
        appendLine("Android：${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("下载核心：${runCatching { YoutubeDL.getInstance().versionName(context) }.getOrNull() ?: "内置版本"}")
        appendLine("组件状态：${Engine.state.value}")
        appendLine("任务记录：")
        appendLine(MediaDiagnostics.taskSummary(TaskStore.tasks.value.takeLast(30)))
    }
    file.writeText(body)
    return MediaFiles.publish(context, file)
}
