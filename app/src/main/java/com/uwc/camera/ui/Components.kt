package com.uwc.camera.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.camera.CameraCapabilities
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.FocusMode
import com.uwc.camera.camera.UnderwaterOptics
import java.util.Locale

/* ---------- Badges d'information ---------- */

@Composable
fun InfoBadge(text: String, muted: Boolean = false) {
    Surface(color = PanelBg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (muted) Muted else Color.White,
            fontSize = if (muted) 14.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RecBadge(ms: Long) {
    val blink by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recBlink",
    )
    Surface(color = PanelBg, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Danger.copy(alpha = blink), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("REC ${formatDuration(ms)}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
        FocusMode.LOCK_ON_LOCK -> "AF → figé au verrou · ${UnderwaterOptics.label(measuredDiopters)}"
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

@Composable
fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = PanelBg, labelColor = Color.White,
            selectedContainerColor = Accent, selectedLabelColor = Color.Black,
            disabledContainerColor = Color(0x66000000), disabledLabelColor = Color(0xFF6A6A6A),
        ),
        border = null,
    )
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> Chip(label(item), selected = item == selected, enabled = enabled(item)) { onSelect(item) } }
    }
}

@Composable
fun BigButton(text: String, color: Color = Accent, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) { Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF2A2A2A), shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
    ) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/* ---------- Blocs de réglages ---------- */

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (enabled) Color.White else Muted, fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = Color.White, fontSize = 16.sp)
            Text(valueLabel, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable
fun ColorDot(argb: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(argb))
            .border(if (selected) 4.dp else 1.dp, if (selected) Color.White else Color(0x55FFFFFF), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
fun Hint(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
}

/* ---------- HUD minimal (inspiré de l'app de référence) ---------- */

/** La seule commande tactile de l'écran principal : la roue crantée des réglages. */
@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(PanelBg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GearGlyph(Modifier.size(34.dp))
    }
}

/** Pastille cliquable de zoom, en bas au centre — un tap passe à l'objectif suivant. */
@Composable
fun ZoomPill(zoom: Float, onClick: () -> Unit) {
    Surface(color = PanelBg, shape = RoundedCornerShape(20.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Text(zoomLabel(zoom), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Gros chrono rouge, lisible même en eau trouble. */
@Composable
fun RecPill(ms: Long) {
    val blink by rememberInfiniteTransition(label = "recPill").animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recPillBlink",
    )
    Surface(color = Danger, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Color.White.copy(alpha = blink), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(formatDuration(ms), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Cadre rouge sur tout l'écran pendant l'enregistrement. */
@Composable
fun RecordingFrame() {
    Box(Modifier.fillMaxSize().border(5.dp, Danger))
}

/** Barre de progression de l'appui long sur Vol+ (verrouillage / déverrouillage). */
@Composable
fun LockProgress(progress: Float, locking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (locking) "Verrouillage…" else "Déverrouillage…", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.width(320.dp).height(14.dp),
            color = Accent,
            trackColor = Color(0x66000000),
        )
    }
}

@Composable
fun BottomLabel(text: String) {
    Surface(color = PanelBg, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Roue crantée dessinée à la main : un anneau épais et huit dents. */
@Composable
fun GearGlyph(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val ring = r * 0.28f
        drawCircle(color, radius = r * 0.55f, center = center, style = Stroke(width = ring))
        repeat(8) { i ->
            rotate(i * 45f, pivot = center) {
                drawLine(color, Offset(center.x, center.y - r * 0.62f), Offset(center.x, center.y - r * 0.98f), strokeWidth = ring * 0.95f, cap = StrokeCap.Butt)
            }
        }
    }
}

/** Bascule PHOTO / VIDÉO : deux demi-pastilles, la moitié active en ambre. */
@Composable
fun ModeToggle(mode: com.uwc.camera.camera.CaptureMode, onToggle: () -> Unit) {
    Surface(color = PanelBg, shape = RoundedCornerShape(20.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onToggle)) {
        Row {
            com.uwc.camera.camera.CaptureMode.entries.forEach { m ->
                val on = m == mode
                Text(
                    m.label,
                    Modifier.background(if (on) Accent else Color.Transparent, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
                    color = if (on) Color.Black else Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/** Bouton de fermeture : une croix dessinée, rond, cohérent avec la roue crantée. */
@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = Color(0xFFFFFFFF)
            drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 6f, cap = StrokeCap.Round)
        }
    }
}
