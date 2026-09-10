package com.uwc.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.WhiteBalance
import java.util.Locale

/**
 * HUD déverrouillé, volontairement minimal : une pastille d'état, la roue des réglages,
 * la pastille de zoom. Photo, vidéo et verrouillage passent par les boutons de volume.
 */
@Composable
fun ControlsOverlay(vm: UwcViewModel, settings: CameraSettings, onOpenSettings: () -> Unit) {
    val activeInfo by vm.controller.activeInfo.collectAsState()
    val focusD by vm.controller.focusDiopters.collectAsState()
    val caps by vm.controller.capabilities.collectAsState()
    val peakingActive by vm.controller.peakingActive.collectAsState()
    val shotCount by vm.shotCount.collectAsState()

    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoBadge(activeInfo.ifEmpty { "initialisation…" })
            InfoBadge(focusLabel(settings, focusD, caps), muted = true)
            if (settings.whiteBalance == WhiteBalance.MANUAL) {
                InfoBadge(String.format(Locale.FRANCE, "WB manuel · R×%.1f · B×%.1f", settings.wbRedGain, settings.wbBlueGain), muted = true)
            }
            if (settings.peakingEnabled && !peakingActive && activeInfo.isNotEmpty()) InfoBadge("peaking indisponible", muted = true)
            if (shotCount > 0) InfoBadge("$shotCount photo${if (shotCount > 1) "s" else ""}", muted = true)
        }

        Box(Modifier.align(Alignment.TopEnd)) { GearButton(onClick = onOpenSettings) }

        Box(Modifier.align(Alignment.BottomCenter)) { ZoomPill(settings.zoomRatio) { vm.cycleLens() } }
    }
}
