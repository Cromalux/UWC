package com.uwc.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.CaptureMode

/** HUD verrouillé : rien de cliquable, tout en gros, contrasté, lisible à travers un masque. */
@Composable
fun LockedHud(vm: UwcViewModel, settings: CameraSettings) {
    val isRecording by vm.controller.isRecording.collectAsState()
    val recMs by vm.controller.recordingMs.collectAsState()
    val activeInfo by vm.controller.activeInfo.collectAsState()
    val focusD by vm.controller.focusDiopters.collectAsState()
    val caps by vm.controller.capabilities.collectAsState()
    val progress by vm.unlockProgress.collectAsState()
    val shotCount by vm.shotCount.collectAsState()

    Box(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Accent, shape = RoundedCornerShape(8.dp)) {
                Text("VERROUILLÉ", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            InfoBadge(activeInfo)
            if (isRecording) RecBadge(recMs)
            InfoBadge(focusLabel(settings, focusD, caps), muted = true)
            if (shotCount > 0) InfoBadge("$shotCount photo${if (shotCount > 1) "s" else ""}", muted = true)
        }

        if (progress > 0f) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Maintiens Vol+ et Vol−…", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(320.dp).height(14.dp),
                    color = Accent,
                    trackColor = Color(0x66000000),
                )
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter), color = PanelBg, shape = RoundedCornerShape(10.dp)) {
            val shutter = if (settings.mode == CaptureMode.PHOTO) "photo" else "REC"
            Text(
                "Vol+ / Vol− : $shutter   ·   Vol− long : photo ⇄ vidéo   ·   Vol+ long : écran   ·   Vol+ & Vol− 2,5 s : déverrouiller",
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
        }
    }
}
