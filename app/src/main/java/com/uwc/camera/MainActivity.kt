package com.uwc.camera

import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.uwc.camera.camera.CameraSettings
import com.uwc.camera.camera.ScreenMode
import com.uwc.camera.lock.SystemUi
import com.uwc.camera.lock.VolumeKeyHandler
import com.uwc.camera.ui.CameraScreen
import com.uwc.camera.ui.UwcTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object { private const val TAG = "UWC/Activity" }

    private val vm: UwcViewModel by viewModels()
    private lateinit var keys: VolumeKeyHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        keys = VolumeKeyHandler(lifecycleScope, object : VolumeKeyHandler.Callbacks {
            override fun onPhoto() = vm.takePhoto()
            override fun onVideoToggle() = vm.toggleVideo()
            override fun onCycleLens() = vm.cycleLens()
            override fun onLockProgress(progress: Float) = vm.setUnlockProgress(progress)
            override fun onLockToggle() = vm.toggleLock()
        })

        // Le geste/bouton retour ne doit rien faire une fois verrouillé.
        val backGuard = object : OnBackPressedCallback(false) { override fun handleOnBackPressed() = Unit }
        onBackPressedDispatcher.addCallback(this, backGuard)

        setContent { UwcTheme { CameraScreen(vm) } }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.locked.collect { locked ->
                    backGuard.isEnabled = locked
                    applyLock(locked)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.locked, vm.blackout, vm.settings) { l, b, s -> brightnessFor(l, b, s) }
                    .distinctUntilChanged()
                    .collect { SystemUi.setBrightness(window, it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SystemUi.immersive(window)
        display?.let { vm.controller.updateRotation(it.rotation) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SystemUi.immersive(window) else keys.reset()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        display?.let { vm.controller.updateRotation(it.rotation) }
    }

    /** Verrouillé : aucun événement tactile n'atteint la hiérarchie de vues. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        if (vm.locked.value) true else super.dispatchTouchEvent(ev)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (VolumeKeyHandler.isVolumeKey(keyCode) && volumeKeysActive()) return keys.onKeyDown(keyCode, event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (VolumeKeyHandler.isVolumeKey(keyCode) && volumeKeysActive()) return keys.onKeyUp(keyCode, event)
        return super.onKeyUp(keyCode, event)
    }

    private fun volumeKeysActive() = vm.locked.value || vm.settings.value.volumeShutterWhenUnlocked

    private fun brightnessFor(locked: Boolean, blackout: Boolean, s: CameraSettings): Float? = when {
        locked && blackout -> 0f
        s.screenMode == ScreenMode.MAX -> 1f
        else -> null
    }

    private fun applyLock(locked: Boolean) {
        SystemUi.immersive(window)
        SystemUi.setGestureExclusion(window.decorView, locked)
        val pinned = getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        if (locked) {
            if (vm.settings.value.usePinning && !pinned) {
                try {
                    startLockTask()
                } catch (e: Exception) {
                    Log.w(TAG, "startLockTask", e)
                    vm.setStatus("Épinglage indisponible — verrouillage tactile seul")
                }
            }
        } else if (pinned) {
            try { stopLockTask() } catch (e: Exception) { Log.w(TAG, "stopLockTask", e) }
        }
    }
}
