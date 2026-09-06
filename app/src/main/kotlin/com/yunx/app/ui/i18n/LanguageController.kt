package com.yunx.app.ui.i18n

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.jiexi.core.i18n.AppLanguage
import com.jiexi.core.i18n.UiStrings

object LanguageController {
    var language by mutableStateOf(AppLanguage.SIMPLIFIED)
        private set
    fun initialize(context: Context) {
        language = AppLanguage.fromTag(context.getSharedPreferences("yunx_settings",Context.MODE_PRIVATE).getString("ui_language","zh-CN"))
    }
    fun select(context: Context, value: AppLanguage) {
        context.getSharedPreferences("yunx_settings",Context.MODE_PRIVATE).edit().putString("ui_language",value.tag).apply()
        language = value
    }
}

fun tr(template: String, vararg args: Any?): String = UiStrings.text(template,LanguageController.language,*args)

@Composable
fun LanguageSetting() {
    val context=LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("语言 / Language",style=MaterialTheme.typography.titleMedium)
        TextButton(onClick={ expanded=true }) { Text(LanguageController.language.nativeName) }
        DropdownMenu(expanded=expanded,onDismissRequest={ expanded=false }) {
            AppLanguage.entries.forEach { item ->
                DropdownMenuItem(text={ Text((if(item==LanguageController.language) "✓ " else "") + item.nativeName) },
                    onClick={ LanguageController.select(context,item); expanded=false })
            }
        }
        Text(tr("即时生效，不会中断下载。官方网页和第三方错误保留原文。"),style=MaterialTheme.typography.bodySmall)
    }
}
