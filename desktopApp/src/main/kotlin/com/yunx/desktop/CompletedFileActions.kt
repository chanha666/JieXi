package com.yunx.desktop

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.yunx.desktop.core.DesktopAppController
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Only one recorded, regular output file can be recycled; never recurse. */
internal fun recyclableOutput(file: File): Boolean =
    file.isAbsolute && file.parentFile != null && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompletedFileActions(controller: DesktopAppController, file: File?, onAgain: () -> Unit, onRemoved: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    FlowRow {
        TextButton(onClick = { runCatching { file?.let(controller::openFile) }.onFailure { message = "无法打开文件：${it.message}" } }, enabled = file?.isFile == true) { Text("打开文件") }
        TextButton(onClick = onAgain) { Text("重新下载") }
        TextButton(onClick = { confirm = true }, enabled = file?.let(::recyclableOutput) == true) { Text("删除本地文件…") }
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("移到回收站并删除任务？") },
        text = { Text("仅处理这一个已完成文件，可从 Windows 回收站恢复。\n${file?.absolutePath}") },
        confirmButton = { Button(onClick = {
            runCatching {
                require(file != null && recyclableOutput(file)) { "文件已移动、不存在或不是普通文件" }
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) { "系统不支持回收站，文件未删除" }
                check(Desktop.getDesktop().moveToTrash(file)) { "移入回收站失败，任务记录已保留" }
                onRemoved()
            }.onFailure { message = it.message }
            confirm = false
        }) { Text("移到回收站") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
