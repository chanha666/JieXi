package com.fuke.mobile

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Cream = Color(0xFFFFF8ED)
val Paper = Color(0xFFFFFCF7)
val Ink = Color(0xFF30241D)
val Muted = Color(0xFF806B5E)
val Orange = Color(0xFFC94F00)
val OrangeSoft = Color(0xFFFFE9D3)
val Line = Color(0xFFE9D9C8)
val Success = Color(0xFF2E7D63)
val Danger = Color(0xFFB63E35)

private val scheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF8E5A37),
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    outline = Line,
    error = Danger
)

@Composable
fun FukeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = Cream.value.toInt()
        window.navigationBarColor = Cream.value.toInt()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
