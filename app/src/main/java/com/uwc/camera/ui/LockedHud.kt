package com.uwc.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings

/** HUD verrouillé : rien de cliquable ; état en haut, rappel de sortie en bas, gros chrono si REC. */
@Composable
fun LockedHud(vm: UwcViewModel, settings: CameraSettings) {
    val isRecording by vm.controller.isRecording.collectAsState()
    val recMs by vm.controller.recordingMs.collectAsState()
    val activeInfo by vm.controller.activeInfo.collectAsState()
    val focusD by vm.controller.focusDiopters.collectAsState()
    val caps by vm.controller.capabilities.collectAsState()
    val shotCount by vm.shotCount.collectAsState()

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Color(0xF2FFFFFF), shape = RoundedCornerShape(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)) {
                    LockGlyph(Modifier.size(15.dp), Color(0xFF111316))
                    Spacer(Modifier.width(7.dp))
                    Text("Verrouillé", color = Color(0xFF111316), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                }
            }
            InfoBadge(activeInfo)
            InfoBadge(focusLabel(settings, focusD, caps), muted = true)
            if (shotCount > 0) InfoBadge("$shotCount photo${if (shotCount > 1) "s" else ""}", muted = true)
        }

        if (isRecording) {
            Surface(Modifier.align(Alignment.Center), color = PanelBg, shape = RoundedCornerShape(16.dp)) {
                Text(formatDuration(recMs), Modifier.padding(horizontal = 28.dp, vertical = 10.dp), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            BottomLabel("Écran verrouillé · Vol+ 1,5 s pour déverrouiller")
        }
    }
}
