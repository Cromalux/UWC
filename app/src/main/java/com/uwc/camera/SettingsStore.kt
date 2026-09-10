package com.uwc.camera

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.FocusMode
import com.uwc.camera.camera.PhotoFormat
import com.uwc.camera.camera.ScreenMode
import com.uwc.camera.camera.VideoProfile
import com.uwc.camera.camera.WhiteBalance
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "uwc_settings")

/** Persistance des réglages. L'état "verrouillé" n'est volontairement jamais persisté. */
class SettingsStore(private val context: Context) {

    private object K {
        val photoFormat = stringPreferencesKey("photoFormat")
        val videoProfile = stringPreferencesKey("videoProfile")
        val flatTonemap = booleanPreferencesKey("flatTonemap")
        val recordAudio = booleanPreferencesKey("recordAudio")
        val stabilization = booleanPreferencesKey("stabilization")
        val peakingEnabled = booleanPreferencesKey("peakingEnabled")
        val peakingThreshold = intPreferencesKey("peakingThreshold")
        val peakingColor = intPreferencesKey("peakingColor")
        val focusMode = stringPreferencesKey("focusMode")
        val focusDiopters = floatPreferencesKey("focusDiopters")
        val whiteBalance = stringPreferencesKey("whiteBalance")
        val wbRedGain = floatPreferencesKey("wbRedGain")
        val wbBlueGain = floatPreferencesKey("wbBlueGain")
        val zoomRatio = floatPreferencesKey("zoomRatio")
        val screenMode = stringPreferencesKey("screenMode")
        val blackoutWhenLocked = booleanPreferencesKey("blackoutWhenLocked")
        val usePinning = booleanPreferencesKey("usePinning")
        val volumeShutterWhenUnlocked = booleanPreferencesKey("volumeShutterWhenUnlocked")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
    }

    suspend fun load(): CameraSettings {
        val p = context.dataStore.data.first()
        val d = CameraSettings()
        return CameraSettings(
            photoFormat = enumOr(p[K.photoFormat], d.photoFormat),
            videoProfile = enumOr(p[K.videoProfile], d.videoProfile),
            flatTonemap = p[K.flatTonemap] ?: d.flatTonemap,
            recordAudio = p[K.recordAudio] ?: d.recordAudio,
            stabilization = p[K.stabilization] ?: d.stabilization,
            peakingEnabled = p[K.peakingEnabled] ?: d.peakingEnabled,
            peakingThreshold = p[K.peakingThreshold] ?: d.peakingThreshold,
            peakingColor = p[K.peakingColor] ?: d.peakingColor,
            focusMode = enumOr(p[K.focusMode], d.focusMode),
            focusDiopters = p[K.focusDiopters] ?: d.focusDiopters,
            whiteBalance = enumOr(p[K.whiteBalance], d.whiteBalance),
            wbRedGain = p[K.wbRedGain] ?: d.wbRedGain,
            wbBlueGain = p[K.wbBlueGain] ?: d.wbBlueGain,
            zoomRatio = p[K.zoomRatio] ?: d.zoomRatio,
            screenMode = enumOr(p[K.screenMode], d.screenMode),
            blackoutWhenLocked = p[K.blackoutWhenLocked] ?: d.blackoutWhenLocked,
            usePinning = p[K.usePinning] ?: d.usePinning,
            volumeShutterWhenUnlocked = p[K.volumeShutterWhenUnlocked] ?: d.volumeShutterWhenUnlocked,
            onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
        )
    }

    suspend fun save(s: CameraSettings) {
        context.dataStore.edit { p ->
            p[K.photoFormat] = s.photoFormat.name
            p[K.videoProfile] = s.videoProfile.name
            p[K.flatTonemap] = s.flatTonemap
            p[K.recordAudio] = s.recordAudio
            p[K.stabilization] = s.stabilization
            p[K.peakingEnabled] = s.peakingEnabled
            p[K.peakingThreshold] = s.peakingThreshold
            p[K.peakingColor] = s.peakingColor
            p[K.focusMode] = s.focusMode.name
            p[K.focusDiopters] = s.focusDiopters
            p[K.whiteBalance] = s.whiteBalance.name
            p[K.wbRedGain] = s.wbRedGain
            p[K.wbBlueGain] = s.wbBlueGain
            p[K.zoomRatio] = s.zoomRatio
            p[K.screenMode] = s.screenMode.name
            p[K.blackoutWhenLocked] = s.blackoutWhenLocked
            p[K.usePinning] = s.usePinning
            p[K.volumeShutterWhenUnlocked] = s.volumeShutterWhenUnlocked
            p[K.onboardingDone] = s.onboardingDone
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
