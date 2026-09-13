package com.uwc.camera.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.camera.CameraCapabilities
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.CaptureMode
import com.uwc.camera.camera.FocusMode
import com.uwc.camera.camera.UnderwaterOptics
import java.util.Locale

/* ---------- Lectures d'état (puces translucides) ---------- */

/**
 * Puce d'information sur l'aperçu : chrome translucide léger façon appli native.
 * `muted` = lecture secondaire (plus petite, gris atténué).
 */
@Composable
fun InfoBadge(text: String, muted: Boolean = false) {
    Surface(color = PanelBg, shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = if (muted) Muted else Color.White,
            fontSize = if (muted) 12.5.sp else 13.5.sp,
            fontWeight = if (muted) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
fun RecBadge(ms: Long) {
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recBlink",
    )
    Surface(color = PanelBg, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(Danger.copy(alpha = blink), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(ms), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun focusLabel(s: CameraSettings, measuredDiopters: Float, caps: CameraCapabilities?): String {
    if (caps != null && !caps.hasManualFocus) return "focus fixe"
    return when (s.focusMode) {
        FocusMode.CONTINUOUS -> "AF continu · ${UnderwaterOptics.label(measuredDiopters)}"
        FocusMode.LOCK_ON_LOCK -> "AF figé au verrou · ${UnderwaterOptics.label(measuredDiopters)}"
        FocusMode.MANUAL -> "MF ${UnderwaterOptics.label(s.focusDiopters)}"
    }
}

fun zoomLabel(z: Float): String =
    if (z == z.toInt().toFloat()) "${z.toInt()}×" else String.format(Locale.US, "%.1f×", z)

fun zoomStops(range: ClosedFloatingPointRange<Float>): List<Float> {
    val candidates = listOf(0.5f, 1f, 2f, 3f, 5f, 10f)
    val stops = (listOf(range.start) + candidates).filter { it >= range.start && it <= range.endInclusive }
    return stops.map { (it * 10).toInt() / 10f }.distinct().sorted()
}

/* ---------- Contrôles ---------- */

/** Puce de sélection : atone au repos, cerclée de bleu à l'état actif (jamais d'aplat plein). */
@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0x14FFFFFF), labelColor = Muted,
            selectedContainerColor = Accent.copy(alpha = 0.20f), selectedLabelColor = Accent,
            disabledContainerColor = Color(0x0AFFFFFF), disabledLabelColor = Color(0xFF5C6066),
        ),
        border = if (selected) BorderStroke(1.dp, Accent.copy(alpha = 0.55f)) else null,
    )
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items.forEach { item -> Chip(label(item), selected = item == selected, enabled = enabled(item)) { onSelect(item) } }
    }
}

@Composable
fun BigButton(text: String, color: Color = Accent, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = OnAccent),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    ) { Text(text, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Surface(
        color = Color(0x1FFFFFFF), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) { Text(text, Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium) }
}

/* ---------- Blocs de réglages ---------- */

/** Titre de section : label gris atténué, discret (l'accent est réservé à l'état actif). */
@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title.uppercase(), color = Faint, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (enabled) Color.White else Faint, fontSize = 14.5.sp)
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = Color.White, fontSize = 14.5.sp)
            Text(valueLabel, color = Accent, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun ColorDot(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color(argb))
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
fun Hint(text: String) {
    Text(text, color = Faint, fontSize = 12.5.sp, lineHeight = 16.5.sp)
}

/* ---------- HUD ---------- */

/** Roue de réglages, façon icône Material sur chrome translucide. */
@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(PanelBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GearGlyph(Modifier.size(22.dp))
    }
}

/** Pastille de zoom cliquable — un tap passe à l'objectif suivant. Chiffre en mono. */
@Composable
fun ZoomPill(zoom: Float, onClick: () -> Unit) {
    Surface(color = ChromeStrong, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)) {
        Text(
            zoomLabel(zoom), Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Sélecteur de mode façon Google Camera : libellés côte à côte, l'actif en blanc
 * souligné d'un point bleu, l'autre en gris atténué.
 */
@Composable
fun ModeToggle(mode: CaptureMode, onToggle: () -> Unit) {
    Surface(color = PanelBg, shape = CircleShape, modifier = Modifier.clip(CircleShape).clickable(onClick = onToggle)) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CaptureMode.entries.forEach { m ->
                val on = m == mode
                Text(
                    m.label,
                    color = if (on) Accent else Faint,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

/** Gros chrono d'enregistrement, chiffres mono tabulaires. */
@Composable
fun RecPill(ms: Long) {
    val blink by rememberInfiniteTransition(label = "recPill").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recPillBlink",
    )
    Surface(color = ChromeStrong, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(Danger.copy(alpha = blink), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(formatDuration(ms), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Cadre rouge pendant l'enregistrement, épousant les coins arrondis RÉELS de l'écran. */
@Composable
fun RecordingFrame() {
    val view = LocalView.current
    val density = LocalDensity.current
    val radiusPx = remember(view) {
        val insets = view.rootWindowInsets
        if (android.os.Build.VERSION.SDK_INT >= 31 && insets != null) {
            listOf(
                android.view.RoundedCorner.POSITION_TOP_LEFT,
                android.view.RoundedCorner.POSITION_TOP_RIGHT,
                android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
                android.view.RoundedCorner.POSITION_BOTTOM_RIGHT,
            ).mapNotNull { insets.getRoundedCorner(it)?.radius }.maxOrNull() ?: 0
        } else 0
    }
    val deviceRadius = with(density) { radiusPx.toDp() }.let { if (it > 0.dp) it else 30.dp }
    // On rentre le cadre de l'épaisseur du trait pour qu'il ne soit pas rogné par le coin physique,
    // et on garde un arrondi concentrique (rayon écran − marge).
    val inset = 4.dp
    val radius = (deviceRadius - inset).coerceAtLeast(0.dp)
    Box(Modifier.fillMaxSize().padding(inset).border(3.dp, Danger, RoundedCornerShape(radius)))
}

/** Rappel discret au centre (déverrouillé) : verrouiller avant de plonger, avec le geste exact. */
@Composable
fun LockReminder() {
    Surface(color = PanelBg, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LockGlyph(Modifier.size(14.dp), Muted)
                Spacer(Modifier.width(8.dp))
                Text("Verrouille avant de te mettre à l'eau", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(3.dp))
            Text("Maintiens Vol +  ·  1,5 s", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

/** Barre de progression de l'appui long Vol+ (verrouillage / déverrouillage). */
@Composable
fun LockProgress(progress: Float, locking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (locking) "Verrouillage…" else "Déverrouillage…", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.width(300.dp).height(6.dp).clip(CircleShape),
            color = Accent,
            trackColor = Color(0x4DFFFFFF),
        )
    }
}

@Composable
fun BottomLabel(text: String) {
    Surface(color = PanelBg, shape = RoundedCornerShape(15.dp)) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
    }
}

/** Roue crantée dessinée : anneau + huit dents (icône au trait, style Material). */
@Composable
fun GearGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val ring = r * 0.24f
        drawCircle(color, radius = r * 0.5f, center = center, style = Stroke(width = ring))
        repeat(8) { i ->
            rotate(i * 45f, pivot = center) {
                drawLine(color, Offset(center.x, center.y - r * 0.6f), Offset(center.x, center.y - r * 0.98f), strokeWidth = ring, cap = StrokeCap.Round)
            }
        }
    }
}

/** Bouton de fermeture (croix au trait) sur chrome translucide. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color(0x1FFFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            drawLine(Color.White, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 4.5f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 4.5f, cap = StrokeCap.Round)
        }
    }
}

/** Petit cadenas fermé, dessiné au trait (icône Material). */
@Composable
fun LockGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.12f
        // corps
        val bodyTop = h * 0.46f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, bodyTop),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw, sw),
        )
        // anse
        drawArc(
            color = color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.3f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.44f),
            style = Stroke(width = sw),
        )
    }
}

/** Petit appareil photo au trait. */
@Composable
fun CameraGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.09f
        drawRoundRect(
            color = color, style = Stroke(width = sw),
            topLeft = Offset(w * 0.06f, h * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw * 1.5f, sw * 1.5f),
        )
        // bosse viseur
        drawLine(color, Offset(w * 0.34f, h * 0.28f), Offset(w * 0.42f, h * 0.16f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.66f, h * 0.28f), Offset(w * 0.58f, h * 0.16f), strokeWidth = sw, cap = StrokeCap.Round)
        // objectif
        drawCircle(color, radius = h * 0.17f, center = center, style = Stroke(width = sw))
    }
}

/** Petite caméra vidéo au trait (corps + objectif triangulaire). */
@Composable
fun VideoGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.09f
        drawRoundRect(
            color = color, style = Stroke(width = sw),
            topLeft = Offset(w * 0.08f, h * 0.3f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(sw, sw),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.68f, h * 0.42f); lineTo(w * 0.92f, h * 0.3f)
            lineTo(w * 0.92f, h * 0.7f); lineTo(w * 0.68f, h * 0.58f); close()
        }
        drawPath(path, color, style = Stroke(width = sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}
