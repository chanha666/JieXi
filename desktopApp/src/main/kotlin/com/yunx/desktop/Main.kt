package com.yunx.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.XunleiDeviceFingerprint
import com.yunx.app.data.network.model.ShareFile
import com.yunx.desktop.core.AppPage
import com.yunx.desktop.core.DesktopAppController
import com.yunx.desktop.core.DesktopResolver
import com.yunx.desktop.core.TaskState
import com.yunx.desktop.browser.EmbeddedLoginImporter
import com.yunx.desktop.security.CredentialKey
import com.yunx.desktop.settings.DesktopPreset
import com.yunx.desktop.update.DesktopRelease
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.ln
import kotlin.math.pow

private val Ink = Color(0xFF29231E)
private val Muted = Color(0xFF746B63)
private val Canvas = Color(0xFFFFFAF3)
private val Sidebar = Color(0xFFFFF1DE)
private val WarmCard = Color(0xFFFFFEFB)
private val Line = Color(0xFFE9DED1)
private val Accent = Color(0xFFD76524)
private val AccentStrong = Color(0xFFA94713)
private val AccentSoft = Color(0xFFFFE8D5)
private val Orange = Color(0xFFE8782D)
private val OrangeSoft = Color(0xFFFFEBDC)
private val Success = Color(0xFF128665)
private val SuccessSoft = Color(0xFFE1F6EE)
private val Warning = Color(0xFFB76A13)
private val WarningSoft = Color(0xFFFFF1D9)
private val Purple = Color(0xFF6B62D9)
private val Error = Color(0xFFB3261E)

private val DesktopTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private val YunXColors: ColorScheme
    @Composable get() = androidx.compose.material3.lightColorScheme(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = AccentSoft,
        onPrimaryContainer = Color(0xFF0B3B34),
        background = Canvas,
        onBackground = Ink,
        surface = WarmCard,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF6EFE6),
        onSurfaceVariant = Muted,
        outline = Line,
        error = Error
    )

fun main() = application {
    XunleiDeviceFingerprint.init()
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = DpSize(1180.dp, 780.dp)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "解析",
        icon = remember { loadDesktopIcon() },
        state = state
    ) {
        MaterialTheme(colorScheme = YunXColors, typography = DesktopTypography) {
            Surface(Modifier.fillMaxSize(), color = Canvas) {
                YunXDesktopApp(remember { DesktopAppController() })
            }
        }
    }
}

private fun loadDesktopIcon(): BitmapPainter {
    val bytes = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")) {
        "Missing desktop icon resource"
    }.use { it.readBytes() }
    return BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}

private fun loadResourcePainter(resourceName: String): BitmapPainter {
    val bytes = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)) {
        "Missing resource: $resourceName"
    }.use { it.readBytes() }
    return BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}

@Composable
private fun YunXDesktopApp(controller: DesktopAppController) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFFFFFDF8), Color(0xFFFFF3E3), Canvas),
                radius = 920f
            )
        ).padding(10.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SidebarNavigation(controller.page) { controller.page = it }
            AnimatedContent(
                targetState = controller.page,
                transitionSpec = {
                    val duration = if (controller.settings.reduceMotion) 0 else 160
                    fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(WarmCard),
                label = "page"
            ) { page ->
                when (page) {
                    AppPage.RESOLVE -> ResolvePage(controller)
                    AppPage.DOWNLOADS -> DownloadsPage(controller)
                    AppPage.LIBRARY -> LibraryPage(controller)
                    AppPage.STATUS -> StatusPage(controller)
                    AppPage.ACCOUNTS -> AccountsPage(controller)
                    AppPage.SPONSOR -> SponsorPage()
                    AppPage.SETTINGS -> SettingsPage(controller)
                }
            }
        }
        controller.updateRelease?.let { release -> DesktopUpdateDialog(controller, release) }
    }
}

@Composable
private fun SidebarNavigation(selected: AppPage, onSelect: (AppPage) -> Unit) {
    Column(
        Modifier.width(224.dp).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(
            Brush.verticalGradient(listOf(Color(0xFFF0F7FB), Sidebar))
        ).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(WarmCard),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Link, null, tint = Accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("解析", fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
                    Text("高速资源工作台", fontSize = 11.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(28.dp))
            NavigationItem("解析", Icons.Outlined.Link, selected == AppPage.RESOLVE) { onSelect(AppPage.RESOLVE) }
            NavigationItem("下载", Icons.Outlined.Download, selected == AppPage.DOWNLOADS) { onSelect(AppPage.DOWNLOADS) }
            NavigationItem("历史收藏", Icons.Outlined.Save, selected == AppPage.LIBRARY) { onSelect(AppPage.LIBRARY) }
            NavigationItem("平台状态", Icons.Outlined.Hub, selected == AppPage.STATUS) { onSelect(AppPage.STATUS) }
            NavigationItem("云盘登录", Icons.Outlined.Cloud, selected == AppPage.ACCOUNTS) { onSelect(AppPage.ACCOUNTS) }
        }
        Column {
            Surface(color = SuccessSoft, shape = RoundedCornerShape(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Success))
                    Spacer(Modifier.width(8.dp))
                    Text("解析引擎就绪", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            NavigationItem("赞赏", Icons.Outlined.FavoriteBorder, selected == AppPage.SPONSOR) { onSelect(AppPage.SPONSOR) }
            NavigationItem("设置", Icons.Outlined.Settings, selected == AppPage.SETTINGS) { onSelect(AppPage.SETTINGS) }
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(11.dp))
            .background(if (selected) WarmCard else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Accent else Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun PageFrame(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, fontSize = 13.sp, color = Muted)
            }
            action?.invoke()
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun ResolvePage(controller: DesktopAppController) {
    PageFrame(
        "解析工作台",
        "粘贴分享链接，自动识别网盘并获取可下载文件",
        action = {
            TextButton(
                onClick = { controller.page = AppPage.SPONSOR },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AccentStrong)
            ) {
                Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("赞赏作者")
            }
        }
    ) {
        val result = controller.resolved
        if (result == null) {
            ResolveInput(controller)
        } else {
            ResolvedFiles(controller)
        }
    }
}

@Composable
private fun ResolveInput(controller: DesktopAppController) {
    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFD7E8)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.background(
                    Brush.linearGradient(
                        listOf(Color(0xFFF7FCFF), Color(0xFFECF6FF), Color(0xFFFFF8F1))
                    )
                ).padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(
                            Brush.linearGradient(listOf(Accent, Color(0xFF41A7F5)))
                        ),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Link, null, tint = Color.White, modifier = Modifier.size(23.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("粘贴链接，剩下的交给解析", fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                        Text("自动识别平台、提取码与文件目录，公开资源优先免登录", color = Muted, fontSize = 12.sp)
                    }
                    Surface(color = SuccessSoft, shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Verified, null, tint = Success, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("安全连接", color = Success, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = controller.linkText,
                    onValueChange = { controller.linkText = it },
                    modifier = Modifier.fillMaxWidth().height(104.dp),
                    placeholder = { Text("粘贴夸克、UC、迅雷、百度、139 或 123 云盘分享链接") },
                    enabled = !controller.isResolving,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = controller.password,
                        onValueChange = { controller.password = it },
                        modifier = Modifier.width(250.dp),
                        placeholder = { Text("提取码（可选）") },
                        enabled = !controller.isResolving,
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(color = Color.White.copy(alpha = 0.72f), shape = RoundedCornerShape(12.dp)) {
                        Text("支持 Ctrl+V 快速粘贴", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { readClipboardText()?.let { controller.linkText = it } },
                        enabled = !controller.isResolving,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("粘贴")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = controller::resolve,
                        enabled = controller.linkText.isNotBlank() && !controller.isResolving,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White)
                    ) {
                        if (controller.isResolving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(if (controller.isResolving) "正在解析" else "开始解析")
                    }
                }
            }
        }
        controller.resolveError?.let {
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFFFFECEA), shape = RoundedCornerShape(11.dp)) {
                Text(it, color = Error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                modifier = Modifier.weight(1.45f),
                colors = CardDefaults.cardColors(containerColor = WarmCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(17.dp)) {
                    Text("平台能力", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("链接会自动路由到对应解析器", color = Muted, fontSize = 10.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformChip("夸克", "匿名", Warning, WarningSoft)
                        PlatformChip("UC", "匿名", Warning, WarningSoft)
                        PlatformChip("迅雷", "授权", Purple, Color(0xFFECEAFF))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlatformChip("百度", "匿名", Warning, WarningSoft)
                        PlatformChip("139", "免登", Success, SuccessSoft)
                        PlatformChip("123", "匿名", Warning, WarningSoft)
                    }
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = WarmCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("实时状态", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    StatusLine(Icons.Outlined.Hub, "解析通道", "就绪", Accent)
                    StatusLine(Icons.Outlined.Memory, "并发引擎", "最高 64 路", Purple)
                    StatusLine(Icons.Outlined.Storage, "保存位置", File(controller.downloadDirectory).name.ifBlank { "已设置" }, Success)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    tint: Color,
    container: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(container), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, fontSize = 11.sp, color = Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlatformChip(text: String, status: String, tint: Color, container: Color) {
    Surface(color = WarmCard, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(7.dp))
            Surface(color = container, shape = RoundedCornerShape(12.dp)) {
                Text(status, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 9.sp, color = tint)
            }
        }
    }
}

@Composable
private fun ResolvedFiles(controller: DesktopAppController) {
    val result = controller.resolved ?: return
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { controller.goToPath(-1) }) { Text(result.session.title.ifBlank { "分享根目录" }) }
            controller.path.forEachIndexed { index, item ->
                Text("/", color = Muted)
                TextButton(onClick = { controller.goToPath(index) }) {
                    Text(item.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(DesktopResolver.platformName(result.platform), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = controller::clearResolution) { Text("解析新链接") }
        }
        controller.resolveError?.let { Text(it, color = Error, fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = WarmCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                    items(controller.currentFiles, key = { it.fid }) { file ->
                        FileRow(file, controller::openFolder, controller::download)
                        HorizontalDivider(color = Line)
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(3.dp)
                )
                if (controller.isResolving) {
                    Box(Modifier.fillMaxSize().background(WarmCard.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: ShareFile, onFolder: (ShareFile) -> Unit, onDownload: (ShareFile) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = file.isdir) { onFolder(file) }.padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (file.isdir) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            null,
            tint = if (file.isdir) Accent else Muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(file.fname, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            if (!file.isdir) Text(formatBytes(file.fsize), color = Muted, fontSize = 11.sp)
        }
        if (file.isdir) {
            Text("打开", color = Accent, fontSize = 12.sp)
        } else {
            OutlinedButton(onClick = { onDownload(file) }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("下载")
            }
        }
    }
}

@Composable
private fun DownloadsPage(controller: DesktopAppController) {
    PageFrame("下载", "智能并发、断点续传与文件完整性校验") {
        if (controller.downloads.isEmpty()) {
            EmptyState(Icons.Outlined.Download, "还没有下载任务", "解析分享链接后，选择文件开始下载")
        } else {
            val active = controller.downloads.count { it.state == TaskState.PREPARING || it.state == TaskState.DOWNLOADING }
            val completed = controller.downloads.count { it.state == TaskState.COMPLETED }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DownloadMetric("全部任务", controller.downloads.size.toString(), Accent, AccentSoft, Modifier.weight(1f))
                DownloadMetric("正在下载", active.toString(), Purple, Color(0xFFF3E9DF), Modifier.weight(1f))
                DownloadMetric("已经完成", completed.toString(), Success, SuccessSoft, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = controller::pauseAll) { Text("全部暂停") }
                Button(onClick = controller::resumeAll) { Text("全部继续") }
            }
            Spacer(Modifier.height(14.dp))
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                    items(controller.downloads, key = { it.id }) { task ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WarmCard),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(17.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = Muted)
                                    Spacer(Modifier.width(11.dp))
                                    Text(task.fileName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(taskStateName(task.state), color = if (task.state in setOf(TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT)) Error else Accent, fontSize = 12.sp)
                                    TextButton(onClick = { controller.setPriority(task, task.priority <= 0) }) { Text(if (task.priority > 0) "普通" else "置顶") }
                                    when (task.state) {
                                        TaskState.DOWNLOADING, TaskState.PREPARING, TaskState.WAITING, TaskState.RETRY_WAIT ->
                                            TextButton(onClick = { controller.pauseDownload(task) }) { Text("暂停") }
                                        TaskState.PAUSED, TaskState.INTERRUPTED, TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT ->
                                            TextButton(onClick = { controller.resumeDownload(task) }) { Text(if (task.state == TaskState.FAILED) "重试" else "继续") }
                                        else -> Unit
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { controller.removeDownload(task) }) {
                                        Icon(Icons.Outlined.DeleteOutline, "删除下载任务", tint = Muted, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    progress = { task.progress.fraction },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                                    color = Accent,
                                    trackColor = AccentSoft
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val detail = if (task.state in setOf(TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT)) task.error.orEmpty() else
                                        "${formatBytes(task.progress.downloaded)} / ${formatBytes(task.progress.total)}   ${formatSpeed(task.progress.bytesPerSecond)}"
                                    Text(detail, color = if (task.state == TaskState.FAILED) Error else Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    task.outputFile?.let { file ->
                                        TextButton(onClick = { controller.openFolder(file) }) {
                                            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text("打开目录")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun LibraryPage(controller: DesktopAppController) {
    PageFrame("历史收藏", "最近解析记录与长期收藏；提取码不会写入记录") {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SavedListCard("解析历史", controller.history.map { Triple(it.title, it.platform, it.link) }, Modifier.weight(1f),
                onOpen = controller::resolveSaved, onRemove = null, onClear = controller::clearHistory)
            SavedListCard("我的收藏", controller.favorites.map { Triple(it.title, it.platform, it.link) }, Modifier.weight(1f),
                onOpen = controller::resolveSaved,
                onRemove = { link -> controller.favorites.firstOrNull { it.link == link }?.let(controller::removeFavorite) }, onClear = null)
        }
    }
}

@Composable
private fun SavedListCard(
    title: String,
    entries: List<Triple<String, String, String>>,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
    onClear: (() -> Unit)?
) {
    Card(modifier.fillMaxHeight(), colors = CardDefaults.cardColors(WarmCard), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                onClear?.let { TextButton(onClick = it) { Text("清空") } }
            }
            if (entries.isEmpty()) EmptyState(Icons.Outlined.Save, "这里还是空的", "解析链接或收藏后会显示在这里")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.third }) { item ->
                    Surface(color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(item.third) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.first.ifBlank { "未命名分享" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.second, color = Muted, fontSize = 11.sp)
                            }
                            onRemove?.let { IconButton(onClick = { it(item.third) }) { Icon(Icons.Outlined.DeleteOutline, "删除收藏") } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPage(controller: DesktopAppController) {
    PageFrame("平台状态与诊断", "区分网络、账号和适配状态；“实验”不等于已通过真实账号验收") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("六个平台", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = controller::runDiagnostics, enabled = !controller.diagnosticRunning) {
                Text(if (controller.diagnosticRunning) "检测中…" else "运行诊断")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(controller.diagnostics, key = { it.name }) { item ->
                Card(colors = CardDefaults.cardColors(WarmCard), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AccentSoft), contentAlignment = Alignment.Center) {
                            Text(item.name.take(1), color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(item.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("适配：${item.support}", color = Warning, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                        Text("账号：${item.account}", color = Muted, fontSize = 12.sp, modifier = Modifier.width(104.dp))
                        Text(item.result, color = if (item.result == "网络正常") Success else Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadMetric(label: String, value: String, tint: Color, container: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(WarmCard), border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(container), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Bolt, null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(label, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AccountsPage(controller: DesktopAppController) {
    PageFrame("云盘登录", "在这里完成授权管理；登录信息由 Windows 在本机加密保存") {
        val state = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E7)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1D8BD)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(WarmCard), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.LockOpen, null, tint = Accent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("公开链接不登录也能先解析", fontWeight = FontWeight.SemiBold)
                                Text("遇到网盘限制时只需点“登录”。官方页面由软件内置组件打开，完成后点右上角导入即可。", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { CredentialCard(controller, "夸克网盘", CredentialKey.QUARK_COOKIE, "Cookie", "https://pan.quark.cn/") }
                item { CredentialCard(controller, "UC 网盘", CredentialKey.UC_COOKIE, "Cookie", "https://drive.uc.cn/") }
                item { CredentialCard(controller, "百度网盘", CredentialKey.BAIDU_COOKIE, "包含 BDUSS 的 Cookie", "https://pan.baidu.com/") }
                item { CredentialCard(controller, "139 网盘", CredentialKey.C139_COOKIE, "完整登录 Cookie", "https://yun.139.com/") }
                item { CredentialCard(controller, "123 云盘", CredentialKey.PAN123_TOKEN, "登录 Token", "https://www.123pan.com/") }
                item { XunleiCredentialCard(controller) }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun CredentialCard(controller: DesktopAppController, title: String, key: CredentialKey, hint: String, loginUrl: String) {
    var value by remember(key) { mutableStateOf(controller.credentialStore.get(key).orEmpty()) }
    var saved by remember(key) { mutableStateOf(controller.credentialStore.has(key)) }
    var browserMessage by remember(key) { mutableStateOf<String?>(null) }
    var browserError by remember(key) { mutableStateOf(false) }
    var embeddedBusy by remember(key) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val supportsDirectLogin = key != CredentialKey.PAN123_TOKEN
    val embeddedImporter = remember { EmbeddedLoginImporter(controller.cookieImporter) }

    fun handleEmbeddedLogin() {
        if (embeddedBusy) return
        coroutineScope.launch {
            embeddedBusy = true
            browserError = false
            browserMessage = "请在内置窗口完成登录，然后点击右上角“登录完成并导入”"
            runCatching {
                withContext(Dispatchers.IO) { embeddedImporter.loginAndImport(key, loginUrl) }
            }.onSuccess { imported ->
                value = imported
                controller.credentialStore.put(key, imported)
                saved = true
                browserMessage = "内置登录成功，授权已加密保存"
            }.onFailure { error ->
                browserError = true
                browserMessage = error.message ?: "内置登录失败"
            }
            embeddedBusy = false
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VpnKey, null, tint = if (saved) Accent else Muted)
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (saved) "已配置" else "未配置", color = if (saved) Accent else Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (supportsDirectLogin) {
                Surface(color = AccentSoft, shape = RoundedCornerShape(11.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("软件内登录", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("打开内置官方登录页，完成后自动保存授权", color = Muted, fontSize = 10.sp)
                        }
                        Button(
                            onClick = ::handleEmbeddedLogin,
                            enabled = !embeddedBusy,
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            if (embeddedBusy) {
                                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(if (saved) "重新登录" else "登录", fontSize = 11.sp)
                        }
                        if (saved) {
                            IconButton(onClick = {
                                controller.credentialStore.remove(key)
                                value = ""
                                saved = false
                                browserMessage = "已退出登录"
                            }) { Icon(Icons.Outlined.DeleteOutline, "退出登录", tint = Muted) }
                        }
                    }
                }
                browserMessage?.let {
                    Text(
                        it,
                        color = if (browserError) Error else Success,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(hint) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = {
                    controller.credentialStore.put(key, value.trim())
                    saved = value.isNotBlank()
                }) {
                    Icon(Icons.Outlined.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("保存")
                }
                IconButton(onClick = {
                    controller.credentialStore.remove(key)
                    value = ""
                    saved = false
                }) { Icon(Icons.Outlined.DeleteOutline, "删除凭证") }
                }
            }
        }
    }
}

@Composable
private fun XunleiCredentialCard(controller: DesktopAppController) {
    var access by remember { mutableStateOf(controller.credentialStore.get(CredentialKey.XUNLEI_ACCESS_TOKEN).orEmpty()) }
    var refresh by remember { mutableStateOf(controller.credentialStore.get(CredentialKey.XUNLEI_REFRESH_TOKEN).orEmpty()) }
    var saved by remember { mutableStateOf(access.isNotBlank()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VpnKey, null, tint = if (saved) Accent else Muted)
                Spacer(Modifier.width(10.dp))
                Text("迅雷网盘", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (saved) "已配置" else "未配置", color = if (saved) Accent else Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(access, { access = it }, Modifier.weight(1f), placeholder = { Text("Access Token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(refresh, { refresh = it }, Modifier.weight(1f), placeholder = { Text("Refresh Token（建议填写）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(onClick = {
                    controller.credentialStore.put(CredentialKey.XUNLEI_ACCESS_TOKEN, access.trim())
                    controller.credentialStore.put(CredentialKey.XUNLEI_REFRESH_TOKEN, refresh.trim())
                    saved = access.isNotBlank()
                }) { Text("保存") }
            }
        }
    }
}

@Composable
private fun SponsorPage() {
    PageFrame(
        title = "赞赏",
        subtitle = "如果解析帮你省下了时间，可以请作者喝杯饮料"
    ) {
        Column(Modifier.fillMaxSize()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentSoft.copy(alpha = 0.55f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0CFB4)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(WarmCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Redeem, null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column {
                        Text("自愿支持，不影响任何功能", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("软件保持免费使用；赞赏只是对持续维护和适配平台变化的鼓励。", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SponsorCodeCard(
                    modifier = Modifier.weight(1f),
                    title = "支付宝",
                    subtitle = "打开支付宝扫一扫",
                    resourceName = "donate_alipay.jpg",
                    accent = Color(0xFF1677FF)
                )
                SponsorCodeCard(
                    modifier = Modifier.weight(1f),
                    title = "微信赞赏",
                    subtitle = "打开微信扫一扫",
                    resourceName = "donate_wechat.jpg",
                    accent = Color(0xFFB68A19)
                )
            }
        }
    }
}

@Composable
private fun SponsorCodeCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    resourceName: String,
    accent: Color
) {
    val painter = remember(resourceName) { loadResourcePainter(resourceName) }
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(accent)
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(subtitle, color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = Color(0xFFFAFAF8),
                shape = RoundedCornerShape(15.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0ECE6))
            ) {
                Image(
                    painter = painter,
                    contentDescription = "$title 赞赏码",
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(controller: DesktopAppController) {
    var directory by remember { mutableStateOf(controller.downloadDirectory) }
    var threads by remember { mutableStateOf(controller.threadCount) }
    var message by remember { mutableStateOf<String?>(null) }
    var githubRepository by remember { mutableStateOf(controller.settings.githubRepositoryUrl) }
    PageFrame("设置", "显示、支持、诊断与桌面下载") {
      Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text("性能预设", fontWeight = FontWeight.Medium)
                Text("稳定适合网络波动，均衡适合日常，极速会占用更多带宽和连接", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DesktopPreset.STABLE, DesktopPreset.BALANCED, DesktopPreset.TURBO, DesktopPreset.SINGLE_TASK_MAX).forEach { preset ->
                        if (controller.settings.preset == preset) Button(onClick = { controller.applyPreset(preset); threads = controller.threadCount }) { Text(preset.label) }
                        else OutlinedButton(onClick = { controller.applyPreset(preset); threads = controller.threadCount }) { Text(preset.label) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("下载目录", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(directory, { directory = it }, Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = {
                        chooseDirectory(File(directory))?.let { directory = it.absolutePath }
                    }) { Text("选择文件夹") }
                }
                Spacer(Modifier.height(20.dp))
                Text("单任务连接数：$threads 路", fontWeight = FontWeight.Medium)
                Text("软件会按文件大小自动使用合适的并发数，小文件不会被强行切碎", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 16, 32, 48, 64).forEach { count ->
                        if (threads == count) Button(onClick = { threads = count }) { Text(count.toString()) }
                        else OutlinedButton(onClick = { threads = count }) { Text(count.toString()) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("动画效果", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (controller.settings.reduceMotion) Button(onClick = { controller.setReduceMotion(false) }) { Text("已减少动画") }
                    else OutlinedButton(onClick = { controller.setReduceMotion(true) }) { Text("减少动画") }
                }
                Spacer(Modifier.height(22.dp))
                Button(onClick = {
                    message = runCatching { controller.saveSettings(directory, threads); "设置已保存" }
                        .getOrElse { it.message ?: "保存失败" }
                }) { Text("保存设置") }
                message?.let { Text(it, color = if (it == "设置已保存") Accent else Error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("常规与支持", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                SettingsActionRow(Icons.Outlined.Palette, "显示主题", "护眼暖色 · 清晰字体 · 可减少动画") {
                    message = "当前使用护眼暖色主题；动画强度可在上方单独调整"
                }
                HorizontalDivider(color = Line)
                SettingsActionRow(Icons.Outlined.BugReport, "诊断中心", "检查平台连通性、登录状态和运行环境") {
                    controller.page = AppPage.STATUS
                }
                HorizontalDivider(color = Line)
                SettingsActionRow(Icons.Outlined.Article, "诊断日志", "打开本机日志文件夹，反馈故障时可一并发送") {
                    runCatching(controller::openDiagnosticLogs)
                        .onFailure { message = it.message ?: "无法打开日志目录" }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(AccentSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("解析 ${DesktopAppController.APP_VERSION}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            controller.updateMessage ?: if (controller.updateConfigured) "作者签名安全更新通道" else "安全更新通道未配置",
                            color = if (controller.updateConfigured) Success else Warning,
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = controller::checkForUpdates, enabled = !controller.updateChecking) {
                        Text(if (controller.updateChecking) "检查中…" else "检查更新")
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(14.dp))
                Text("GitHub 反馈仓库", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text("只填写你自己的仓库；不会把定制版问题提交给原项目", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        githubRepository,
                        { githubRepository = it },
                        Modifier.weight(1f),
                        placeholder = { Text("https://github.com/用户名/仓库名") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        message = runCatching { controller.saveGitHubRepository(githubRepository); "GitHub 地址已保存" }
                            .getOrElse { it.message ?: "地址无效" }
                    }) { Text("保存") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (!controller.openGitHubFeedback()) message = "请先填写并保存你的 GitHub 仓库地址"
                    }) { Text("提交反馈") }
                }
            }
        }
      }
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Text("打开", color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun DesktopUpdateDialog(controller: DesktopAppController, release: DesktopRelease) {
    AlertDialog(
        onDismissRequest = controller::dismissUpdate,
        icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = Accent, modifier = Modifier.size(30.dp)) },
        title = {
            Column {
                Text("解析 ${release.version} 可用", fontWeight = FontWeight.SemiBold)
                Text("当前版本 ${DesktopAppController.APP_VERSION}", color = Muted, fontSize = 12.sp)
            }
        },
        text = {
            Column {
                Text("本次更新", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Surface(
                    Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    color = Color(0xFFFFFAF3),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    LazyColumn(Modifier.padding(14.dp)) {
                        item { Text(release.notes.ifBlank { "暂无更新说明" }, color = Ink, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("安装包会在软件内下载，并校验作者签名清单中的 SHA-256。", color = Muted, fontSize = 11.sp)
                controller.updateMessage?.let {
                    Text(it, color = if (controller.downloadedUpdate != null) Success else Warning, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            val downloaded = controller.downloadedUpdate
            if (downloaded != null) {
                Button(onClick = { controller.openFile(downloaded) }) { Text("运行安装包") }
            } else {
                Button(onClick = { controller.downloadUpdate(release) }, enabled = !controller.updateDownloading) {
                    Text(if (controller.updateDownloading) "下载并校验中…" else "下载更新")
                }
            }
        },
        dismissButton = { TextButton(onClick = controller::dismissUpdate) { Text("暂不更新") } }
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Muted, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(13.dp))
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

private fun chooseDirectory(initial: File): File? {
    val chooser = JFileChooser(initial.takeIf { it.exists() })
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "选择下载文件夹"
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun readClipboardText(): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
}.getOrNull()?.trim()?.takeIf { it.isNotBlank() }

private fun taskStateName(state: TaskState): String = when (state) {
    TaskState.WAITING -> "排队中"
    TaskState.PREPARING -> "正在获取直链"
    TaskState.DOWNLOADING -> "下载中"
    TaskState.PAUSED -> "已暂停"
    TaskState.RETRY_WAIT -> "等待重试"
    TaskState.INTERRUPTED -> "可恢复"
    TaskState.NEEDS_REAUTH -> "需要重新登录"
    TaskState.NEEDS_INPUT -> "需要提取码"
    TaskState.VERIFYING -> "正在校验"
    TaskState.COMPLETED -> "已完成"
    TaskState.FAILED -> "失败"
    TaskState.CANCELLED -> "已取消"
}

private fun formatSpeed(value: Long): String = if (value <= 0) "" else "${formatBytes(value)}/s"

private fun formatBytes(value: Long): String {
    if (value < 0) return "未知"
    if (value < 1024) return "$value B"
    val unit = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
    val names = arrayOf("B", "KB", "MB", "GB", "TB")
    return "%.1f %s".format(value / 1024.0.pow(unit), names[unit])
}
