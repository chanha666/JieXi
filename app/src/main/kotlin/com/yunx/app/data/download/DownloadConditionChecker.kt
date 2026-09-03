package com.yunx.app.data.download

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.yunx.app.data.prefs.SettingsRepository

object DownloadConditionChecker {
    fun blockedReason(context: Context, settings: SettingsRepository): String? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "当前没有可用网络"
        if (settings.wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "仅 Wi-Fi 下载已开启"
        if (!settings.allowRoaming && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) return "当前处于漫游网络"
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (settings.chargingOnly && !charging) return "仅充电时下载已开启"
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (settings.pauseOnLowBattery && level >= 0 && level * 100 / scale.coerceAtLeast(1) <= 15 && !charging) return "电量低于 15%，任务已暂停"
        return null
    }
}
