package com.yunx.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.*
import com.yunx.desktop.security.*
import kotlinx.coroutines.*

@Composable
internal fun TokenLoginCard(store: CredentialStore, xunlei: Boolean, onSuccess: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<XunleiLoginStep?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { XunleiApi() }
    val device = remember { XunleiDeviceFingerprint.deviceId() }
    suspend fun finish(result: XunleiLoginStep) {
        step = result
        if(result.needSms) { message = "需要短信验证，请点击发送验证码"; return }
        check(result.sessionId.isNotBlank()) { result.message.ifBlank { "登录失败，请检查账号或验证码" } }
        val captcha = api.initCaptcha(device, username).orEmpty()
        val tokens = api.exchangeToken(result.sessionId, device, captcha) ?: error("无法换取登录凭据，请重试")
        store.put(CredentialKey.XUNLEI_ACCESS_TOKEN, tokens.first)
        store.put(CredentialKey.XUNLEI_REFRESH_TOKEN, tokens.second)
        store.put(CredentialKey.XUNLEI_CAPTCHA_TOKEN, captcha)
        store.put(CredentialKey.XUNLEI_DEVICE_ID, device)
        message = "登录成功，授权已加密保存"; password = ""; code = ""; step = null; expanded = false; onSuccess()
    }
    fun request(block: suspend () -> Unit) {
        if(busy) return
        busy = true
        scope.launch {
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { message = e.message ?: "登录未完成" }
            finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = !expanded }, enabled = !busy) { Text(if(expanded) "收起登录" else "登录") }
            TextButton(onClick = {
                val keys = if(xunlei) listOf(CredentialKey.XUNLEI_ACCESS_TOKEN, CredentialKey.XUNLEI_REFRESH_TOKEN, CredentialKey.XUNLEI_CAPTCHA_TOKEN, CredentialKey.XUNLEI_DEVICE_ID)
                    else listOf(CredentialKey.PAN123_TOKEN)
                keys.forEach(store::remove); message = "本机授权已移除"; onSuccess()
            }, enabled = !busy) { Text("退出账号") }
        }
        if(expanded) {
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("手机号 / 账号") }, singleLine = true, enabled = !busy)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码（不保存）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
            Button(enabled = !busy && username.isNotBlank() && password.isNotBlank(), onClick = {
                request {
                    if(xunlei) finish(api.loginWithPassword(username.trim(), password, device))
                    else {
                        val token = Pan123Api().login(username.trim(),password)
                        store.put(CredentialKey.PAN123_TOKEN,token)
                        password = ""; expanded = false; message = "登录成功，授权已加密保存"; onSuccess()
                    }
                }
            }) { Text(if(busy) "处理中…" else "登录") }
            if(xunlei) {
                OutlinedTextField(code,{ code = it },Modifier.fillMaxWidth(),label = { Text("短信验证码") },singleLine = true, enabled = !busy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy && username.isNotBlank(), onClick = { request {
                        val result = api.sendSms(username.trim(),device)
                        check(result.smsCreditKey.isNotBlank()) { result.message.ifBlank { "短信发送失败" } }
                        step = result; message = "验证码已发送"
                    } }) { Text("发送验证码") }
                    Button(enabled = !busy && code.isNotBlank() && !step?.smsCreditKey.isNullOrBlank(), onClick = { request {
                        val current = checkNotNull(step)
                        finish(api.smsLogin(username.trim(),code.trim(),current.smsCreditKey,current.smsToken,device))
                    } }) { Text("验证码登录") }
                }
            }
        }
        message?.let { Text(it) }
    }
}
