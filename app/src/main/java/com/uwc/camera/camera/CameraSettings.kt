package com.uwc.camera.camera

enum class CaptureMode(val label: String) { PHOTO("PHOTO"), VIDEO("VIDÉO") }

enum class PhotoFormat(val label: String) { JPEG("JPEG"), RAW("RAW"), RAW_JPEG("RAW+JPEG") }

enum class VideoProfile(val label: String) { SDR("SDR 8 bits"), HLG10("HLG10 · Log") }

enum class WhiteBalance(val label: String) { AUTO("Auto"), MANUAL("Manuel") }

/**
 * Stratégie de mise au point.
 *  - CONTINUOUS   : AF continu du système (bon en photo 1× sur sujets mobiles).
 *  - LOCK_ON_LOCK : l'AF travaille jusqu'au verrouillage, puis la distance courante est gelée.
 *  - MANUAL       : distance fixe choisie par l'utilisateur (idéal en 0,5× / vidéo).
 */
enum class FocusMode(val label: String) { CONTINUOUS("AF continu"), LOCK_ON_LOCK("Figé au verrouillage"), MANUAL("Manuel") }

enum class ScreenMode(val label: String) { MAX("Max"), SYSTEM("Système") }

/**
 * Tous les réglages utilisateur. Persistés via [com.uwc.camera.SettingsStore].
 * Deux modes exclusifs (PHOTO / VIDÉO) pour garder la pleine qualité de chacun.
 * Les champs "structurels" (mode, format, profil, peaking on/off) déclenchent un rebind
 * CameraX ; les autres sont appliqués à chaud via Camera2CameraControl.
 */
data class CameraSettings(
    val captureMode: CaptureMode = CaptureMode.PHOTO,
    val photoFormat: PhotoFormat = PhotoFormat.RAW_JPEG,
    val videoProfile: VideoProfile = VideoProfile.HLG10,
    /** Cadence vidéo cible. Le 4K LOG est limité à 30 sur ce matériel (CameraX). */
    val videoFps: Int = 30,
    val flatTonemap: Boolean = false,
    val recordAudio: Boolean = false,
    val stabilization: Boolean = true,
    /** Force une vitesse d'obturation courte (via plage FPS haute) pour tuer le flou de bougé. */
    val antiBlur: Boolean = true,

    val peakingEnabled: Boolean = true,
    val peakingThreshold: Int = 40,
    val peakingColor: Int = 0xFFFF3B30.toInt(),

    val focusMode: FocusMode = FocusMode.LOCK_ON_LOCK,
    /** Dioptries (1/m) pour le mode MANUAL. 0 = infini. Borné par LENS_INFO_MINIMUM_FOCUS_DISTANCE. */
    val focusDiopters: Float = 1.33f,

    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    /** Multiplicateurs appliqués aux gains AWB figés au passage en manuel. */
    val wbRedGain: Float = 1.0f,
    val wbBlueGain: Float = 1.0f,

    val zoomRatio: Float = 1f,

    val screenMode: ScreenMode = ScreenMode.MAX,
    val blackoutWhenLocked: Boolean = false,
    val usePinning: Boolean = false,
    val volumeShutterWhenUnlocked: Boolean = true,
    val onboardingDone: Boolean = false,
)

/** Sous-ensemble des réglages qui impose de reconstruire la session caméra. */
data class BindConfig(
    val captureMode: CaptureMode,
    val photoFormat: PhotoFormat,
    val videoProfile: VideoProfile,
    val videoFps: Int,
    val peaking: Boolean,
    val stabilization: Boolean,
) {
    companion object {
        fun from(s: CameraSettings) = BindConfig(s.captureMode, s.photoFormat, s.videoProfile, s.videoFps, s.peakingEnabled, s.stabilization)
    }
}

object PeakingColors {
    val options: List<Pair<String, Int>> = listOf(
        "Rouge" to 0xFFFF3B30.toInt(),
        "Jaune" to 0xFFFFEB3B.toInt(),
        "Vert" to 0xFF00E676.toInt(),
        "Blanc" to 0xFFFFFFFF.toInt(),
    )
}

/**
 * Conversion distance réelle ↔ dioptries à travers un hublot plat : la réfraction eau/air
 * rapproche virtuellement le sujet d'un facteur ≈ 0,75 (n_eau ≈ 1,33).
 */
object UnderwaterOptics {
    const val APPARENT_FACTOR = 0.75f
    fun dioptersForRealMeters(m: Float): Float = if (m <= 0f) 0f else 1f / (m * APPARENT_FACTOR)
    fun realMetersForDiopters(d: Float): Float? = if (d <= 0.001f) null else 1f / (d * APPARENT_FACTOR)
    fun label(d: Float): String {
        val m = realMetersForDiopters(d) ?: return "∞"
        return if (m >= 1f) String.format(java.util.Locale.FRANCE, "%.1f m", m) else "${(m * 100).toInt()} cm"
    }
    val presetsMeters = listOf("∞" to 0f, "3 m" to 3f, "1,5 m" to 1.5f, "1 m" to 1f, "50 cm" to 0.5f)
}

/**
 * La courbe plate n'a de sens que sur une image "développée" par le téléphone :
 * vidéo SDR ou photo JPEG. En HLG10 (log) et en RAW/RAW+JPEG elle est inutile ou nuisible.
 */
val CameraSettings.flatCurveApplicable: Boolean
    get() = (captureMode == CaptureMode.VIDEO && videoProfile == VideoProfile.SDR) ||
        (captureMode == CaptureMode.PHOTO && photoFormat == PhotoFormat.JPEG)
