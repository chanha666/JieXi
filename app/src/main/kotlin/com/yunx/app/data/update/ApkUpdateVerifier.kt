package com.yunx.app.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

internal data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerSha256: Set<String>
)

internal data class ApkVerification(val accepted: Boolean, val message: String)

/** Verifies APK identity before an update is exposed to Android's installer. */
internal object ApkUpdateVerifier {
    fun verify(context: Context, apk: File): ApkVerification = runCatching {
        require(apk.isFile && apk.length() > 0L) { "更新 APK 不存在或为空" }
        val manager = context.packageManager
        val installed = packageInfo(manager, context.packageName, archive = false)
            ?: error("无法读取当前应用签名")
        val candidate = packageInfo(manager, apk.absolutePath, archive = true)
            ?: error("下载文件不是有效的 Android 安装包")
        compare(identity(installed), identity(candidate))
    }.getOrElse { ApkVerification(false, it.message ?: "更新 APK 身份校验失败") }

    internal fun compare(installed: ApkIdentity, candidate: ApkIdentity): ApkVerification {
        if (candidate.packageName != installed.packageName) {
            return ApkVerification(false, "更新包名不匹配，已拒绝安装")
        }
        if (candidate.versionCode <= installed.versionCode) {
            return ApkVerification(false, "更新版本必须高于当前版本")
        }
        if (installed.signerSha256.isEmpty() || candidate.signerSha256.isEmpty()) {
            return ApkVerification(false, "无法读取更新包发行签名")
        }
        if (installed.signerSha256.intersect(candidate.signerSha256).isEmpty()) {
            return ApkVerification(false, "更新包不是作者发行签名，已拒绝安装")
        }
        return ApkVerification(true, "APK 包名、版本与发行签名校验通过")
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(manager: PackageManager, value: String, archive: Boolean): PackageInfo? {
        val legacyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            val flags = PackageManager.PackageInfoFlags.of(legacyFlags.toLong())
            if (archive) manager.getPackageArchiveInfo(value, flags) else manager.getPackageInfo(value, flags)
        } else {
            if (archive) manager.getPackageArchiveInfo(value, legacyFlags) else manager.getPackageInfo(value, legacyFlags)
        }
    }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): ApkIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> emptyArray()
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory ?: signing.apkContentsSigners
            }
        } else {
            info.signatures ?: emptyArray()
        }
        return ApkIdentity(
            packageName = info.packageName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            signerSha256 = signatures.mapTo(linkedSetOf()) { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }
        )
    }
}
