package com.yunx.desktop.core

import java.util.Properties

object DesktopBuildInfo {
    val version: String = Properties().run {
        val resource = checkNotNull(DesktopBuildInfo::class.java.classLoader.getResourceAsStream("app-version.properties"))
        resource.use(::load)
        getProperty("version").also { check(it.matches(Regex("\\d+\\.\\d+\\.\\d+"))) { "Invalid build version" } }
    }
}
