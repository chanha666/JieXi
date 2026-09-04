package com.yunx.desktop.settings

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Author-owned support destinations shown by the Windows application. */
object DesktopSupportLinks {
    const val GITHUB_REPOSITORY = "https://github.com/chanha666/JieXi"
    const val GITHUB_ISSUES = "$GITHUB_REPOSITORY/issues/new"
    const val FEEDBACK_EMAIL = "3316109338@qq.com"

    fun feedbackEmailUri(subject: String = "解析 Windows 问题反馈"): URI {
        val encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8).replace("+", "%20")
        return URI.create("mailto:$FEEDBACK_EMAIL?subject=$encodedSubject")
    }
}
