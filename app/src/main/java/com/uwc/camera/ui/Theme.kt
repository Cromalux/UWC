package com.uwc.camera.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Langage visuel « appareil photo natif », plus sobre :
 * monochrome blanc/gris sur l'image, un seul accent bleu Pixel posé avec parcimonie
 * (état actif + mise au point), rouge réservé à l'enregistrement, chrome translucide léger.
 * Typo = Roboto (police système Android → FontFamily par défaut).
 */

/** Bleu Pixel — le seul accent coloré. Uniquement pour l'état actif et la mise au point. */
val Accent = Color(0xFF8AB4F8)
/** Texte sombre lisible sur l'accent bleu clair. */
val OnAccent = Color(0xFF0B1220)
/** Rouge d'enregistrement — nulle part ailleurs. */
val Danger = Color(0xFFFF524A)

/** Texte secondaire sur l'image. */
val Muted = Color(0xFFC9CDD3)
/** Labels et texte tertiaire. */
val Faint = Color(0xFF9BA1A9)

/** Chrome translucide des puces posées sur l'aperçu (~noir 40 %). */
val PanelBg = Color(0x66000000)
/** Chrome un peu plus dense pour les puces cliquables. */
val ChromeStrong = Color(0x80000000)
/** Fond du panneau de réglages. */
val SheetBg = Color(0xF01A1D20)
/** Liseré fin blanc. */
val Hairline = Color(0x2EFFFFFF)

@Composable
fun UwcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = OnAccent,
            secondary = Accent, onSecondary = OnAccent,
            background = Color.Black, onBackground = Color.White,
            surface = Color(0xFF181B1E), onSurface = Color.White,
            surfaceVariant = Color(0xFF23262A), onSurfaceVariant = Muted,
            error = Danger, onError = Color.White,
            outline = Faint,
        ),
        content = content,
    )
}
