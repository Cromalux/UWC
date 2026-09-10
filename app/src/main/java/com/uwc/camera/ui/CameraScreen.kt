package com.uwc.camera.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.BindConfig
import com.uwc.camera.camera.CameraSettings
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(vm: UwcViewModel) {
    val settings by vm.settings.collectAsState()
    val locked by vm.locked.collectAsState()
    val blackout by vm.blackout.collectAsState()
    val ready by vm.ready.collectAsState()
    val progress by vm.unlockProgress.collectAsState()
    val isRecording by vm.controller.isRecording.collectAsState()
    val recMs by vm.controller.recordingMs.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showDiag by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(locked) { if (locked) { showSettings = false; showDiag = false; showHelp = false } }

    PermissionGate {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraPreview(vm, settings, ready)
            ShutterFlash(vm)
            if (locked) {
                LockedHud(vm, settings)
            } else {
                ControlsOverlay(vm, settings, onOpenSettings = { showSettings = true })
                if (showSettings) SettingsSheet(vm, settings, onClose = { showSettings = false }, onDiagnostics = { showDiag = true }, onHelp = { showHelp = true })
                if (isRecording) Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) { RecPill(recMs) }
            }
            if (isRecording) RecordingFrame()
            if (progress > 0f) Box(Modifier.align(Alignment.Center)) { LockProgress(progress, locking = !locked) }
            StatusToast(vm)
            if (locked && blackout) BlackoutOverlay(progress)
            if (showDiag) DiagnosticsDialog(vm) { showDiag = false }
            if (ready && (showHelp || !settings.onboardingDone)) {
                OnboardingDialog {
                    showHelp = false
                    if (!settings.onboardingDone) vm.update { it.copy(onboardingDone = true) }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(vm: UwcViewModel, settings: CameraSettings, ready: Boolean) {
    val owner = LocalLifecycleOwner.current
    val bindConfig = BindConfig.from(settings)
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var laidOut by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    doOnLayout { laidOut = true }
                    view = this
                }
            },
        )
        val v = view
        // Rebind uniquement sur changement structurel (mode / format / profil / peaking / stab).
        LaunchedEffect(bindConfig, ready, laidOut, v) {
            if (ready && laidOut && v != null) vm.controller.bind(owner, v, vm.settings.value)
        }
        if (settings.peakingEnabled) PeakingOverlay(vm.controller.peaking)
    }
}

@Composable
private fun ShutterFlash(vm: UwcViewModel) {
    val flashAt by vm.flashAt.collectAsState()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(flashAt) {
        if (flashAt > 0L) { alpha.snapTo(0.85f); alpha.animateTo(0f, tween(220)) }
    }
    if (alpha.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alpha.value)))
}

@Composable
private fun StatusToast(vm: UwcViewModel) {
    val status by vm.status.collectAsState()
    val s = status ?: return
    LaunchedEffect(s.id) { delay(2800); vm.clearStatus(s.id) }
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(color = PanelBg, shape = RoundedCornerShape(12.dp)) {
            Text(s.text, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BlackoutOverlay(progress: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (progress > 0f) LockProgress(progress, locking = false)
        else Text("écran éteint · Vol+ 1,5 s pour déverrouiller", color = Color(0xFF2E2E2E), fontSize = 14.sp)
    }
}
