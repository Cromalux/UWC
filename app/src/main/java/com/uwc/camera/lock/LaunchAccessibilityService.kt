package com.uwc.camera.lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.uwc.camera.MainActivity

/**
 * Lance UWC sur un triple appui rapide de Vol+ , depuis n'importe où (écran allumé requis).
 * Permet d'ouvrir l'app une fois le téléphone déjà dans la pochette, sans toucher l'écran.
 *
 * Le service se contente d'observer les touches (il les laisse passer, `onKeyEvent` renvoie false),
 * donc le volume continue de fonctionner normalement partout ailleurs.
 */
class LaunchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UWC/Launch"
        private const val WINDOW_MS = 900L
        private const val NEEDED = 3
    }

    private val presses = ArrayDeque<Long>()

    override fun onServiceConnected() {
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = System.currentTimeMillis()
            presses.addLast(now)
            while (presses.isNotEmpty() && now - presses.first() > WINDOW_MS) presses.removeFirst()
            if (presses.size >= NEEDED) {
                presses.clear()
                launchApp()
            }
        }
        return false // ne jamais consommer : le volume doit rester fonctionnel
    }

    private fun launchApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Lancement refusé", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
