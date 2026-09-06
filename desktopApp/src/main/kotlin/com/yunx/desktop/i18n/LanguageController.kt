package com.yunx.desktop.i18n

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import com.jiexi.core.i18n.AppLanguage
import com.jiexi.core.i18n.UiStrings
import java.util.prefs.Preferences

object LanguageController {
    private val preferences=Preferences.userRoot().node("com/yunx/desktop/language")
    var language by mutableStateOf(AppLanguage.fromTag(preferences.get("ui_language","zh-CN")))
        private set
    fun select(value: AppLanguage) {
        preferences.put("ui_language",value.tag)
        language=value
    }
}

fun tr(template: String, vararg args: Any?): String = UiStrings.text(template,LanguageController.language,*args)
fun brandName(): String = if(AppLanguage.resolved(LanguageController.language) == AppLanguage.ENGLISH) "JieXi" else "解析"

@Composable
fun LanguageSetting() {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("语言 / Language",style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={ expanded=true }) { Text(LanguageController.language.nativeName) }
        DropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) {
            AppLanguage.entries.forEach { item ->
                DropdownMenuItem(text={ Text((if(item==LanguageController.language) "✓ " else "")+item.nativeName) },
                    onClick={ LanguageController.select(item); expanded=false })
            }
        }
        Text(tr("即时生效，不会中断下载。官方网页和第三方错误保留原文。"),style=MaterialTheme.typography.bodySmall)
    }
}
