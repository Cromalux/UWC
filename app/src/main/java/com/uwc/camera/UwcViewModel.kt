package com.uwc.camera

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.uwc.camera.camera.CameraController
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.CaptureMode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'app : réglages persistés, verrouillage (volatile), compteurs et messages. */
@OptIn(FlowPreview::class)
class UwcViewModel(app: Application) : AndroidViewModel(app) {

    data class Status(val text: String, val id: Long)

    val controller = CameraController(app)
    private val haptics = Haptics(app)
    private val store = SettingsStore(app)

    private val _settings = MutableStateFlow(CameraSettings())
    val settings: StateFlow<CameraSettings> = _settings
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked
    private val _blackout = MutableStateFlow(false)
    val blackout: StateFlow<Boolean> = _blackout
    private val _unlockProgress = MutableStateFlow(0f)
    val unlockProgress: StateFlow<Float> = _unlockProgress
    private val _shotCount = MutableStateFlow(0)
    val shotCount: StateFlow<Int> = _shotCount
    private val _flashAt = MutableStateFlow(0L)
    val flashAt: StateFlow<Long> = _flashAt
    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status

    init {
        controller.onStatus = ::setStatus

        viewModelScope.launch {
            _settings.value = store.load()
            try {
                controller.initialize()
                _ready.value = true
            } catch (e: Exception) {
                setStatus("Caméra inaccessible : ${e.message}")
            }
        }
        viewModelScope.launch {
            settings.drop(1).debounce(400).collectLatest { store.save(it) }
        }
        viewModelScope.launch {
            combine(settings, locked) { s, l -> s to l }.collect { (s, l) ->
                controller.peaking.threshold = s.peakingThreshold
                controller.peaking.color = s.peakingColor
                controller.applyControls(s, l)
            }
        }
    }

    fun update(transform: (CameraSettings) -> CameraSettings) = _settings.update(transform)

    fun takePhoto() {
        if (!_ready.value) return
        if (settings.value.captureMode != CaptureMode.PHOTO) { toggleMode(); return }
        if (controller.takePhoto()) {
            _shotCount.update { it + 1 }
            _flashAt.value = SystemClock.uptimeMillis()
            haptics.shutter()
        }
    }

    fun toggleVideo() {
        if (!_ready.value) return
        if (settings.value.captureMode != CaptureMode.VIDEO) { toggleMode(); return }
        controller.toggleRecording(settings.value.recordAudio)
        haptics.recordToggle()
    }

    fun toggleMode() {
        if (controller.isRecording.value) { setStatus("Enregistrement en cours"); haptics.error(); return }
        val next = if (settings.value.captureMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
        update { it.copy(captureMode = next) }
        haptics.modeToggle()
        setStatus(if (next == CaptureMode.PHOTO) "Mode PHOTO" else "Mode VIDÉO")
    }

    /** Objectif suivant parmi les crans "physiques" (0,5× / 1× / 2× / 5×) disponibles sur ce téléphone. */
    fun cycleLens() {
        val range = controller.zoomRange.value
        val stops = listOf(0.5f, 1f, 2f, 5f).filter { it >= range.start - 0.01f && it <= range.endInclusive + 0.01f }
            .ifEmpty { listOf(1f) }
        val cur = settings.value.zoomRatio
        val idx = stops.indexOfFirst { kotlin.math.abs(it - cur) < 0.05f }
        val next = stops[(idx + 1) % stops.size]
        update { it.copy(zoomRatio = next) }
        haptics.modeToggle()
        setStatus("Objectif ${if (next == next.toInt().toFloat()) "${next.toInt()}×" else "${next}×"}")
    }

    fun toggleLock() {
        val now = !_locked.value
        _locked.value = now
        _unlockProgress.value = 0f
        _blackout.value = now && settings.value.blackoutWhenLocked
        haptics.lockToggle()
        setStatus(if (now) "Verrouillé — Vol+ maintenu 1,5 s pour déverrouiller" else "Déverrouillé")
    }

    fun setUnlockProgress(p: Float) { _unlockProgress.value = p }

    fun setStatus(text: String) { _status.value = Status(text, SystemClock.uptimeMillis()) }

    fun clearStatus(id: Long) { if (_status.value?.id == id) _status.value = null }

    override fun onCleared() { controller.stopRecordingIfAny() }
}
