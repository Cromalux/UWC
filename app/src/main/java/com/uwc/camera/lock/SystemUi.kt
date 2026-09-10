package com.uwc.camera.lock

import android.graphics.Rect
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout

/** Petits helpers fenêtre : immersif, luminosité, exclusion des gestes système. */
object SystemUi {

    fun immersive(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** [value] dans 0f..1f, ou null pour rendre la main au système. */
    fun setBrightness(window: Window, value: Float?) {
        val lp = window.attributes
        lp.screenBrightness = value?.coerceIn(0f, 1f) ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    /**
     * Demande au système de ne pas interpréter les swipes de bord comme des gestes de navigation.
     * Android plafonne à 200dp par bord, mais c'est déjà une bonne partie des faux positifs.
     */
    fun setGestureExclusion(view: View, enabled: Boolean) {
        view.doOnLayout {
            it.systemGestureExclusionRects =
                if (enabled) listOf(Rect(0, 0, it.width, it.height)) else emptyList()
        }
    }
}
