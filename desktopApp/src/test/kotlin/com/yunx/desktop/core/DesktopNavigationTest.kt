package com.yunx.desktop.core

import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.model.ShareFile
import kotlin.test.*

class DesktopNavigationTest {
    @Test fun everyDestinationHasReachablePrimaryParent() {
        assertEquals(listOf(AppPage.RESOLVE, AppPage.DOWNLOADS, AppPage.MINE), AppPage.primary)
        AppPage.entries.forEach { assertTrue(it.primaryPage in AppPage.primary) }
        assertEquals(AppPage.RESOLVE, AppPage.MEDIA.primaryPage)
        assertEquals(AppPage.MINE, AppPage.CLOUD.primaryPage)
    }
    @Test fun everyAndroidDriveHasADesktopCredentialAndRoot() {
        assertEquals(6, SharePlatform.entries.size)
        SharePlatform.entries.forEach { assertNotNull(DesktopCloudService.key(it)); assertNotNull(DesktopCloudService.root(it)) }
        val file = ShareFile("id","目录",0,true,"/","","")
        assertEquals("id",DesktopCloudService.directory(SharePlatform.QUARK,file))
        assertEquals("/目录",DesktopCloudService.directory(SharePlatform.BAIDU,file.copy(fidToken="/目录")))
    }
}
