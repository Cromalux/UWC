package com.uwc.camera.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.CaptureMode
import com.uwc.camera.camera.PhotoFormat
import com.uwc.camera.camera.VideoProfile
import com.uwc.camera.camera.WhiteBalance
import java.util.Locale
import kotlin.math.abs

/** HUD en mode déverrouillé : infos, déclencheur, verrou, et la barre de chips (mode / format / peaking / zoom). */
@Composable
fun ControlsOverlay(vm: UwcViewModel, settings: CameraSettings, settingsOpen: Boolean, onToggleSettings: () -> Unit) {
    val isRecording by vm.controller.isRecording.collectAsState()
    val recMs by vm.controller.recordingMs.collectAsState()
    val activeInfo by vm.controller.activeInfo.collectAsState()
    val zoomRange by vm.controller.zoomRange.collectAsState()
    val focusD by vm.controller.focusDiopters.collectAsState()
    val caps by vm.controller.capabilities.collectAsState()
    val peakingActive by vm.controller.peakingActive.collectAsState()
    val shotCount by vm.shotCount.collectAsState()

    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {

        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoBadge(activeInfo.ifEmpty { "initialisation…" })
            if (isRecording) RecBadge(recMs)
            InfoBadge(focusLabel(settings, focusD, caps), muted = true)
            if (settings.whiteBalance == WhiteBalance.MANUAL) {
                InfoBadge(String.format(Locale.FRANCE, "WB manuel · R×%.1f · B×%.1f", settings.wbRedGain, settings.wbBlueGain), muted = true)
            }
            if (settings.peakingEnabled && !peakingActive && activeInfo.isNotEmpty()) InfoBadge("peaking indisponible", muted = true)
            if (shotCount > 0) InfoBadge("$shotCount photo${if (shotCount > 1) "s" else ""}", muted = true)
        }

        TextButton(onClick = onToggleSettings, modifier = Modifier.align(Alignment.TopEnd)) {
            Text(if (settingsOpen) "FERMER" else "RÉGLAGES", color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        if (!settingsOpen) {
            Column(
                Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ShutterButton(mode = settings.mode, recording = isRecording) { vm.shutter() }
                BigButton("VERROUILLER") { vm.toggleLock() }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureMode.entries.forEach { m ->
                    Chip(m.label, selected = settings.mode == m, enabled = !isRecording) { vm.update { it.copy(mode = m) } }
                }
                Spacer(Modifier.width(10.dp))
                if (settings.mode == CaptureMode.PHOTO) {
                    PhotoFormat.entries.forEach { f ->
                        val ok = caps?.let { c -> c.coercePhotoFormat(f) == f } ?: true
                        Chip(f.label, selected = settings.photoFormat == f, enabled = ok) { vm.update { it.copy(photoFormat = f) } }
                    }
                } else {
                    VideoProfile.entries.forEach { p ->
                        val ok = p == VideoProfile.SDR || (caps?.supportsHlg10 ?: true)
                        Chip(p.label, selected = settings.videoProfile == p, enabled = ok && !isRecording) { vm.update { it.copy(videoProfile = p) } }
                    }
                }
                Spacer(Modifier.width(10.dp))
                Chip("PEAKING", selected = settings.peakingEnabled, enabled = !isRecording) {
                    vm.update { it.copy(peakingEnabled = !it.peakingEnabled) }
                }
                Spacer(Modifier.width(10.dp))
                zoomStops(zoomRange).forEach { z ->
                    Chip(zoomLabel(z), selected = abs(settings.zoomRatio - z) < 0.05f) { vm.update { it.copy(zoomRatio = z) } }
                }
            }
        }
    }
}
