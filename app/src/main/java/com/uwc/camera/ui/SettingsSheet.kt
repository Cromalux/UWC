package com.uwc.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.FocusMode
import com.uwc.camera.camera.PeakingColors
import com.uwc.camera.camera.CaptureMode
import com.uwc.camera.camera.PhotoFormat
import com.uwc.camera.camera.VideoProfile
import com.uwc.camera.camera.ScreenMode
import com.uwc.camera.camera.UnderwaterOptics
import com.uwc.camera.camera.WhiteBalance
import java.util.Locale
import kotlin.math.roundToInt

/** Panneau latéral de réglages — uniquement accessible déverrouillé. */
@Composable
fun SettingsSheet(vm: UwcViewModel, s: CameraSettings, onClose: () -> Unit, onDiagnostics: () -> Unit, onHelp: () -> Unit) {
    val caps by vm.controller.capabilities.collectAsState()
    val zoomRange by vm.controller.zoomRange.collectAsState()
    val isRecording by vm.controller.isRecording.collectAsState()
    val ctx = LocalContext.current
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(recordAudio = granted) }
        if (!granted) vm.setStatus("Micro refusé — vidéo sans son")
    }

    Box(Modifier.fillMaxSize()) {
        // Zone gauche (aperçu) : un clic ferme le panneau.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose,
            ),
        )
        Surface(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(470.dp)
                // Absorbe les clics sur le panneau pour qu'ils ne traversent pas vers la zone de fermeture.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            color = SheetBg,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Réglages", Modifier.weight(1f), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    CloseButton(onClose)
                }

                Section("CAPTURE") {
                    Text("Mode (Vol− long pour basculer)", color = Muted, fontSize = 13.sp)
                    ChipRow(CaptureMode.entries, s.captureMode, label = { it.label }, enabled = { !isRecording }) { m ->
                        if (!isRecording) vm.update { it.copy(captureMode = m) }
                    }
                    if (s.captureMode == CaptureMode.PHOTO) {
                        Text("Format photo", color = Muted, fontSize = 13.sp)
                        ChipRow(PhotoFormat.entries, s.photoFormat, label = { it.label },
                            enabled = { f -> caps?.let { c -> c.coercePhotoFormat(f) == f } ?: true }) { f ->
                            vm.update { it.copy(photoFormat = f) }
                        }
                        val raw = s.photoFormat != PhotoFormat.JPEG
                        Text("Objectif", color = Muted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            zoomStops(zoomRange).forEach { z ->
                                Chip(zoomLabel(z), selected = kotlin.math.abs(s.zoomRatio - z) < 0.05f) { vm.setZoom(z) }
                            }
                        }
                        if (raw) Hint("Le 0,5× (ultra grand-angle) ne fait pas de RAW : en RAW/RAW+JPEG l'app reste sur le capteur principal. Pour le 0,5×, passe en JPEG ou en vidéo.")
                    } else {
                        Text("Profil vidéo", color = Muted, fontSize = 13.sp)
                        ChipRow(VideoProfile.entries, s.videoProfile, label = { it.label },
                            enabled = { p -> !isRecording && (p == VideoProfile.SDR || (caps?.supportsHlg10 ?: true)) }) { p ->
                            vm.update { it.copy(videoProfile = p) }
                        }
                        Text("Objectif", color = Muted, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            zoomStops(zoomRange).forEach { z ->
                                Chip(zoomLabel(z), selected = kotlin.math.abs(s.zoomRatio - z) < 0.05f) { vm.setZoom(z) }
                            }
                        }
                    }
                    SwitchRow("Anti-flou (vitesse rapide, ISO auto)", s.antiBlur, enabled = caps?.antiBlurFps != null) {
                        vm.update { it.copy(antiBlur = !it.antiBlur) }
                    }
                    Hint("Anti-flou : force une obturation courte pour figer le mouvement (utile en photo). Monte les ISO en basse lumière.")
                }

                Section("FOCUS PEAKING") {
                    SwitchRow("Activer", s.peakingEnabled) { vm.update { it.copy(peakingEnabled = !it.peakingEnabled) } }
                    LabeledSlider("Seuil (bas = plus sensible)", s.peakingThreshold.toFloat(), 10f..150f, "${s.peakingThreshold}") { v ->
                        vm.update { it.copy(peakingThreshold = v.roundToInt()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PeakingColors.options.forEach { (_, argb) ->
                            ColorDot(argb, selected = s.peakingColor == argb) { vm.update { it.copy(peakingColor = argb) } }
                        }
                    }
                }

                Section("MISE AU POINT") {
                    val manualOk = caps?.hasManualFocus ?: true
                    ChipRow(FocusMode.entries, s.focusMode, label = { it.label }, enabled = { it == FocusMode.CONTINUOUS || manualOk }) { m ->
                        vm.update { it.copy(focusMode = m) }
                    }
                    when (s.focusMode) {
                        FocusMode.MANUAL -> {
                            val maxD = caps?.minFocusDiopters?.takeIf { it > 0f } ?: 10f
                            LabeledSlider("Distance réelle", s.focusDiopters, 0f..maxD, UnderwaterOptics.label(s.focusDiopters)) { v ->
                                vm.update { it.copy(focusDiopters = v) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UnderwaterOptics.presetsMeters.forEach { (lbl, m) ->
                                    SmallButton(lbl) { vm.update { it.copy(focusDiopters = UnderwaterOptics.dioptersForRealMeters(m).coerceIn(0f, maxD)) } }
                                }
                            }
                            Hint("Distances réelles sous l'eau : la réfraction du hublot plat (sujet vu aux ¾ de sa distance) est déjà corrigée. En 0,5×, « 1 m » couvre pratiquement tout.")
                        }
                        FocusMode.LOCK_ON_LOCK -> Hint("L'AF travaille normalement ; au verrouillage, la distance courante est gelée jusqu'au déverrouillage. Cadre ton sujet type, vérifie au peaking, puis verrouille.")
                        FocusMode.CONTINUOUS -> Hint("AF continu du système. Peut « pomper » sur les particules ou un fond bleu uniforme.")
                    }
                }

                Section("BALANCE DES BLANCS") {
                    ChipRow(WhiteBalance.entries, s.whiteBalance, label = { it.label }) { wb -> vm.update { it.copy(whiteBalance = wb) } }
                    if (s.whiteBalance == WhiteBalance.MANUAL) {
                        LabeledSlider("Rouge", s.wbRedGain, 1f..4f, String.format(Locale.US, "×%.2f", s.wbRedGain)) { v -> vm.update { it.copy(wbRedGain = v) } }
                        LabeledSlider("Bleu", s.wbBlueGain, 0.5f..3f, String.format(Locale.US, "×%.2f", s.wbBlueGain)) { v -> vm.update { it.copy(wbBlueGain = v) } }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("Surface") { vm.update { it.copy(wbRedGain = 1f, wbBlueGain = 1f) } }
                            SmallButton("Eau bleue") { vm.update { it.copy(wbRedGain = 2.2f, wbBlueGain = 0.8f) } }
                            SmallButton("Eau verte") { vm.update { it.copy(wbRedGain = 1.8f, wbBlueGain = 1.25f) } }
                        }
                        Hint("Part des gains AWB au moment du passage en manuel, puis pousse le rouge (absorbé par l'eau). Affecte aperçu, JPEG et vidéo — jamais les données RAW.")
                    }
                }

                Section("VIDÉO") {
                    if (caps?.tonemapContrastCurve == true) {
                        SwitchRow("Courbe plate (flat) en plus du profil", s.flatTonemap) { vm.update { it.copy(flatTonemap = !it.flatTonemap) } }
                        Hint("S'applique à la vidéo et au JPEG (jamais au RAW).")
                    } else {
                        Hint("Ce téléphone n'expose pas de courbe de tonemap personnalisée : HLG10 est la voie « log » disponible.")
                    }
                    SwitchRow("Stabilisation", s.stabilization, enabled = caps?.stabilizationSupported != false) { vm.update { it.copy(stabilization = !it.stabilization) } }
                    SwitchRow("Enregistrer le son", s.recordAudio) {
                        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (!s.recordAudio && !has) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else vm.update { it.copy(recordAudio = !it.recordAudio) }
                    }
                }

                Section("ÉCRAN & VERROUILLAGE") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Luminosité", Modifier.weight(1f), fontSize = 16.sp)
                        ChipRow(ScreenMode.entries, s.screenMode, label = { it.label }) { m -> vm.update { it.copy(screenMode = m) } }
                    }
                    SwitchRow("Écran noir une fois verrouillé", s.blackoutWhenLocked) { vm.update { it.copy(blackoutWhenLocked = !it.blackoutWhenLocked) } }
                    SwitchRow("Épingler l'app au verrouillage", s.usePinning) { vm.update { it.copy(usePinning = !it.usePinning) } }
                    Hint("Désactivé par défaut. Le verrouillage bloque déjà le tactile et masque les barres (mode immersif). L'épinglage ajoute un blocage système de la barre de navigation et des notifications, MAIS Android impose alors un message gris à confirmer à chaque verrouillage — n'active ceci que si tu acceptes ce message.")
                    SwitchRow("Boutons volume actifs aussi hors verrouillage", s.volumeShutterWhenUnlocked) { vm.update { it.copy(volumeShutterWhenUnlocked = !it.volumeShutterWhenUnlocked) } }
                    Hint("Désactivé, le volume ne sert qu'une fois verrouillé — mais alors le verrouillage lui-même doit se faire par Vol+ long… donc laisse-le activé.")
                }

                Section("LANCEMENT DANS LA POCHETTE") {
                    val svcOn = remember(s) { launchServiceEnabled(ctx) }
                    Hint(if (svcOn) "Actif : triple appui rapide sur Volume + ouvre UWC depuis n'importe où (écran allumé)."
                         else "Active le service pour ouvrir UWC par triple appui sur Volume +, téléphone déjà dans la pochette.")
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.fillMaxWidth()) { Text(if (svcOn) "GÉRER (ACCESSIBILITÉ)" else "ACTIVER LE TRIPLE-CLIC", fontWeight = FontWeight.Bold) }
                    Hint("Ouvre Accessibilité › Applications installées › UWG — lancement triple-clic. UWC n'utilise l'accessibilité que pour détecter ce raccourci.")
                }

                OutlinedButton(onClick = onHelp, Modifier.fillMaxWidth()) { Text("AIDE DES BOUTONS", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = onDiagnostics, Modifier.fillMaxWidth()) { Text("DIAGNOSTIC CAMÉRA", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Vrai si le service d'accessibilité de lancement est activé pour cette app. */
private fun launchServiceEnabled(ctx: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return flat.split(':').any { it.substringBefore('/').equals(ctx.packageName, ignoreCase = true) }
}
