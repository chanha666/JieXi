package com.yunx.desktop.system

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/** Native WLAN status; no network traffic, credentials, or adapter changes. */
object WindowsWifiConnection {
    private interface Wlan : StdCallLibrary {
        fun WlanOpenHandle(version: Int, reserved: Pointer?, negotiated: IntByReference, handle: PointerByReference): Int
        fun WlanEnumInterfaces(handle: Pointer, reserved: Pointer?, list: PointerByReference): Int
        fun WlanFreeMemory(memory: Pointer)
        fun WlanCloseHandle(handle: Pointer, reserved: Pointer?): Int
    }
    private val api by lazy { runCatching { Native.load("wlanapi", Wlan::class.java) }.getOrNull() }
    @Volatile private var lastCheck = 0L
    @Volatile private var connected = false
    @Synchronized fun isConnected(): Boolean {
        if(System.currentTimeMillis() - lastCheck < 2000) return connected
        connected = runCatching {
            val wlan = api ?: return@runCatching false
            val handle = PointerByReference()
            if(wlan.WlanOpenHandle(2,null,IntByReference(),handle) != 0) return@runCatching false
            val list = PointerByReference()
            try {
                if(wlan.WlanEnumInterfaces(handle.value,null,list) != 0) return@runCatching false
                val memory = list.value ?: return@runCatching false
                val count = memory.getInt(0).coerceIn(0,256)
                // WLAN_INTERFACE_INFO = GUID(16) + WCHAR[256](512) + state(DWORD).
                (0 until count).any { memory.getInt(8L + it * 532L + 528L) == 1 }
            } finally {
                list.value?.let(wlan::WlanFreeMemory)
                wlan.WlanCloseHandle(handle.value,null)
            }
        }.getOrDefault(false)
        lastCheck = System.currentTimeMillis()
        return connected
    }
}
