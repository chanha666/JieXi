package com.jiexi.core.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedLinkClassifierTest {
    @Test
    fun `extracts cloud link and passcode from share text`() {
        val result = UnifiedLinkClassifier.classifyText(
            "夸克分享：https://pan.quark.cn/s/abc123 提取码：9K2a"
        ).single()

        assertEquals(LinkKind.CLOUD_SHARE, result.kind)
        assertEquals(LinkPlatform.QUARK, result.platform)
        assertEquals("9K2a", result.passcode)
    }

    @Test
    fun `classifies public video platforms and strips punctuation`() {
        val links = UnifiedLinkClassifier.classifyText(
            "先看 https://www.bilibili.com/video/BV1xx，另一个 https://x.com/user/status/123。"
        )

        assertEquals(listOf(LinkPlatform.BILIBILI, LinkPlatform.X), links.map { it.platform })
        assertTrue(links.all { it.kind == LinkKind.VIDEO_PAGE })
        assertTrue(links.none { it.normalizedUrl.endsWith("，") || it.normalizedUrl.endsWith("。") })
    }

    @Test
    fun `detects direct media and de-duplicates normalized links`() {
        val links = UnifiedLinkClassifier.classifyText(
            "https://cdn.example.com/a/video.MP4 https://cdn.example.com/a/video.MP4"
        )

        assertEquals(1, links.size)
        assertEquals(LinkKind.DIRECT_MEDIA, links.single().kind)
        assertEquals(LinkPlatform.DIRECT, links.single().platform)
    }

    @Test
    fun `marks protected commercial sites honestly`() {
        val result = UnifiedLinkClassifier.classifyUrl("https://v.qq.com/x/cover/example.html")!!

        assertEquals(LinkPlatform.TENCENT_VIDEO, result.platform)
        assertTrue(result.mayRequireLogin)
        assertTrue(result.warning!!.contains("DRM"))
    }

    @Test
    fun `unknown web links are routed to generic detection`() {
        val result = UnifiedLinkClassifier.classifyUrl("https://example.com/watch/123")!!

        assertEquals(LinkKind.UNKNOWN, result.kind)
        assertEquals(LinkPlatform.OTHER, result.platform)
    }

    @Test
    fun `rejects non web schemes`() {
        assertNull(UnifiedLinkClassifier.classifyUrl("file:///D:/video.mp4"))
    }
}
