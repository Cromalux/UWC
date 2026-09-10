package com.uwc.camera

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Retour haptique : sous l'eau on ne voit pas toujours l'écran, on sent le téléphone. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator

    fun shutter() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    fun recordToggle() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    fun lockToggle() = play(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), -1))
    fun modeToggle() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
    fun error() = play(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))

    private fun play(effect: VibrationEffect) {
        try { vibrator?.vibrate(effect) } catch (_: Throwable) {}
    }
}
