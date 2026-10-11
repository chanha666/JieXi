package com.fuke.mobile

import java.io.IOException

internal class MediaHttpException(val status: Int) : IOException("视频文件请求失败（HTTP $status）。")
internal class RangeUnsupportedException : IOException("服务器未接受分段请求，切换单连接下载。")

internal object DirectResponsePolicy {
    fun validate(status: Int, contentType: String?) {
        if (status !in 200..299) throw MediaHttpException(status)
        val mime = contentType.orEmpty().substringBefore(';').trim().lowercase()
        if (mime.startsWith("text/") || mime in setOf(
                "application/json", "application/xml", "application/xhtml+xml",
                "application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml"
            )) {
            throw IllegalStateException("服务器返回了网页或播放清单，而不是视频文件，请重新解析原链接。")
        }
    }
}
