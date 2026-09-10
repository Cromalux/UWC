package com.uwc.camera.lock

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Boutons de volume — les seuls contrôles fiables sous l'eau.
 *
 *  - Vol+ court                 → photo
 *  - Vol+ maintenu [LOCK_MS]    → verrouiller / déverrouiller (barre de progression pendant l'appui)
 *  - Vol− court                 → démarrer / arrêter la vidéo
 *  - Vol− maintenu [LONG_MS]    → bascule PHOTO ⇄ VIDÉO
 *
 * Toutes les touches volume sont consommées (pas de HUD volume système).
 */
class VolumeKeyHandler(
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onPhoto()
        fun onVideoToggle()
        fun onSwitchMode()
        /** 0f..1f pendant l'appui long sur Vol+ ; 0f quand il est relâché avant terme. */
        fun onLockProgress(progress: Float)
        fun onLockToggle()
    }

    companion object {
        const val LOCK_MS = 1500L
        const val LONG_MS = 1000L
        private const val TICK_MS = 40L

        fun isVolumeKey(keyCode: Int) =
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    }

    private var upJob: Job? = null
    private var downJob: Job? = null
    private var upFired = false
    private var downFired = false

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode)) return false
        if (event.repeatCount > 0) return true
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            upFired = false
            upJob?.cancel()
            upJob = scope.launch {
                val start = System.currentTimeMillis()
                while (true) {
                    val p = ((System.currentTimeMillis() - start).toFloat() / LOCK_MS).coerceIn(0f, 1f)
                    callbacks.onLockProgress(p)
                    if (p >= 1f) break
                    delay(TICK_MS)
                }
                upFired = true
                upJob = null
                callbacks.onLockProgress(0f)
                callbacks.onLockToggle()
            }
        } else {
            downFired = false
            downJob?.cancel()
            downJob = scope.launch {
                delay(LONG_MS)
                downFired = true
                downJob = null
                callbacks.onSwitchMode()
            }
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode)) return false
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val wasRunning = upJob != null
            upJob?.cancel(); upJob = null
            if (wasRunning) callbacks.onLockProgress(0f)
            if (!upFired) callbacks.onPhoto()
            upFired = false
        } else {
            downJob?.cancel(); downJob = null
            if (!downFired) callbacks.onVideoToggle()
            downFired = false
        }
        return true
    }

    /** À appeler si l'activité perd le focus : évite un état "touche enfoncée" fantôme. */
    fun reset() {
        upJob?.cancel(); upJob = null
        downJob?.cancel(); downJob = null
        upFired = false; downFired = false
        callbacks.onLockProgress(0f)
    }
}
