package com.jiexi.core.i18n

import kotlin.test.*
import java.util.Locale

class UiStringsTest {
    @Test fun `all languages expose matching nonempty catalog entries`() {
        assertTrue(UiCatalog.english.size >= 700)
        assertEquals(UiCatalog.english.keys,UiCatalog.traditional.keys)
        assertTrue(UiCatalog.english.values.all { it.isNotBlank() })
        assertTrue(UiCatalog.traditional.values.all { it.isNotBlank() })
    }
    @Test fun `translations preserve placeholder indexes`() {
        val holes=Regex("\\{\\d+\\}")
        for(catalog in listOf(UiCatalog.english,UiCatalog.traditional)) catalog.forEach { (key,value) ->
            assertEquals(holes.findAll(key).map { it.value }.toList().sorted(),holes.findAll(value).map { it.value }.toList().sorted(),key)
        }
    }
    @Test fun `language tags persist roundtrip and unknown values safely fall back`() {
        AppLanguage.entries.forEach { assertEquals(it,AppLanguage.fromTag(it.tag)) }
        assertEquals(AppLanguage.SIMPLIFIED,AppLanguage.fromTag("unknown"))
    }
    @Test fun `system chooses traditional Chinese by region or script`() {
        listOf("zh-TW","zh-HK","zh-MO","zh-Hant").forEach {
            assertEquals(AppLanguage.TRADITIONAL,AppLanguage.resolved(AppLanguage.SYSTEM,Locale.forLanguageTag(it)))
        }
        assertEquals(AppLanguage.SIMPLIFIED,AppLanguage.resolved(AppLanguage.SYSTEM,Locale.forLanguageTag("zh-CN")))
        assertEquals(AppLanguage.ENGLISH,AppLanguage.resolved(AppLanguage.SYSTEM,Locale.forLanguageTag("de-DE")))
    }
    @Test fun `switching languages does not cache old text`() {
        assertEquals("下载",UiStrings.text("下载",AppLanguage.SIMPLIFIED))
        assertEquals("Downloads",UiStrings.text("下载",AppLanguage.ENGLISH))
        assertEquals("下載",UiStrings.text("下载",AppLanguage.TRADITIONAL))
        assertEquals("下载",UiStrings.text("下载",AppLanguage.SIMPLIFIED))
    }
    @Test fun `injected file names and placeholder-like data remain verbatim`() {
        val userFile="我的{1}视频.mp4"
        assertEquals("Delete “$userFile”?",UiStrings.text("确定删除「{0}」吗？",AppLanguage.ENGLISH,userFile))
        assertEquals(userFile,UiStrings.text(userFile,AppLanguage.ENGLISH))
    }
    @Test fun `paths URLs and unknown errors are unchanged`() {
        for(value in listOf("D:\\视频\\test.mp4","https://example.test/下载?a=1","HTTP 403: provider response"))
            assertEquals(value,UiStrings.text(value,AppLanguage.ENGLISH))
    }
    @Test fun `task counters preserve values and order`() {
        assertEquals("12 total · 3 active · 9 completed",UiStrings.text("共 {0} 项 · 进行中 {1} · 已完成 {2}",AppLanguage.ENGLISH,12,3,9))
    }
}
