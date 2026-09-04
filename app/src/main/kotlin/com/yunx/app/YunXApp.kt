package com.yunx.app

import android.app.Application
import android.util.Log
import com.fuke.mobile.Engine
import com.fuke.mobile.TaskStore
import com.yunx.app.crash.CrashHandler
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadConditionChecker
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.security.BookmarkSecurityPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YunXApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * 下载引擎属于进程，而不是某个 Activity/Compose 页面。
     * 旋转屏幕或系统重建 Activity 时仍复用同一个实例，避免旧协程失控、进度归零和重复下载。
     */
    val downloadManager: DownloadManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val settings = SettingsRepository(this)
        DownloadManager(
            context = applicationContext,
            dao = AppDatabase.get(this).downloadTaskDao(),
            downloader = ChunkDownloader { HttpClients.downloadClient() },
            threadProvider = settings::downloadThreadsFor,
            saveDirProvider = { settings.downloadDirUri },
            concurrencyProvider = { settings.maxConcurrentDownloads },
            speedLimitProvider = { settings.downloadSpeedLimit },
            retryCountProvider = { settings.downloadRetryCount },
            keepWhenLockedProvider = { settings.keepDownloadWhenLocked },
            showSpeedProvider = { settings.notificationShowSpeed },
            conditionBlocker = { DownloadConditionChecker.blockedReason(applicationContext, settings) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        TaskStore.initialize(this)
        // 迅雷动态设备指纹：首次启动生成并持久化（开源分发后每台设备独立指纹）
        com.yunx.app.data.network.XunleiDeviceFingerprint.init(this)
        applicationScope.launch {
            runCatching { Engine.initialize(this@YunXApp) }
                .onFailure { Log.e("JieXiMedia", "媒体解析核心初始化失败", it) }
            val database = AppDatabase.get(this@YunXApp)
            database.downloadTaskDao().markRunningAsInterrupted()
            val bookmarkDao = database.bookmarkDao()
            bookmarkDao.clearStoredPasscodes()
            bookmarkDao.listAllOnce().forEach { stored ->
                val safe = BookmarkSecurityPolicy.withoutStoredPasscode(stored)
                if (safe.link != stored.link || stored.pwd.isNotEmpty()) {
                    bookmarkDao.sanitizeStoredBookmark(stored.id, safe.link)
                }
            }
            val retentionDays = SettingsRepository(this@YunXApp).historyRetentionDays
            if (retentionDays == 0) database.historyDao().clear()
            else database.historyDao().deleteOlderThan(System.currentTimeMillis() - retentionDays * 86_400_000L)
        }
    }
}
