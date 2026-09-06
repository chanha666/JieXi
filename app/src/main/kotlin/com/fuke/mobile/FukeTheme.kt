package com.fuke.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.yunx.app.ui.theme.ComposeEmptyActivityTheme

// Media screens use the same live palette as the rest of the application.
val Cream: Color @Composable get() = MaterialTheme.colorScheme.background
val Paper: Color @Composable get() = MaterialTheme.colorScheme.surface
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Orange: Color @Composable get() = MaterialTheme.colorScheme.primary
val OrangeSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Success: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val Danger: Color @Composable get() = MaterialTheme.colorScheme.error

@Composable
fun FukeTheme(content: @Composable () -> Unit) {
    ComposeEmptyActivityTheme(content = content)
}
