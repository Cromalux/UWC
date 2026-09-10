package com.uwc.camera.lock

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Machine à états pour les boutons de volume — les seuls contrôles fiables sous l'eau.
 *
 *  - Vol+ ou Vol− (appui court)      → déclencheur (photo / start-stop vidéo)
 *  - Vol− maintenu [LONG_MS]          → bascule photo ↔ vidéo
 *  - Vol+ maintenu [LONG_MS]          → écran noir on/off
 *  - Vol+ ET Vol− maintenus [COMBO_MS]→ verrouiller / déverrouiller
 *
 * Toutes les touches volume sont consommées (pas de HUD volume système).
 */
class VolumeKeyHandler(
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onShutter()
        fun onLongVolumeUp()
        fun onLongVolumeDown()
        /** 0f..1f pendant le combo ; 0f quand il est annulé. */
        fun onComboProgress(progress: Float)
        fun onComboComplete()
    }

    companion object {
        const val COMBO_MS = 2500L
        const val LONG_MS = 1000L
        private const val TICK_MS = 40L

        fun isVolumeKey(keyCode: Int) =
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
    }

    private var upPressed = false
    private var downPressed = false
    private var comboJob: Job? = null
    private var longJob: Job? = null
    private var comboFired = false
    private var longFired = false
    /** Une fois qu'un combo a été engagé, le relâchement des touches ne déclenche rien. */
    private var suppressShutter = false

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode)) return false
        if (event.repeatCount > 0) return true

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) upPressed = true else downPressed = true
        cancelLong()

        if (upPressed && downPressed) {
            suppressShutter = true
            startCombo()
        } else {
            startLong(keyCode)
        }
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isVolumeKey(keyCode)) return false

        val comboWasRunning = comboJob != null
        cancelCombo()
        cancelLong()

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) upPressed = false else downPressed = false

        if (comboWasRunning && !comboFired) callbacks.onComboProgress(0f)

        if (!upPressed && !downPressed) {
            if (!suppressShutter && !longFired && !comboFired) callbacks.onShutter()
            suppressShutter = false
            longFired = false
            comboFired = false
        }
        return true
    }

    /** À appeler si l'activité perd le focus : évite un état "touche enfoncée" fantôme. */
    fun reset() {
        cancelCombo(); cancelLong()
        upPressed = false; downPressed = false
        suppressShutter = false; longFired = false; comboFired = false
        callbacks.onComboProgress(0f)
    }

    private fun startCombo() {
        comboJob?.cancel()
        comboJob = scope.launch {
            val start = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - start).toFloat() / COMBO_MS).coerceIn(0f, 1f)
                callbacks.onComboProgress(p)
                if (p >= 1f) break
                delay(TICK_MS)
            }
            comboFired = true
            comboJob = null
            callbacks.onComboComplete()
        }
    }

    private fun startLong(keyCode: Int) {
        longJob?.cancel()
        longJob = scope.launch {
            delay(LONG_MS)
            longFired = true
            longJob = null
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) callbacks.onLongVolumeUp() else callbacks.onLongVolumeDown()
        }
    }

    private fun cancelCombo() { comboJob?.cancel(); comboJob = null }
    private fun cancelLong() { longJob?.cancel(); longJob = null }
}
