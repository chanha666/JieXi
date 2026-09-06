package com.yunx.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.desktop.core.DesktopAppController
import com.yunx.desktop.security.DesktopAuthBackup
import kotlinx.coroutines.*
import java.io.File
import javax.swing.JFileChooser

@Composable
internal fun CredentialBackupPage(controller: DesktopAppController) {
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            try { message = withContext(Dispatchers.IO) { block() } }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message = if(e is javax.crypto.AEADBadTagException) "密码错误或备份已损坏" else e.message ?: "操作失败" }
            finally { busy = false; password = "" }
        }
    }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("认证备份", style = MaterialTheme.typography.headlineMedium)
        Text("与 Android 认证备份格式互通。导出文件使用密码加密，导入后仍可能需要平台重新验证。")
        Text("导入会替换备份中对应平台的本机登录信息，不改动未包含的平台。")
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("备份密码（至少 8 位）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy && password.length >= 8, onClick = {
                val chooser = JFileChooser(controller.settings.downloadDirectory).apply { selectedFile = File(currentDirectory, "解析认证-${System.currentTimeMillis()}.yunx") }
                if(chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    if(file.exists()) message = "为避免覆盖旧备份，请选择新的文件名"
                    else run {
                        val encrypted = DesktopAuthBackup.export(controller.credentialStore,password)
                        java.nio.file.Files.writeString(file.toPath(), encrypted, java.nio.file.StandardOpenOption.CREATE_NEW)
                        "加密备份已导出：${file.absolutePath}"
                    }
                }
            }) { Text("导出加密备份") }
            OutlinedButton(enabled = !busy && password.isNotBlank(), onClick = {
                val chooser = JFileChooser(controller.settings.downloadDirectory)
                if(chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) pendingImport = chooser.selectedFile
            }) { Text("导入认证备份") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it) }
    }
    pendingImport?.let { file -> AlertDialog(onDismissRequest = { pendingImport = null },
        title = { Text("确认恢复账号") }, text = { Text("将从 ${file.name} 恢复认证。对应平台的现有登录信息会被替换。") },
        confirmButton = { Button(onClick = {
            pendingImport = null
            run {
                require(file.isFile && file.length() <= 2_000_000) { "请选择有效且小于 2 MB 的备份文件" }
                val count = DesktopAuthBackup.import(controller.credentialStore,file.readText(),password)
                "已恢复 $count 个平台。请到网盘与账号确认登录状态。"
            }
        }) { Text("恢复") } },
        dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } }) }
}
