package com.jiexi.core.i18n

import java.util.Locale

enum class AppLanguage(val tag: String, val nativeName: String) {
    SYSTEM("system", "跟随系统 / System"),
    SIMPLIFIED("zh-CN", "简体中文"),
    TRADITIONAL("zh-TW", "繁體中文"),
    ENGLISH("en", "English");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SIMPLIFIED
        fun resolved(language: AppLanguage, locale: Locale = Locale.getDefault()): AppLanguage =
            if(language != SYSTEM) language else if(locale.language != "zh") ENGLISH
            else if(locale.script.equals("Hant",true) || locale.country in setOf("TW","HK","MO")) TRADITIONAL
            else SIMPLIFIED
    }
}

/** Explicit UI templates only. Never pass file names, URLs, tokens or user input here. */
object UiStrings {
    fun text(template: String, language: AppLanguage, vararg args: Any?): String {
        val translated = when(AppLanguage.resolved(language)) {
            AppLanguage.ENGLISH -> UiCatalog.english[template] ?: template
            AppLanguage.TRADITIONAL -> UiCatalog.traditional[template] ?: template
            else -> template
        }
        // Single-pass substitution does not interpret placeholders inside user values.
        return if(args.isEmpty()) translated else Regex("\\{(\\d+)\\}").replace(translated) { match ->
            args.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
        }
    }
}
