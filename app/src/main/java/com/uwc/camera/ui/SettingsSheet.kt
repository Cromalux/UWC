package com.uwc.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.uwc.camera.UwcViewModel
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.FocusMode
import com.uwc.camera.camera.PeakingColors
import com.uwc.camera.camera.ScreenMode
import com.uwc.camera.camera.UnderwaterOptics
import com.uwc.camera.camera.WhiteBalance
import java.util.Locale
import kotlin.math.roundToInt

/** Panneau latéral de réglages — uniquement accessible déverrouillé. */
@Composable
fun SettingsSheet(vm: UwcViewModel, s: CameraSettings, onClose: () -> Unit, onDiagnostics: () -> Unit) {
    val caps by vm.controller.capabilities.collectAsState()
    val ctx = LocalContext.current
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(recordAudio = granted) }
        if (!granted) vm.setStatus("Micro refusé — vidéo sans son")
    }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(470.dp), color = PanelBg) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RÉGLAGES", Modifier.weight(1f), color = Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = onClose) { Text("FERMER", color = Accent, fontWeight = FontWeight.Bold) }
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
                    SwitchRow("Épingler l'app au verrouillage (recommandé)", s.usePinning) { vm.update { it.copy(usePinning = !it.usePinning) } }
                    Hint("L'épinglage bloque la barre de navigation, le volet de notifications et l'Assistant. Android demande une confirmation la première fois, et « Épinglage d'applications » doit être activé dans Paramètres › Sécurité.")
                    SwitchRow("Volume = déclencheur aussi hors verrouillage", s.volumeShutterWhenUnlocked) { vm.update { it.copy(volumeShutterWhenUnlocked = !it.volumeShutterWhenUnlocked) } }
                }

                Section("BOUTONS UNE FOIS VERROUILLÉ") {
                    Hint("Vol+ ou Vol− court : photo / REC start-stop")
                    Hint("Vol− maintenu 1 s : bascule photo ⇄ vidéo")
                    Hint("Vol+ maintenu 1 s : écran noir on/off")
                    Hint("Vol+ et Vol− maintenus 2,5 s : déverrouiller")
                }

                OutlinedButton(onClick = onDiagnostics, Modifier.fillMaxWidth()) { Text("DIAGNOSTIC CAMÉRA", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
