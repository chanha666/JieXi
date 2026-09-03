package com.yunx.desktop.system

import com.sun.jna.Library
import com.sun.jna.Native

/** 下载期间请求 Windows 保持系统唤醒；失败时静默降级，不影响下载。 */
object WindowsPowerGuard {
    private const val ES_CONTINUOUS = -0x80000000
    private const val ES_SYSTEM_REQUIRED = 0x00000001

    private interface KernelPower : Library {
        fun SetThreadExecutionState(flags: Int): Int
    }

    private val api by lazy { runCatching { Native.load("kernel32", KernelPower::class.java) }.getOrNull() }

    fun setDownloading(active: Boolean) {
        val flags = if (active) ES_CONTINUOUS or ES_SYSTEM_REQUIRED else ES_CONTINUOUS
        runCatching { api?.SetThreadExecutionState(flags) }
    }
}
