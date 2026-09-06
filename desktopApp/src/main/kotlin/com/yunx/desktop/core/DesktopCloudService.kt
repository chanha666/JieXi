package com.yunx.desktop.core

import com.yunx.app.data.network.*
import com.yunx.app.data.network.model.*
import com.yunx.desktop.security.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.time.OffsetDateTime

/** Uses the same provider APIs as Android; never caches signed download URLs. */
class DesktopCloudService(private val store: CredentialStore) {
    private val quark = QuarkApi()
    private val uc = UCApi()
    private val baidu = BaiduApi()
    private val c139 = C139Api()
    private val pan123 = Pan123Api()
    private val xunlei = XunleiApi().apply {
        refreshTokenProvider = { deviceId ->
            store.get(CredentialKey.XUNLEI_REFRESH_TOKEN)?.let { refresh ->
                refreshToken(refresh, deviceId)?.also { (access, nextRefresh) ->
                    store.put(CredentialKey.XUNLEI_ACCESS_TOKEN, access)
                    store.put(CredentialKey.XUNLEI_REFRESH_TOKEN, nextRefresh)
                }
            }
        }
    }
    private val device get() = store.get(CredentialKey.XUNLEI_DEVICE_ID) ?: XunleiDeviceFingerprint.deviceId()
    private val captcha get() = store.get(CredentialKey.XUNLEI_CAPTCHA_TOKEN).orEmpty()
    fun credential(platform: SharePlatform): String = store.get(key(platform))
        ?: error("请先到「我的 → 网盘与账号」登录 " + DesktopResolver.platformName(platform))

    suspend fun list(platform: SharePlatform, parent: String): List<ShareFile> {
        val c = credential(platform)
        return when (platform) {
            SharePlatform.QUARK, SharePlatform.UC -> {
                val all = mutableListOf<ShareFile>()
                var page = 1
                do {
                    val batch = (if (platform == SharePlatform.QUARK) quark.listCloudFiles(parent, c, page, 100)
                        else uc.listCloudFiles(parent, c, page, 100)) ?: error("目录读取失败，请检查登录状态")
                    val fresh = batch.filter { item -> all.none { it.fid == item.fid } }
                    all += fresh
                    if (batch.size < 100 || fresh.isEmpty()) break
                    page++
                } while (page <= 1000)
                all
            }
            SharePlatform.BAIDU -> {
                val all = mutableListOf<ShareFile>()
                for(page in 1..1000) {
                    val batch = baidu.listCloudFiles(parent,c,page)
                    val fresh = batch.filter { item -> all.none { it.fid == item.fid } }
                    all += fresh
                    if(batch.size < 100 || fresh.isEmpty()) break
                }
                all
            }
            SharePlatform.C139 -> {
                val all = mutableListOf<ShareFile>()
                var cursor: String? = null
                val seen = mutableSetOf<String>()
                do {
                    val (batch, next) = c139.listCloudFiles(parent, c, cursor)
                    all += batch
                    cursor = next?.takeIf { it.isNotBlank() && seen.add(it) }
                } while (cursor != null)
                all.distinctBy { it.fid }
            }
            SharePlatform.PAN123, SharePlatform.XUNLEI -> {
                val all = mutableListOf<ShareFile>()
                var cursor = if(platform == SharePlatform.PAN123) "0" else ""
                val seen = mutableSetOf(cursor)
                var page = 1
                do {
                    val (batch,next) = if(platform == SharePlatform.PAN123) pan123.listCloudFiles(parent,c,cursor,page)
                        else xunlei.getFilesPage(parent,c,device,captcha,cursor)
                    all += batch
                    if(next.isNullOrBlank() || next == "-1" || !seen.add(next)) break
                    cursor = next; page++
                } while(page <= 1000)
                all.distinctBy { it.fid }
            }
        }
    }
    suspend fun collectFiles(p: SharePlatform, selected: List<ShareFile>): List<ShareFile> {
        val result = mutableListOf<ShareFile>()
        val pending = java.util.ArrayDeque(selected)
        val seen = mutableSetOf<String>()
        while(pending.isNotEmpty()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val file = pending.removeFirst()
            if(!seen.add(file.fid)) continue
            require(seen.size <= 10000) { "单次最多展开 10000 项，请分批选择" }
            if(file.isdir) pending.addAll(list(p, directory(p,file))) else result += file
        }
        return result
    }
    suspend fun quota(p: SharePlatform): QuotaInfo? {
        val c = credential(p)
        return when(p) {
            SharePlatform.QUARK -> quark.getQuota(c)
            SharePlatform.UC -> uc.getQuota(c)
            SharePlatform.BAIDU -> baidu.getQuota(c)
            SharePlatform.C139 -> c139.getQuota(c)
            SharePlatform.PAN123 -> pan123.getQuota(c)
            SharePlatform.XUNLEI -> xunlei.getQuota(c, device, captcha)
        }
    }
    suspend fun download(p: SharePlatform, file: ShareFile): DownloadLink {
        require(!file.isdir)
        val c = credential(p)
        val link = when(p) {
            SharePlatform.QUARK -> quark.getDownloadLink(file.fid, c)
            SharePlatform.UC -> uc.getDownloadLink(file.fid, c)
            SharePlatform.BAIDU -> DownloadLink(file.fid, file.fname, baidu.locateDownload(file.fidToken, c), file.fsize)
            SharePlatform.C139 -> c139.getDownloadUrl(file.fid, c)
            SharePlatform.PAN123 -> pan123.getDownloadLink(file, c)
            SharePlatform.XUNLEI -> xunlei.getFileDetail(file.fid, c, device, captcha)
        }
        return requireNotNull(link?.takeIf { it.downloadUrl.isNotBlank() }) { "未获取到下载地址，请检查账号权限" }
    }
    suspend fun rename(p: SharePlatform, file: ShareFile, name: String) {
        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\')) { "请输入有效文件名" }
        val c = credential(p)
        when(p) {
            SharePlatform.QUARK -> check(quark.renameFile(file.fid, name, c)) { "重命名失败" }
            SharePlatform.UC -> check(uc.renameFile(file.fid, name, c)) { "重命名失败" }
            SharePlatform.BAIDU -> check(baidu.renameFile(file.fidToken, name, c)) { "重命名失败" }
            SharePlatform.C139 -> check(c139.renameFile(file.fid, name, c)) { "重命名失败" }
            SharePlatform.PAN123 -> pan123.renameFile(file.fid, name, c)
            SharePlatform.XUNLEI -> check(xunlei.renameFile(file.fid, name, c, device, captcha)) { "重命名失败" }
        }
    }
    suspend fun move(p: SharePlatform, files: List<ShareFile>, dest: String) {
        val c = credential(p)
        require(files.none { directory(p, it) == dest }) { "不能移动到自身目录" }
        when(p) {
            SharePlatform.QUARK -> files.forEach { checkNotNull(quark.moveFile(it.fid, dest, c)) { "移动提交失败" } }
            SharePlatform.UC -> files.forEach { checkNotNull(uc.moveFile(it.fid, dest, c)) { "移动提交失败" } }
            SharePlatform.BAIDU -> check(baidu.moveFiles(files.map { it.fidToken }, dest, c)) { "移动失败" }
            SharePlatform.C139 -> await139(checkNotNull(c139.moveFiles(files.map { it.fid }, dest, c)), c)
            SharePlatform.PAN123 -> pan123.moveFiles(files.map { it.fid }, dest, c)
            SharePlatform.XUNLEI -> {
                val id = checkNotNull(xunlei.moveFile(files.map { it.fid }, dest, c, device, captcha))
                check(xunlei.pollTask(id, c, device, captcha)) { "移动任务未完成，请刷新确认" }
            }
        }
    }
    suspend fun delete(p: SharePlatform, files: List<ShareFile>) {
        val c = credential(p)
        when(p) {
            SharePlatform.QUARK -> files.forEach { checkNotNull(quark.deleteFile(it.fid, c)) { "删除提交失败" } }
            SharePlatform.UC -> files.forEach { checkNotNull(uc.deleteFile(it.fid, c)) { "删除提交失败" } }
            SharePlatform.BAIDU -> check(baidu.deleteFiles(files.map { it.fidToken }, c)) { "删除失败" }
            SharePlatform.C139 -> await139(checkNotNull(c139.deleteFiles(files.map { it.fid }, c)), c)
            SharePlatform.PAN123 -> pan123.deleteFiles(files, c)
            SharePlatform.XUNLEI -> check(xunlei.deleteFiles(files.map { it.fid }, c, device, captcha)) { "删除失败" }
        }
    }
    suspend fun share(p: SharePlatform, files: List<ShareFile>, days: Int, password: String): ShareInfo {
        require(files.isNotEmpty())
        require(password.isEmpty() || password.matches(Regex("[A-Za-z0-9]{4}"))) { "提取码需为 4 位字母或数字" }
        val c = credential(p)
        val ids = files.map { it.fid }
        val title = if (files.size == 1) files.first().fname else "分享 ${files.size} 个文件"
        val expiry = when(days) { 1 -> 2; 7 -> 3; 30 -> 4; else -> 1 }
        val urlType = if (password.isEmpty()) 1 else 2
        return when(p) {
            SharePlatform.QUARK -> quark.getShareInfo(checkNotNull(quark.createShare(ids, title, urlType, password, expiry, c)), c)
            SharePlatform.UC -> uc.getShareInfo(checkNotNull(uc.createShare(ids, title, urlType, password, expiry, c)), c)
            SharePlatform.BAIDU -> {
                require(password.length == 4) { "百度分享需要 4 位提取码" }
                val result = baidu.createShare(ids, days, password, c)
                ShareInfo(result.link, result.pwd, result.shareId, title, expiry)
            }
            SharePlatform.C139 -> c139.createShare(files.filterNot { it.isdir }.map { it.fid }, files.filter { it.isdir }.map { it.fid }, days.takeIf { it > 0 }, title, c)
            SharePlatform.PAN123 -> pan123.createShare(ids, title, if(days == 0) Pan123Constants.EXPIRATION_FOREVER else OffsetDateTime.now().plusDays(days.toLong()).toString(), password, c)
            SharePlatform.XUNLEI -> xunlei.createShare(ids, title, if(days == 0) "-1" else days.toString(), c, device, captcha, password)
        } ?: error("平台未返回分享链接")
    }
    private suspend fun await139(id: String, c: String) {
        repeat(30) {
            val status = c139.getTask(id, c)
            check(status.results.none { it.second.isNotBlank() && it.second != "0000" }) { "平台操作失败，请刷新确认" }
            if (status.status == "Succeed" || status.status == "SUCCEEDED" || status.progress >= 100) return
            if (status.status.contains("fail", true)) error("网盘任务失败，请刷新确认")
            delay(1000)
        }
        error("任务仍在平台处理，请刷新确认，不要重复提交")
    }
    companion object {
        fun key(p: SharePlatform) = when(p) {
            SharePlatform.QUARK -> CredentialKey.QUARK_COOKIE
            SharePlatform.UC -> CredentialKey.UC_COOKIE
            SharePlatform.BAIDU -> CredentialKey.BAIDU_COOKIE
            SharePlatform.C139 -> CredentialKey.C139_COOKIE
            SharePlatform.PAN123 -> CredentialKey.PAN123_TOKEN
            SharePlatform.XUNLEI -> CredentialKey.XUNLEI_ACCESS_TOKEN
        }
        fun root(p: SharePlatform) = when(p) { SharePlatform.BAIDU, SharePlatform.C139 -> "/"; SharePlatform.XUNLEI -> ""; else -> "0" }
        fun directory(p: SharePlatform, f: ShareFile) = if(p == SharePlatform.BAIDU) f.fidToken else f.fid
    }
}
