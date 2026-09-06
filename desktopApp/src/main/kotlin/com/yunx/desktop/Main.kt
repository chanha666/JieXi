package com.yunx.desktop

import com.yunx.desktop.i18n.tr

import com.yunx.desktop.i18n.tr

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
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
import com.yunx.desktop.settings.DesktopSupportLinks
import com.yunx.desktop.update.DesktopRelease
import com.yunx.desktop.system.SingleInstanceGuard
import com.yunx.desktop.media.DesktopMediaTask
import com.yunx.desktop.media.MediaTaskState
import com.yunx.desktop.media.DesktopMediaController
import com.yunx.desktop.media.WatermarkSelectionDialog
import com.yunx.app.data.network.ProxyMode
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import kotlin.math.ln
import kotlin.math.pow

private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Canvas: Color @Composable get() = MaterialTheme.colorScheme.background
private val Sidebar: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val WarmCard: Color @Composable get() = MaterialTheme.colorScheme.surface
private val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AccentStrong: Color @Composable get() = MaterialTheme.colorScheme.primary
private val AccentSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Orange: Color @Composable get() = MaterialTheme.colorScheme.primary
private val OrangeSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Success = Color(0xFF128665)
private val SuccessSoft = Color(0xFFE1F6EE)
private val Warning = Color(0xFFB76A13)
private val WarningSoft = Color(0xFFFFF1D9)

private const val SPONSOR_IMAGE_ALIPAY = "donate_alipay.jpg"
private const val SPONSOR_IMAGE_WECHAT = "donate_wechat.jpg"
private const val APP_FEEDBACK_REPO_PLACEHOLDER = "https://github.com/你的仓库/你的项目"
private const val APP_ICON_RESOURCE = "icon.png"
private const val APP_ICON_BRAND_RESOURCE = "icon_brand.png"
private val Purple = Color(0xFF6B62D9)
private val Error = Color(0xFFB3261E)

private val DesktopFontFamily: FontFamily by lazy {
    val fonts = File(System.getenv("WINDIR") ?: "C:/Windows", "Fonts")
    val regular = File(fonts, "msyh.ttc")
    val bold = File(fonts, "msyhbd.ttc")
    if(regular.isFile && bold.isFile) FontFamily(
        androidx.compose.ui.text.platform.Font(regular, FontWeight.Normal),
        androidx.compose.ui.text.platform.Font(bold, FontWeight.Bold)
    ) else FontFamily.SansSerif
}
private val DesktopTypography = Typography(
    headlineLarge = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = DesktopFontFamily, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = DesktopFontFamily, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = DesktopFontFamily, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = DesktopFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

fun main() {
    var installerAfterExit: File? = null
    val instance = SingleInstanceGuard.acquire()
    if (instance == null) {
        JOptionPane.showMessageDialog(null, "解析已经在运行，请查看任务栏右下角托盘。", "解析", JOptionPane.INFORMATION_MESSAGE)
        return
    }
    try {
        application(exitProcessOnExit = false) {
            XunleiDeviceFingerprint.init()
            val state = rememberWindowState(position = WindowPosition(Alignment.Center), size = DpSize(1180.dp, 780.dp))
            val icon = remember { loadDesktopIcon() }
            val controller = remember { DesktopAppController() }
            val trayState = rememberTrayState()
            var windowVisible by remember { mutableStateOf(true) }
            LaunchedEffect(controller) {
                controller.onTaskNotification = { title, message ->
                    trayState.sendNotification(Notification(title, message))
                }
                kotlinx.coroutines.delay(1500)
                if (controller.updateConfigured) controller.checkForUpdates()
            }
            Tray(
                state = trayState,
                icon = icon,
                tooltip = "解析",
                menu = {
                    Item("打开解析", onClick = { windowVisible = true })
                    Item("打开下载目录", onClick = { controller.openDirectory(controller.settings.downloadDirectory) })
                    Item("退出", onClick = ::exitApplication)
                }
            )
            Window(
                visible = windowVisible,
                onCloseRequest = {
                    if (controller.settings.closeToTray) {
                        windowVisible = false
                        trayState.sendNotification(Notification("解析仍在运行", "下载任务会继续，可从托盘重新打开。"))
                    } else exitApplication()
                },
                title = "解析",
                icon = icon,
                state = state
            ) {
                window.minimumSize = java.awt.Dimension(780, 560)
                val appearanceRevision = controller.appearanceRevision
                val scheme = desktopColorScheme(controller.settings.themeMode, controller.settings.accentHex, appearanceRevision)
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, controller.settings.fontScale)) {
                MaterialTheme(colorScheme = scheme, typography = DesktopTypography) {
                    Surface(Modifier.fillMaxSize(), color = Canvas) {
                        YunXDesktopApp(controller) { file ->
                            installerAfterExit = file
                            exitApplication()
                        }
                    }
                }
                }
            }
        }
    } finally {
        instance.close()
    }
    installerAfterExit?.let { file ->
        try {
            java.awt.Desktop.getDesktop().open(file)
        } catch (error: Exception) {
            JOptionPane.showMessageDialog(null, "无法启动安装包，请手动打开：${file.absolutePath}", "解析更新", JOptionPane.ERROR_MESSAGE)
        }
    }
    kotlin.system.exitProcess(0)
}

private fun loadDesktopIcon(): BitmapPainter {
    return loadResourcePainter(listOf(APP_ICON_BRAND_RESOURCE, APP_ICON_RESOURCE))
}

private fun loadResourcePainter(resourceName: String): BitmapPainter {
    val bytes = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName)) {
        "Missing resource: $resourceName"
    }.use { it.readBytes() }
    return BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
}

private fun loadResourcePainter(resourceNames: List<String>): BitmapPainter {
    for (resourceName in resourceNames) {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceName) ?: continue
        return stream.use {
            BitmapPainter(SkiaImage.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
        }
    }
    error("Missing resource: ${resourceNames.joinToString()}")
}

@Composable
private fun YunXDesktopApp(controller: DesktopAppController, onInstallUpdate: (File) -> Unit) {
    Box(Modifier.fillMaxSize().background(Canvas).padding(12.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SidebarNavigation(controller.page) { controller.page = it }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(WarmCard)) {
                if (controller.page !in AppPage.primary) {
                    TextButton(onClick = { controller.page = controller.page.primaryPage }, modifier = Modifier.padding(start = 20.dp, top = 10.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (controller.page.primaryPage == AppPage.RESOLVE) tr("返回解析") else tr("返回我的"))
                    }
                }
                AnimatedContent(
                    targetState = controller.page,
                    transitionSpec = {
                        val duration = if (controller.settings.reduceMotion) 0 else 160
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    modifier = Modifier.weight(1f),
                    label = "page"
                ) { page ->
                    when (page) {
                        AppPage.RESOLVE -> ResolvePage(controller)
                        AppPage.MEDIA -> MediaPage(controller)
                        AppPage.DOWNLOADS -> DownloadsPage(controller)
                        AppPage.MINE -> MinePage(controller)
                        AppPage.TOOLS -> MediaToolsPage(controller)
                        AppPage.LIBRARY -> LibraryWorkspace(controller)
                        AppPage.STATUS -> StatusPage(controller)
                        AppPage.ACCOUNTS -> AccountsPage(controller)
                        AppPage.CLOUD -> CloudWorkspace(controller)
                        AppPage.APPEARANCE -> DesktopPreferencesPage(controller, appearance = true)
                        AppPage.BACKUP -> CredentialBackupPage(controller)
                        AppPage.SPONSOR -> SponsorPage()
                        AppPage.SETTINGS -> SettingsPage(controller)
                    }
                }
            }
        }
        controller.updateRelease?.let { release -> DesktopUpdateDialog(controller, release, onInstallUpdate) }
    }
}

@Composable
private fun SidebarNavigation(selected: AppPage, onSelect: (AppPage) -> Unit) {
    val brandMark = remember { loadDesktopIcon() }
    Column(
        Modifier.width(184.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp))
            .background(Sidebar).verticalScroll(rememberScrollState()).padding(14.dp)
    ) {
        Row(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(brandMark, "解析图标", modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(com.yunx.desktop.i18n.brandName(), fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("链接到文件"), fontSize = 12.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(26.dp))
        NavigationItem("解析", Icons.Outlined.Link, selected.primaryPage == AppPage.RESOLVE) { onSelect(AppPage.RESOLVE) }
        NavigationItem("下载", Icons.Outlined.Download, selected.primaryPage == AppPage.DOWNLOADS) { onSelect(AppPage.DOWNLOADS) }
        NavigationItem("我的", Icons.Outlined.PersonOutline, selected.primaryPage == AppPage.MINE) { onSelect(AppPage.MINE) }
        Spacer(Modifier.height(32.dp))
        Text("4.1 · Windows", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(13.dp))
    }
}

@Composable
private fun MinePage(controller: DesktopAppController) {
    PageFrame(tr("我的"), tr("账号、工具与偏好，各归其位")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(tr("我的内容"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            MineEntry(tr("历史与收藏"), tr("重新解析、收藏管理与历史记录"), Icons.Outlined.Save) { controller.page = AppPage.LIBRARY }
            MineEntry(tr("网盘与账号"), tr("软件内登录、浏览器导入与凭据管理"), Icons.Outlined.Cloud) { controller.page = AppPage.ACCOUNTS }
            MineEntry(tr("我的网盘文件"), tr("空间、目录、下载、移动、重命名与分享"), Icons.Outlined.Folder) { controller.page = AppPage.CLOUD }
            Text(tr("工具与偏好"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
            MineEntry(tr("媒体工具"), tr("图片局部修复、音频提取、视频截图与媒体信息"), Icons.Outlined.Build) { controller.page = AppPage.TOOLS }
            MineEntry(tr("视频下载选项"), tr("批量视频、画质、字幕与音频"), Icons.Outlined.OndemandVideo) { controller.page = AppPage.MEDIA }
            MineEntry(tr("平台与诊断"), tr("账号状态、网络诊断与脱敏日志"), Icons.Outlined.Hub) { controller.page = AppPage.STATUS }
            MineEntry(tr("外观"), tr("浅色、深色、系统主题与字体大小"), Icons.Outlined.Palette) { controller.page = AppPage.APPEARANCE }
            MineEntry(tr("认证备份"), tr("加密导入导出，手机与电脑互通"), Icons.Outlined.Shield) { controller.page = AppPage.BACKUP }
            MineEntry(tr("设置与更新"), tr("下载路径、速度预设、减少动画、在线更新与反馈"), Icons.Outlined.Settings) { controller.page = AppPage.SETTINGS }
            Text(tr("关于"), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
            MineEntry(tr("赞赏作者"), tr("如果解析帮到了你，欢迎支持后续维护"), Icons.Outlined.FavoriteBorder) { controller.page = AppPage.SPONSOR }
        }
    }
}

@Composable
private fun MineEntry(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 17.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(description, color = Muted, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = Muted, modifier = Modifier.size(17.dp))
    }
    HorizontalDivider(color = Line.copy(alpha = 0.5f))
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
        Text(tr(label), fontSize = 14.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
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
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
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
        tr("解析"),
        tr("粘贴链接，获取你需要的文件"),
        action = {
            TextButton(
                onClick = { controller.page = AppPage.SPONSOR },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = AccentStrong)
            ) {
                Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(tr("支持开发"))
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = controller.linkText,
            onValueChange = { controller.linkText = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            placeholder = { Text(tr("粘贴网盘、公开视频链接或分享文字")) },
            enabled = !controller.isResolving,
            shape = RoundedCornerShape(16.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = controller.password, onValueChange = { controller.password = it },
                modifier = Modifier.weight(1f), placeholder = { Text(tr("网盘提取码（可选）")) },
                enabled = !controller.isResolving, singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            OutlinedButton(onClick = { readClipboardText()?.let { controller.linkText = it } }, enabled = !controller.isResolving, modifier = Modifier.height(50.dp)) {
                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(tr("粘贴"))
            }
            TextButton(onClick = { controller.linkText = ""; controller.password = "" }, enabled = !controller.isResolving) { Text(tr("清空")) }
        }
        Button(
            onClick = controller::resolve, enabled = controller.linkText.isNotBlank() && !controller.isResolving,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)
        ) {
            if (controller.isResolving) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (controller.isResolving) tr("正在解析…") else tr("开始解析"), fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("部分网盘需要登录账号后才能下载。"), color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { controller.page = AppPage.ACCOUNTS }) { Text(tr("管理账号")) }
        }
        controller.resolveError?.let {
            Surface(color = Color(0xFFFFECEA), shape = RoundedCornerShape(12.dp)) {
                Text(it, color = Error, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
            }
        }
        HorizontalDivider(color = Line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("最近下载"), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = { controller.page = AppPage.DOWNLOADS }) { Text(tr("查看全部")) }
        }
        val recent = (controller.downloads.take(3).map { it.fileName to taskStateName(it.state) } +
            controller.media.tasks.take(3).map { it.title to mediaTaskStateName(it.state) }).take(3)
        if (recent.isEmpty()) Text(tr("还没有任务。从上面粘贴第一个链接开始。"), color = Muted, fontSize = 14.sp)
        recent.forEach { (title, state) ->
            Row(Modifier.fillMaxWidth().clickable { controller.page = AppPage.DOWNLOADS }.padding(vertical = 10.dp)) {
                Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp)); Text(state, color = Muted, fontSize = 13.sp)
            }
        }
        Text(tr("保存到 ") + controller.downloadDirectory, color = Muted, fontSize = 12.sp)
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
        Text(tr(label), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                Text(detail, fontSize = 12.sp, color = Muted, maxLines = 1)
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
    var selected by remember(controller.path) { mutableStateOf(emptySet<String>()) }
    var destination by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picked = controller.currentFiles.filter { it.fid in selected }
    if(destination) CloudFolderDialog(result.platform, controller.cloud, { destination = false }) { directory ->
        transferring = true
        scope.launch {
            try {
                val credential = controller.cloud.credential(result.platform)
                picked.forEach { result.repository.transferFile(result.session,it,directory,credential).getOrThrow() }
                message = "已转存 ${picked.size} 项"
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { message = e.message ?: "转存失败" }
            finally { transferring = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { controller.goToPath(-1) }) { Text(result.session.title.ifBlank { tr("分享根目录") }) }
            controller.path.forEachIndexed { index, item ->
                Text("/", color = Muted)
                TextButton(onClick = { controller.goToPath(index) }) {
                    Text(item.first, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(DesktopResolver.platformName(result.platform), color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = controller::clearResolution) { Text(tr("解析新链接")) }
        }
        controller.resolveError?.let { Text(it, color = Error, fontSize = 13.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected.isNotEmpty() && selected.size == controller.currentFiles.size,
                { selected = if(it) controller.currentFiles.map { f -> f.fid }.toSet() else emptySet() })
            Text(tr("已选 {0}", selected.size),modifier = Modifier.weight(1f))
            TextButton(onClick = { controller.downloadShareSelection(picked) }, enabled = picked.isNotEmpty() && !transferring && !controller.isResolving) { Text(tr("批量下载")) }
            TextButton(onClick = { destination = true },enabled = picked.isNotEmpty() && !transferring) { Text(tr("转存到网盘")) }
            TextButton(onClick = { controller.addFavoriteCurrent(); message = "已添加收藏" }) { Text(tr("收藏链接")) }
        }
        if(transferring) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it,color = Accent) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(file.fid in selected, { selected = if(it) selected + file.fid else selected - file.fid }, enabled = !transferring)
                            Box(Modifier.weight(1f)) { FileRow(file, controller::openFolder, controller::download) }
                        }
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
            if (!file.isdir) Text(formatBytes(file.fsize), color = Muted, fontSize = 12.sp)
        }
        if (file.isdir) {
            Text(tr("打开"), color = Accent, fontSize = 12.sp)
        } else {
            OutlinedButton(onClick = { onDownload(file) }, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("下载"))
            }
        }
    }
}

@Composable
private fun MediaPage(controller: DesktopAppController) {
    val media = controller.media
    var formatMenu by remember { mutableStateOf(false) }
    PageFrame(
        tr("公开视频下载"),
        tr("选择画质或批量添加；优先使用原站公开提供的资源"),
        action = {
            OutlinedButton(onClick = { controller.page = AppPage.TOOLS }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.Build, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(tr("媒体工具"))
            }
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6C7AD)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    Modifier.background(WarmCard)
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(45.dp).clip(RoundedCornerShape(15.dp)).background(AccentSoft),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.OndemandVideo, null, tint = Accent, modifier = Modifier.size(23.dp)) }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("视频与音频"), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(tr("YouTube、哔哩哔哩、抖音、X、TikTok、小红书、微博、视频号与通用网站"), color = Muted, fontSize = 12.sp)
                        }
                        Surface(color = SuccessSoft, shape = RoundedCornerShape(18.dp)) {
                            Text(tr("匿名模式"), color = Success, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = media.inputText,
                        onValueChange = media::acceptInput,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 98.dp),
                        placeholder = { Text(tr("粘贴视频链接或整段分享文字；支持多链接批量加入队列")) },
                        enabled = !media.analyzing && !media.queueing,
                        shape = RoundedCornerShape(15.dp)
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { readClipboardText()?.let(media::acceptInput) },
                            enabled = !media.analyzing && !media.queueing,
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("粘贴"))
                        }
                        Spacer(Modifier.width(9.dp))
                        Button(
                            onClick = media::analyze,
                            enabled = media.inputText.isNotBlank() && !media.analyzing && !media.queueing,
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) {
                            if (media.analyzing) {
                                CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(7.dp))
                            }
                            Text(if (media.analyzing) tr("正在解析") else tr("解析第一个链接"))
                        }
                        Spacer(Modifier.weight(1f))
                        Text(tr("公开视频原站提供 4K / 8K 时可直接选择"), color = Muted, fontSize = 12.sp)
                    }
                }
            }

            media.analysisError?.let { message ->
                Spacer(Modifier.height(10.dp))
                Surface(color = Color(0xFFFFECEA), shape = RoundedCornerShape(11.dp)) {
                    Text(message, color = Error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(11.dp))
                }
            }

            media.preview?.let { preview ->
                Spacer(Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarmCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(OrangeSoft),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.PlayArrow, null, tint = Orange, modifier = Modifier.size(32.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(preview.platform, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(preview.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOf(preview.uploader, formatDurationDesktop(preview.durationSeconds), preview.engine).filter(String::isNotBlank).joinToString(" · "),
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                if (preview.downloadUrl.isNotBlank()) Text(tr("已提取可直接下载的公开源"), color = Success, fontSize = 12.sp)
                            }
                        }
                        preview.warning?.let {
                            Spacer(Modifier.height(9.dp))
                            Text(it, color = Warning, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                OutlinedButton(onClick = { formatMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                                    Text(media.selectedFormat?.label ?: tr("自动选择最高画质"), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("⌄")
                                }
                                DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                                    preview.formats.forEach { choice ->
                                        DropdownMenuItem(
                                            text = { Text(tr(choice.label), fontSize = 12.sp) },
                                            onClick = { media.selectedFormat = choice; formatMenu = false }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Checkbox(media.embedSubtitles, { media.embedSubtitles = it })
                            Text(tr("尝试嵌入中英文字幕"), fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = media::enqueueAll,
                    enabled = media.inputText.isNotBlank() && !media.queueing && !media.analyzing,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    if (media.queueing) {
                        CircularProgressIndicator(Modifier.size(17.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (media.queueing) tr("正在批量识别") else tr("全部加入队列并下载"))
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { controller.page = AppPage.DOWNLOADS }, modifier = Modifier.height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(tr("查看下载任务 ({0})", media.tasks.size))
                }
                Spacer(Modifier.weight(1f))
                Text(tr("默认单任务满速 · 失败自动保留断点"), color = Muted, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("YouTube", "哔哩哔哩", "抖音", "X", "TikTok", "小红书", "微博", "视频号").forEach { name ->
                    Surface(color = WarmCard, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(18.dp)) {
                        Text(name, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MediaToolsPage(controller: DesktopAppController) {
    val media = controller.media
    var screenshotSecond by remember { mutableStateOf("1.0") }
    PageFrame(tr("媒体工具"), tr("所有处理都在本机完成：图片去水印、音频提取、视频截图与媒体信息")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(17.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(SuccessSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Verified, null, tint = Success, modifier = Modifier.size(21.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("本地媒体核心"), fontWeight = FontWeight.SemiBold)
                            val status = media.coreStatus
                            Text(
                                if (status == null) tr("正在检查组件…") else "yt-dlp ${status.ytDlpVersion} · FFmpeg ${status.ffmpegVersion} · Deno ${status.denoVersion}",
                                color = if (status?.ready == false) Error else Muted,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = media::refreshCoreStatus, enabled = !media.coreChecking) { Text(tr("重新检查")) }
                        TextButton(onClick = media::updateCore, enabled = !media.coreChecking) { Text(tr("更新核心")) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MediaToolCard(
                    Modifier.weight(1f), Icons.Outlined.Image, tr("图片去水印"), tr("打开图片后拖动框选水印区域，智能修复并另存新文件")
                ) {
                    chooseLocalFile("选择 PNG 或 JPEG 图片", listOf("png", "jpg", "jpeg"))?.let { source ->
                        media.toolMessage = "已选择 ${source.name}，正在打开框选工具…"
                        openWatermarkTool(source, media)
                    }
                }
                MediaToolCard(
                    Modifier.weight(1f), Icons.Outlined.AudioFile, tr("提取 MP3"), tr("从本地视频导出最高质量 MP3 音频")
                ) {
                    chooseLocalFile("选择视频", listOf("mp4", "mkv", "mov", "webm", "avi", "flv"))?.let(media::extractAudio)
                }
                MediaToolCard(
                    Modifier.weight(1f), Icons.Outlined.Info, tr("导出媒体信息"), tr("生成包含编码、分辨率、码率和时长的 JSON")
                ) {
                    chooseLocalFile("选择媒体文件", listOf("mp4", "mkv", "mov", "webm", "mp3", "m4a", "flac"))?.let(media::exportMetadata)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = WarmCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.OndemandVideo, null, tint = Accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("视频截图"), fontWeight = FontWeight.SemiBold)
                        Text(tr("输入时间点后，从本地视频导出高清 JPG"), color = Muted, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = screenshotSecond,
                        onValueChange = { screenshotSecond = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8) },
                        modifier = Modifier.width(130.dp),
                        label = { Text(tr("秒")) },
                        singleLine = true
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = {
                        chooseLocalFile("选择视频", listOf("mp4", "mkv", "mov", "webm", "avi", "flv"))
                            ?.let { media.captureFrame(it, screenshotSecond.toDoubleOrNull() ?: 1.0) }
                    }, enabled = !media.toolBusy) { Text(tr("选择视频并截图")) }
                }
            }
            media.toolMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Surface(color = if ("失败" in message || "错误" in message) Color(0xFFFFECEA) else AccentSoft, shape = RoundedCornerShape(11.dp)) {
                    Text(message, color = if ("失败" in message || "错误" in message) Error else AccentStrong, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { controller.openDirectory(File(controller.downloadDirectory, "媒体工具")) }) {
                Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(tr("打开媒体工具成品目录"))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MediaToolCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().height(148.dp).padding(17.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(13.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Text(tr("开始使用 →"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadsPage(controller: DesktopAppController) {
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("全部") }
    var addDirect by remember { mutableStateOf(false) }
    var directUrl by remember { mutableStateOf("") }
    var directName by remember { mutableStateOf("") }
    var directError by remember { mutableStateOf<String?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.yunx.desktop.core.DesktopDownloadTask?>(null) }
    if(addDirect) AlertDialog(onDismissRequest = { addDirect = false },title = { Text(tr("添加文件直链")) },
        text = { Column {
            OutlinedTextField(directUrl,{ directUrl = it },label = { Text(tr("HTTP/HTTPS 文件直链")) })
            OutlinedTextField(directName,{ directName = it },label = { Text(tr("保存文件名（可选）")) })
            directError?.let { Text(it,color = Error) }
        } },
        confirmButton = { Button(onClick = {
            runCatching { controller.addDirectDownload(directUrl,directName) }.onSuccess { addDirect = false }.onFailure { directError = it.message }
        }) { Text(tr("开始下载")) } },
        dismissButton = { TextButton(onClick = { addDirect = false }) { Text(tr("取消")) } })
    if(clearAll || pendingDelete != null) AlertDialog(onDismissRequest = { clearAll = false; pendingDelete = null },
        title = { Text(if(clearAll) tr("删除全部任务？") else tr("删除下载任务？")) },
        text = { Text(tr("会取消相关任务并清理未完成临时文件。已完成的文件会保留。")) },
        confirmButton = { Button(onClick = {
            if(clearAll) {
                controller.downloads.toList().forEach(controller::removeDownload)
                controller.media.tasks.toList().forEach(controller.media::remove)
            } else pendingDelete?.let(controller::removeDownload)
            clearAll = false; pendingDelete = null
        }) { Text(tr("确认删除")) } },
        dismissButton = { TextButton(onClick = { clearAll = false; pendingDelete = null }) { Text(tr("取消")) } })
    PageFrame(tr("下载"), tr("文件与视频任务统一管理")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query,{ query = it },Modifier.weight(1f),placeholder = { Text(tr("搜索任务名称")) },singleLine = true)
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { directUrl = ""; directName = ""; directError = null; addDirect = true }) { Text(tr("添加直链")) }
            TextButton(onClick = { clearAll = true }) { Text(tr("删除全部")) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部","网盘与文件","视频与音频","已完成","未完成").forEach { label ->
                androidx.compose.material3.FilterChip(kind == label,{ kind = label },label = { Text(tr(label)) })
            }
        }
        val cloudTasks = controller.downloads.filter { task -> task.fileName.contains(query,true) && kind != "视频与音频" &&
            (kind != "已完成" || task.state == TaskState.COMPLETED) && (kind != "未完成" || task.state != TaskState.COMPLETED) }
        val mediaTasks = controller.media.tasks.filter { task -> task.title.contains(query,true) && kind != "网盘与文件" &&
            (kind != "已完成" || task.state == MediaTaskState.COMPLETED) && (kind != "未完成" || task.state != MediaTaskState.COMPLETED) }
        if (cloudTasks.isEmpty() && mediaTasks.isEmpty()) {
            EmptyState(Icons.Outlined.Download, tr("还没有下载任务"), tr("解析网盘分享或公开视频链接后，任务会显示在这里"))
        } else {
            val active = controller.downloads.count { it.state == TaskState.PREPARING || it.state == TaskState.DOWNLOADING } +
                mediaTasks.count { it.state in setOf(MediaTaskState.ANALYZING, MediaTaskState.DOWNLOADING) }
            val completed = controller.downloads.count { it.state == TaskState.COMPLETED } +
                mediaTasks.count { it.state == MediaTaskState.COMPLETED }
            val total = controller.downloads.size + mediaTasks.size
            Text(tr("共 {0} 项 · 进行中 {1} · 已完成 {2}", total, active, completed), color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = controller::pauseAll) { Text(tr("全部暂停")) }
                Button(onClick = controller::resumeAll) { Text(tr("全部继续")) }
            }
            Spacer(Modifier.height(14.dp))
            val state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                    if (cloudTasks.isNotEmpty()) {
                        item("cloud-heading") {
                            Text(tr("网盘文件"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(cloudTasks, key = { it.id }) { task ->
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
                                    Text(tr(taskStateName(task.state)), color = if (task.state in setOf(TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT)) Error else Accent, fontSize = 12.sp)
                                    TextButton(onClick = { controller.setPriority(task, task.priority <= 0) }) { Text(if (task.priority > 0) tr("普通") else tr("置顶")) }
                                    when (task.state) {
                                        TaskState.DOWNLOADING, TaskState.PREPARING, TaskState.WAITING, TaskState.RETRY_WAIT ->
                                            TextButton(onClick = { controller.pauseDownload(task) }) { Text(tr("暂停")) }
                                        TaskState.PAUSED, TaskState.INTERRUPTED, TaskState.FAILED, TaskState.NEEDS_REAUTH, TaskState.NEEDS_INPUT ->
                                            TextButton(onClick = { controller.resumeDownload(task) }) { Text(if (task.state == TaskState.FAILED) tr("重试") else tr("继续")) }
                                        else -> Unit
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { pendingDelete = task }) {
                                        Icon(Icons.Outlined.DeleteOutline, tr("删除下载任务"), tint = Muted, modifier = Modifier.size(18.dp))
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
                                    Text(detail, color = if (task.state == TaskState.FAILED) Error else Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    if (task.retryCount > 0 || task.errorCode.isNotBlank()) {
                                        Text(tr("{0} · 已重试 {1} 次", task.errorCode.ifBlank { tr("自动重试") }, task.retryCount), color = Warning, fontSize = 12.sp)
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    task.outputFile?.let { file ->
                                        TextButton(onClick = { controller.openFolder(file) }) {
                                            Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(5.dp))
                                            Text(tr("打开目录"))
                                        }
                                    }
                                }
                                CopyDownloadLinkButton { controller.copyTaskLink(task) }
                                if(task.state == TaskState.COMPLETED) CompletedFileActions(controller,task.outputFile,
                                    onAgain = { controller.downloadAgain(task) }, onRemoved = { controller.removeDownload(task) })
                            }
                        }
                    }
                    if (mediaTasks.isNotEmpty()) {
                        item("media-heading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("公开视频与媒体"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = controller.media::clearFinished) { Text(tr("清理已完成")) }
                            }
                        }
                        items(mediaTasks, key = { "media-${it.id}" }) { task ->
                            MediaTaskCard(controller, task)
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MediaTaskCard(controller: DesktopAppController, task: DesktopMediaTask) {
    var confirmDelete by remember { mutableStateOf(false) }
    if(confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr("删除任务？")) },
        text = { Text(tr("取消任务并清理未完成的临时文件，已完成文件仍会保留。")) },
        confirmButton = { Button(onClick = { controller.media.remove(task); confirmDelete = false }) { Text(tr("删除任务")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消")) } })
    val stateColor = when (task.state) {
        MediaTaskState.COMPLETED -> Success
        MediaTaskState.FAILED -> Error
        MediaTaskState.PAUSED, MediaTaskState.INTERRUPTED -> Warning
        MediaTaskState.CANCELLED -> Muted
        else -> Accent
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(OrangeSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.OndemandVideo, null, tint = Orange, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${task.platform} · ${task.formatLabel}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(tr(mediaTaskStateName(task.state)), color = stateColor, fontSize = 12.sp)
                when (task.state) {
                    MediaTaskState.WAITING, MediaTaskState.ANALYZING, MediaTaskState.DOWNLOADING ->
                        IconButton(onClick = { controller.media.pause(task) }) { Icon(Icons.Outlined.Pause, tr("暂停"), tint = Muted) }
                    MediaTaskState.PAUSED, MediaTaskState.INTERRUPTED, MediaTaskState.FAILED ->
                        TextButton(onClick = { controller.media.resume(task) }) { Text(if (task.state == MediaTaskState.FAILED) tr("重试") else tr("继续")) }
                    else -> Unit
                }
                if (task.state !in setOf(MediaTaskState.COMPLETED, MediaTaskState.CANCELLED)) {
                    IconButton(onClick = { controller.media.cancel(task) }) { Icon(Icons.Outlined.Cancel, tr("取消"), tint = Muted) }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, tr("删除任务"), tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(11.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                color = Orange,
                trackColor = OrangeSoft
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val detail = task.error?.takeIf(String::isNotBlank)
                    ?: listOf(task.stage, task.speed, task.eta.takeIf(String::isNotBlank)?.let { "剩余 $it" }.orEmpty())
                        .filter(String::isNotBlank).joinToString(" · ")
                Text(detail, color = if (task.error != null) Error else Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                task.outputFile?.takeIf(File::isFile)?.let { file ->
                    TextButton(onClick = { controller.openFolder(file) }) {
                        Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(tr("打开目录"))
                    }
                }
            }
            CopyDownloadLinkButton { controller.media.engine.analyze(task.sourceUrl).downloadUrl to {} }
            if(task.state == MediaTaskState.COMPLETED) CompletedFileActions(controller,task.outputFile,
                onAgain = { controller.media.downloadAgain(task) },onRemoved = { controller.media.remove(task) })
        }
    }
}

@Composable
private fun LibraryPage(controller: DesktopAppController) {
    PageFrame(tr("历史收藏"), tr("最近解析记录与长期收藏；提取码不会写入记录")) {
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
                onClear?.let { TextButton(onClick = it) { Text(tr("清空")) } }
            }
            if (entries.isEmpty()) EmptyState(Icons.Outlined.Save, tr("这里还是空的"), tr("解析链接或收藏后会显示在这里"))
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.third }) { item ->
                    Surface(color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onOpen(item.third) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.first.ifBlank { tr("未命名分享") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.second, color = Muted, fontSize = 12.sp)
                            }
                            onRemove?.let { IconButton(onClick = { it(item.third) }) { Icon(Icons.Outlined.DeleteOutline, tr("删除收藏")) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPage(controller: DesktopAppController) {
    PageFrame(tr("平台状态与诊断"), tr("区分网络、账号和适配状态；“实验”不等于已通过真实账号验收")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("六个平台"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = controller::runDiagnostics, enabled = !controller.diagnosticRunning) {
                Text(if (controller.diagnosticRunning) tr("检测中…") else tr("运行诊断"))
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
                        Text(tr("适配：{0}", item.support), color = Warning, fontSize = 12.sp, modifier = Modifier.width(92.dp))
                        Text(tr("账号：{0}", item.account), color = Muted, fontSize = 12.sp, modifier = Modifier.width(104.dp))
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
                Text(tr(label), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AccountsPage(controller: DesktopAppController) {
    PageFrame(tr("云盘登录"), tr("在这里完成授权管理；登录信息由 Windows 在本机加密保存")) {
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
                                Text(tr("公开链接不登录也能先解析"), fontWeight = FontWeight.SemiBold)
                                Text(tr("遇到网盘限制时只需点“登录”。官方页面由软件内置组件打开，完成后点右上角导入即可。"), color = Muted, fontSize = 12.sp)
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
                Text(if (saved) tr("已配置") else tr("未配置"), color = if (saved) Accent else Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (key == CredentialKey.PAN123_TOKEN) TokenLoginCard(controller.credentialStore, false) {
                value = controller.credentialStore.get(key).orEmpty(); saved = value.isNotBlank()
            }
            if (supportsDirectLogin) {
                Surface(color = AccentSoft, shape = RoundedCornerShape(11.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("软件内登录"), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(tr("打开内置官方登录页，完成后自动保存授权"), color = Muted, fontSize = 12.sp)
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
                            Text(if (saved) tr("重新登录") else tr("登录"), fontSize = 12.sp)
                        }
                        if (saved) {
                            IconButton(onClick = {
                                if (embeddedBusy) return@IconButton
                                embeddedBusy = true
                                coroutineScope.launch {
                                    val cleared = withContext(Dispatchers.IO) {
                                        embeddedImporter.clearLoginData(key) and
                                            controller.cookieImporter.clearLoginData()
                                    }
                                    browserError = !cleared
                                    browserMessage = if (cleared) {
                                        controller.credentialStore.remove(key)
                                        value = ""
                                        saved = false
                                        "已退出登录，并清除专用登录会话"
                                    } else {
                                        // Keep the retry button visible and the encrypted
                                        // credential consistent with the still-live browser
                                        // session. Claiming logout succeeded here would leave
                                        // usable cookies hidden in a locked profile.
                                        "退出尚未完成：请关闭登录窗口，然后再次点击退出"
                                    }
                                    embeddedBusy = false
                                }
                            }) { Icon(Icons.Outlined.DeleteOutline, tr("退出登录"), tint = Muted) }
                        }
                    }
                }
                browserMessage?.let {
                    Text(
                        it,
                        color = if (browserError) Error else Success,
                        fontSize = 12.sp,
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
                    Text(tr("保存"))
                }
                IconButton(onClick = {
                    controller.credentialStore.remove(key)
                    value = ""
                    saved = false
                }) { Icon(Icons.Outlined.DeleteOutline, tr("删除凭证")) }
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
            TokenLoginCard(controller.credentialStore, true) {
                access = controller.credentialStore.get(CredentialKey.XUNLEI_ACCESS_TOKEN).orEmpty()
                refresh = controller.credentialStore.get(CredentialKey.XUNLEI_REFRESH_TOKEN).orEmpty()
                saved = access.isNotBlank()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VpnKey, null, tint = if (saved) Accent else Muted)
                Spacer(Modifier.width(10.dp))
                Text(tr("迅雷网盘"), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(if (saved) tr("已配置") else tr("未配置"), color = if (saved) Accent else Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(access, { access = it }, Modifier.weight(1f), placeholder = { Text("Access Token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(refresh, { refresh = it }, Modifier.weight(1f), placeholder = { Text(tr("Refresh Token（建议填写）")) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(onClick = {
                    controller.credentialStore.put(CredentialKey.XUNLEI_ACCESS_TOKEN, access.trim())
                    controller.credentialStore.put(CredentialKey.XUNLEI_REFRESH_TOKEN, refresh.trim())
                    saved = access.isNotBlank()
                }) { Text(tr("保存")) }
            }
        }
    }
}

@Composable
private fun SponsorPage() {
    PageFrame(
        title = tr("赞赏支持"),
        subtitle = tr("如果本工具对你有价值，你可以在这里直接支持开发与适配更新")
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
                        Text(tr("自愿支持，功能不受影响"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(tr("赞赏只用于持续维护、修复平台适配与问题响应。"), color = Muted, fontSize = 12.sp)
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
                    title = "赞赏码（支付宝）",
                    subtitle = "打开支付宝扫一扫",
                    resourceName = SPONSOR_IMAGE_ALIPAY,
                    accent = Color(0xFF1677FF)
                )
                SponsorCodeCard(
                    modifier = Modifier.weight(1f),
                    title = "赞赏码（微信）",
                    subtitle = "打开微信扫一扫",
                    resourceName = SPONSOR_IMAGE_WECHAT,
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
                    Text(subtitle, color = Muted, fontSize = 12.sp)
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
                    contentDescription = "$title 赞赏二维码",
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPage(controller: DesktopAppController) {
    var directory by remember { mutableStateOf(controller.downloadDirectory) }
    var threads by remember { mutableStateOf(controller.threadCount) }
    var message by remember { mutableStateOf<String?>(null) }
    var githubRepository by remember { mutableStateOf(controller.settings.githubRepositoryUrl) }
    var closeToTray by remember { mutableStateOf(controller.settings.closeToTray) }
    var notifications by remember { mutableStateOf(controller.settings.notifyOnCompletion) }
    var preventSleep by remember { mutableStateOf(controller.settings.preventSleepWhileDownloading) }
    var proxyMode by remember { mutableStateOf(controller.settings.proxyMode) }
    var proxyHost by remember { mutableStateOf(controller.settings.proxyHost) }
    var proxyPort by remember { mutableStateOf(controller.settings.proxyPort.toString()) }
    PageFrame(tr("设置"), tr("显示、支持、诊断与桌面下载")) {
      Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        DesktopDownloadPreferences(controller)
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = WarmCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                Text(tr("性能预设"), fontWeight = FontWeight.Medium)
                Text(tr("稳定适合网络波动，均衡适合日常，极速会占用更多带宽和连接"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DesktopPreset.STABLE, DesktopPreset.BALANCED, DesktopPreset.TURBO, DesktopPreset.SINGLE_TASK_MAX).forEach { preset ->
                        if (controller.settings.preset == preset) Button(onClick = { controller.applyPreset(preset); threads = controller.threadCount }) { Text(tr(preset.label)) }
                        else OutlinedButton(onClick = { controller.applyPreset(preset); threads = controller.threadCount }) { Text(tr(preset.label)) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(tr("下载目录"), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(directory, { directory = it }, Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = {
                        chooseDirectory(File(directory))?.let { directory = it.absolutePath }
                    }) { Text(tr("选择文件夹")) }
                }
                Spacer(Modifier.height(20.dp))
                Text(tr("单任务连接数：{0} 路", threads), fontWeight = FontWeight.Medium)
                Text(tr("软件会按文件大小自动使用合适的并发数，小文件不会被强行切碎"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 16, 32, 48, 64).forEach { count ->
                        if (threads == count) Button(onClick = { threads = count }) { Text(count.toString()) }
                        else OutlinedButton(onClick = { threads = count }) { Text(count.toString()) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("动画效果"), fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (controller.settings.reduceMotion) Button(onClick = { controller.setReduceMotion(false) }) { Text(tr("已减少动画")) }
                    else OutlinedButton(onClick = { controller.setReduceMotion(true) }) { Text(tr("减少动画")) }
                }
                Spacer(Modifier.height(18.dp))
                Text(tr("Windows 桌面集成"), fontWeight = FontWeight.Medium)
                FlowRow {
                    Checkbox(closeToTray, { closeToTray = it }); Text(tr("关闭窗口后留在托盘"))
                    Spacer(Modifier.width(18.dp))
                    Checkbox(notifications, { notifications = it }); Text(tr("完成/失败通知"))
                    Spacer(Modifier.width(18.dp))
                    Checkbox(preventSleep, { preventSleep = it }); Text(tr("下载时阻止休眠"))
                }
                Spacer(Modifier.height(18.dp))
                Text(tr("网络代理"), fontWeight = FontWeight.Medium)
                Text(tr("系统代理适合大多数用户；也可指定 HTTP 或 SOCKS5"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ProxyMode.entries.forEach { mode ->
                        val label = when (mode) { ProxyMode.SYSTEM -> "跟随系统"; ProxyMode.DIRECT -> "不使用"; ProxyMode.HTTP -> "HTTP"; ProxyMode.SOCKS -> "SOCKS5" }
                        if (proxyMode == mode) Button(onClick = { proxyMode = mode }) { Text(tr(label)) }
                        else OutlinedButton(onClick = { proxyMode = mode }) { Text(tr(label)) }
                    }
                }
                if (proxyMode == ProxyMode.HTTP || proxyMode == ProxyMode.SOCKS) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(proxyHost, { proxyHost = it }, Modifier.weight(1f), label = { Text(tr("代理服务器")) }, singleLine = true)
                        OutlinedTextField(proxyPort, { proxyPort = it.filter(Char::isDigit).take(5) }, Modifier.width(150.dp), label = { Text(tr("端口")) }, singleLine = true)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(onClick = {
                    message = runCatching {
                        controller.saveSettings(directory, threads)
                        controller.saveDesktopIntegration(closeToTray, notifications, preventSleep)
                        controller.saveProxy(proxyMode, proxyHost, proxyPort.toIntOrNull() ?: 7890)
                        "设置已保存"
                    }
                        .getOrElse { it.message ?: "保存失败" }
                }) { Text(tr("保存设置")) }
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
                Text(tr("常规与支持"), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                SettingsActionRow(Icons.Outlined.Palette, "显示主题", "护眼暖色 · 清晰字体 · 可减少动画") {
                    controller.page = AppPage.APPEARANCE
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
                HorizontalDivider(color = Line)
                SettingsActionRow(Icons.Outlined.Save, "导出脱敏诊断包", "生成不包含 Cookie、Token 和完整链接的 ZIP") {
                    message = runCatching {
                        val file = controller.exportDiagnosticBundle()
                        controller.openDirectory(file.parentFile)
                        "诊断包已导出：${file.name}"
                    }.getOrElse { it.message ?: "诊断包导出失败" }
                }
                HorizontalDivider(color = Line)
                SettingsActionRow(Icons.AutoMirrored.Outlined.OpenInNew, "GitHub 问题反馈", "chanha666/JieXi · 提交问题与建议") {
                    if (!controller.openGitHubFeedback()) message = "请先填写并保存你的反馈仓库地址"
                }
                HorizontalDivider(color = Line)
                SettingsActionRow(Icons.Outlined.Email, "QQ 邮箱反馈", "${DesktopSupportLinks.FEEDBACK_EMAIL} · 打开默认邮件应用") {
                    message = runCatching {
                        if (controller.openSupportEmail()) "已打开默认邮件应用" else "系统没有可用的邮件应用"
                    }.getOrElse { it.message ?: "无法打开默认邮件应用" }
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
                        Text(tr("解析 {0}", DesktopAppController.APP_VERSION), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            controller.updateMessage ?: if (controller.updateConfigured) tr("作者签名安全更新通道") else tr("安全更新通道未配置"),
                            color = if (controller.updateConfigured) Success else Warning,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = controller::checkForUpdates, enabled = !controller.updateChecking) {
                        Text(if (controller.updateChecking) tr("检查中…") else tr("检查更新"))
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(14.dp))
                Text(tr("反馈仓库（我的项目）"), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(tr("建议填你的维护仓库，问题会直接进入对应项目 Issues"), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        githubRepository,
                        { githubRepository = it },
                        Modifier.weight(1f),
                        placeholder = { Text(APP_FEEDBACK_REPO_PLACEHOLDER) },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        message = runCatching { controller.saveGitHubRepository(githubRepository); "GitHub 地址已保存" }
                            .getOrElse { it.message ?: "地址无效" }
                    }) { Text(tr("保存")) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (!controller.openGitHubFeedback()) message = "请先填写并保存你的反馈仓库地址"
                    }) { Text(tr("打开反馈")) }
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
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Text(tr("打开"), color = Accent, fontSize = 12.sp)
    }
}

@Composable
private fun DesktopUpdateDialog(controller: DesktopAppController, release: DesktopRelease, onInstallUpdate: (File) -> Unit) {
    AlertDialog(
        onDismissRequest = controller::dismissUpdate,
        icon = { Icon(Icons.Outlined.SystemUpdate, null, tint = Accent, modifier = Modifier.size(30.dp)) },
        title = {
            Column {
                Text(tr("解析 {0} 可用", release.version), fontWeight = FontWeight.SemiBold)
                Text(tr("当前版本 {0}", DesktopAppController.APP_VERSION), color = Muted, fontSize = 12.sp)
            }
        },
        text = {
            Column {
                Text(tr("本次更新"), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Surface(
                    Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    color = Color(0xFFFCFAF7),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    LazyColumn(Modifier.padding(14.dp)) {
                        item { Text(release.notes.ifBlank { tr("暂无更新说明") }, color = Ink, fontSize = 13.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("安装包会在软件内下载，并校验作者签名清单中的 SHA-256。"), color = Muted, fontSize = 12.sp)
                controller.updateMessage?.let {
                    Text(it, color = if (controller.downloadedUpdate != null) Success else Warning, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            val downloaded = controller.downloadedUpdate
            if (downloaded != null) {
                Button(onClick = { controller.installUpdate(onInstallUpdate) }, enabled = !controller.updateDownloading) { Text(tr("退出并安装")) }
            } else {
                Button(onClick = { controller.downloadUpdate(release) }, enabled = !controller.updateDownloading) {
                    Text(if (controller.updateDownloading) tr("下载并校验中…") else tr("下载更新"))
                }
            }
        },
        dismissButton = { TextButton(onClick = controller::dismissUpdate) { Text(tr("暂不更新")) } }
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

private fun chooseLocalFile(title: String, extensions: List<String>): File? {
    val normalized = extensions.map { it.trim().removePrefix(".").lowercase() }.filter(String::isNotBlank)
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        isMultiSelectionEnabled = false
        isAcceptAllFileFilterUsed = false
        if (normalized.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(
                normalized.joinToString("、") { it.uppercase() } + " 文件",
                *normalized.toTypedArray()
            )
        }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.takeIf(File::isFile)
    } else {
        null
    }
}

private fun openWatermarkTool(source: File, media: DesktopMediaController) {
    runCatching { WatermarkSelectionDialog.show(null, source) }
        .onSuccess { output ->
            media.toolMessage = if (output == null) {
                "已取消图片去水印"
            } else {
                "图片去水印完成：${output.absolutePath}"
            }
        }
        .onFailure { error ->
            media.toolMessage = "图片去水印失败：${error.message ?: "未知错误"}"
        }
}

private fun formatDurationDesktop(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
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

private fun mediaTaskStateName(state: MediaTaskState): String = when (state) {
    MediaTaskState.WAITING -> "排队中"
    MediaTaskState.ANALYZING -> "正在解析"
    MediaTaskState.DOWNLOADING -> "下载中"
    MediaTaskState.PAUSED -> "已暂停"
    MediaTaskState.INTERRUPTED -> "可恢复"
    MediaTaskState.COMPLETED -> "已完成"
    MediaTaskState.FAILED -> "失败"
    MediaTaskState.CANCELLED -> "已取消"
}

private fun formatSpeed(value: Long): String = if (value <= 0) "" else "${formatBytes(value)}/s"

private fun formatBytes(value: Long): String {
    if (value < 0) return "未知"
    if (value < 1024) return "$value B"
    val unit = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(1, 4)
    val names = arrayOf("B", "KB", "MB", "GB", "TB")
    return "%.1f %s".format(value / 1024.0.pow(unit), names[unit])
}
