package com.uwc.camera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette haute visibilité : fond noir, accent ambre (très lisible sur un fond de scène bleu). */
val Accent = Color(0xFFFFB300)
val Danger = Color(0xFFFF3B30)
val PanelBg = Color(0xD9000000)
val Muted = Color(0xFFB8B8B8)

@Composable
fun UwcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            secondary = Accent, onSecondary = Color.Black,
            background = Color.Black, onBackground = Color.White,
            surface = Color(0xFF141414), onSurface = Color.White,
            surfaceVariant = Color(0xFF242424), onSurfaceVariant = Muted,
            error = Danger,
        ),
        content = content,
    )
}
