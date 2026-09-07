package com.jiexi.core.media

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransferLivenessTest {
    @Test fun `silence and retry spam expire without byte growth`() {
        var now = 0L
        val state = TransferLiveness(100) { now }
        state.sample(mapOf("video.part" to 0L))
        now = 99
        state.observe("WARNING: Retrying (10/10)")
        assertFalse(state.expired())
        now = 100
        assertTrue(state.expired())
    }
    @Test fun `byte growth extends deadline but repeated values do not`() {
        var now = 0L
        val state = TransferLiveness(100) { now }
        now = 90
        state.sample(mapOf("video.part" to 1L))
        now = 150
        state.sample(mapOf("video.part" to 1L))
        assertFalse(state.expired())
        now = 190
        assertTrue(state.expired())
    }
    @Test fun `merging gets bounded grace that log spam cannot extend`() {
        var now = 0L
        val state = TransferLiveness(100) { now }
        state.observe("[Merger] Merging formats")
        now = 899_999
        state.observe("[Merger] Merging formats")
        assertFalse(state.expired())
        now = 900_000
        assertTrue(state.expired())
    }
    @Test fun `stage text never exposes signed urls or raw errors`() {
        assertEquals("正在获取 YouTube 媒体信息", TransferLiveness.stage("[youtube] https://example.com?token=secret"))
        assertEquals("连接失败，正在重试", TransferLiveness.stage("WARNING: timed out"))
        assertEquals(null, TransferLiveness.stage("cookie=private"))
    }
}
