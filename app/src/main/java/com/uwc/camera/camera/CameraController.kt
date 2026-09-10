package com.uwc.camera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.uwc.camera.peaking.PeakingAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow

/**
 * Façade CameraX. Une seule caméra (arrière), photo et vidéo armées en permanence :
 * Preview + ImageCapture (RAW/JPEG) + VideoCapture (SDR/HLG10) + ImageAnalysis optionnel (peaking).
 */
class CameraController(private val context: Context) {

    companion object {
        private const val TAG = "UWC/Camera"
        private const val CAPTURE_WATCHDOG_MS = 6000L

        /** Courbe "flat" : noirs relevés, blancs compressés, gamma doux — à étalonner en post. */
        private val FLAT_CURVE: TonemapCurve by lazy {
            val n = 32
            val pts = FloatArray(n * 2)
            val lift = 0.06f
            val top = 0.94f
            for (i in 0 until n) {
                val x = i / (n - 1f)
                val y = lift + (top - lift) * x.toDouble().pow(1 / 2.2).toFloat()
                pts[2 * i] = x
                pts[2 * i + 1] = y.coerceIn(0f, 1f)
            }
            TonemapCurve(pts, pts, pts)
        }
    }

    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var analysis: ImageAnalysis? = null
    private var recording: Recording? = null
    private var activePhotoFormat = PhotoFormat.JPEG
    private var caps: CameraCapabilities? = null
    private var lastControls: Pair<CameraSettings, Boolean>? = null
    private var busy = false

    val peaking = PeakingAnalyzer()
    /** Messages courts destinés à l'utilisateur (toast maison). */
    var onStatus: (String) -> Unit = {}

    private val _capabilities = MutableStateFlow<CameraCapabilities?>(null)
    val capabilities: StateFlow<CameraCapabilities?> = _capabilities
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _recordingMs = MutableStateFlow(0L)
    val recordingMs: StateFlow<Long> = _recordingMs
    private val _lastCapture = MutableStateFlow<Uri?>(null)
    val lastCapture: StateFlow<Uri?> = _lastCapture
    private val _zoomRange = MutableStateFlow(1f..1f)
    val zoomRange: StateFlow<ClosedFloatingPointRange<Float>> = _zoomRange
    private val _activeInfo = MutableStateFlow("")
    val activeInfo: StateFlow<String> = _activeInfo
    private val _peakingActive = MutableStateFlow(false)
    val peakingActive: StateFlow<Boolean> = _peakingActive
    /** Distance de mise au point courante rapportée par le HAL, en dioptries. */
    private val _focusDiopters = MutableStateFlow(0f)
    val focusDiopters: StateFlow<Float> = _focusDiopters

    // Mémoire de l'AWB : on relit gains + matrice tant que l'auto tourne, pour les figer en manuel.
    @Volatile private var awbFrozen = false
    @Volatile private var lastAwbGains: RggbChannelVector? = null
    @Volatile private var lastAwbTransform: ColorSpaceTransform? = null
    @Volatile private var lastAfDiopters = 0f

    private val sessionCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (!awbFrozen) {
                result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let { lastAwbGains = it }
                result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.let { lastAwbTransform = it }
            }
            result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { d ->
                _focusDiopters.value = d
                if (request.get(CaptureRequest.CONTROL_AF_MODE) != CameraMetadata.CONTROL_AF_MODE_OFF) lastAfDiopters = d
            }
        }
    }

    suspend fun initialize() {
        val p = awaitProvider()
        provider = p
        val info = p.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)
        val c = CameraCapabilities.probe(context, info)
        caps = c
        _capabilities.value = c
        _zoomRange.value = c.zoomRange
        Log.i(TAG, c.report())
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
        }, mainExecutor)
    }

    /**
     * (Re)construit la session : Preview + ImageCapture + VideoCapture (+ ImageAnalysis pour le peaking),
     * tous armés en même temps. Si le HAL refuse une combinaison, on dégrade dans cet ordre :
     * sans peaking → vidéo SDR → photo JPEG, en prévenant l'utilisateur à chaque cran.
     * À appeler depuis le thread principal, PreviewView déjà mesurée.
     */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, settings: CameraSettings) {
        val p = provider ?: return
        val c = caps ?: return
        if (recording != null) {
            onStatus("Arrête l'enregistrement avant de changer la configuration")
            return
        }
        p.unbindAll()
        peaking.clear()
        imageCapture = null; videoCapture = null; analysis = null

        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val previewBuilder = Preview.Builder().setTargetRotation(rotation)
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(sessionCallback)
        val preview = previewBuilder.build()
        preview.surfaceProvider = previewView.surfaceProvider

        fun photoUseCase(fmt: PhotoFormat): ImageCapture {
            val outFmt = when (fmt) {
                PhotoFormat.JPEG -> ImageCapture.OUTPUT_FORMAT_JPEG
                PhotoFormat.RAW -> ImageCapture.OUTPUT_FORMAT_RAW
                PhotoFormat.RAW_JPEG -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            }
            return ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setOutputFormat(outFmt)
                // Le RAW n'a rien à gagner au post-traitement "qualité" ; on privilégie la latence (sujets mobiles).
                .setCaptureMode(if (fmt == PhotoFormat.JPEG) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        }

        fun videoUseCase(hlg: Boolean): Pair<VideoCapture<Recorder>, String> {
            val qualities = if (hlg) c.videoQualitiesHlg else c.videoQualitiesSdr
            val preferred = listOf(Quality.UHD, Quality.FHD, Quality.HD).firstOrNull { it in qualities }
            val selector = if (preferred != null)
                QualitySelector.from(preferred, FallbackStrategy.lowerQualityOrHigherThan(preferred))
            else QualitySelector.from(Quality.HIGHEST)
            val recorder = Recorder.Builder().setQualitySelector(selector).build()
            val b = VideoCapture.Builder(recorder)
                .setTargetRotation(rotation)
                .setDynamicRange(if (hlg) DynamicRange.HLG_10_BIT else DynamicRange.SDR)
            if (settings.stabilization && c.stabilizationSupported) b.setVideoStabilizationEnabled(true)
            val label = "${if (hlg) "HLG10" else "SDR"} ${preferred?.let(CameraCapabilities::qualityLabel) ?: ""}".trim()
            return b.build() to label
        }

        fun analysisUseCase(): ImageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .build()
            .also { it.setAnalyzer(analysisExecutor, peaking) }

        fun tryBind(vararg cases: UseCase): Boolean {
            val g = UseCaseGroup.Builder()
            cases.forEach { g.addUseCase(it) }
            previewView.viewPort?.let { g.setViewPort(it) }
            return try {
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, g.build())
                true
            } catch (e: Exception) {
                Log.w(TAG, "bind ${cases.joinToString { it.javaClass.simpleName }} a échoué : ${e.message}")
                p.unbindAll()
                false
            }
        }

        val wantedFmt = c.coercePhotoFormat(settings.photoFormat)
        val wantedHlg = settings.videoProfile == VideoProfile.HLG10 && c.supportsHlg10
        val wantPeaking = settings.peakingEnabled

        // Ordre de dégradation : chaque étape retire une exigence.
        data class Attempt(val fmt: PhotoFormat, val hlg: Boolean, val peaking: Boolean)
        val attempts = buildList {
            add(Attempt(wantedFmt, wantedHlg, wantPeaking))
            if (wantPeaking) add(Attempt(wantedFmt, wantedHlg, false))
            if (wantedHlg) { add(Attempt(wantedFmt, false, wantPeaking)); if (wantPeaking) add(Attempt(wantedFmt, false, false)) }
            if (wantedFmt != PhotoFormat.JPEG) { add(Attempt(PhotoFormat.JPEG, wantedHlg, false)); add(Attempt(PhotoFormat.JPEG, false, false)) }
        }.distinct()

        var bound: Attempt? = null
        var videoLabel = ""
        for (a in attempts) {
            val ic = photoUseCase(a.fmt)
            val (vc, label) = videoUseCase(a.hlg)
            val an = if (a.peaking) analysisUseCase() else null
            val ok = if (an != null) tryBind(preview, ic, vc, an) else tryBind(preview, ic, vc)
            if (ok) {
                imageCapture = ic; videoCapture = vc; analysis = an
                activePhotoFormat = a.fmt; videoLabel = label; bound = a
                break
            }
        }
        if (bound == null) {
            onStatus("Caméra indisponible : aucune configuration acceptée")
            _activeInfo.value = ""
            _peakingActive.value = false
            return
        }

        _peakingActive.value = bound.peaking
        _activeInfo.value = "${bound.fmt.label} · $videoLabel"
        when {
            bound.fmt != wantedFmt -> onStatus("RAW indisponible avec la vidéo simultanée → JPEG")
            bound.hlg != wantedHlg -> onStatus("HLG10 indisponible avec la photo simultanée → SDR")
            bound.peaking != wantPeaking -> onStatus("Focus peaking indisponible avec cette configuration")
        }
        Log.i(TAG, "Session : $bound")

        camera?.cameraInfo?.zoomState?.value?.let { _zoomRange.value = it.minZoomRatio..it.maxZoomRatio }
        lastControls?.let { (s, locked) -> applyControls(s, locked) }
            ?: camera?.cameraControl?.setZoomRatio(settings.zoomRatio.coerceIn(_zoomRange.value))
    }

    /** Réglages "à chaud" : focus, balance des blancs, courbe, zoom. Sans rebind. */
    fun applyControls(s: CameraSettings, locked: Boolean) {
        lastControls = s to locked
        val cam = camera ?: return
        val c = caps ?: return
        val b = CaptureRequestOptions.Builder()

        val fixedDiopters: Float? = when (s.focusMode) {
            FocusMode.CONTINUOUS -> null
            FocusMode.MANUAL -> s.focusDiopters
            FocusMode.LOCK_ON_LOCK -> if (locked) lastAfDiopters else null
        }
        if (fixedDiopters != null && c.hasManualFocus) {
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, fixedDiopters.coerceIn(0f, c.minFocusDiopters))
        }

        val manualWb = s.whiteBalance == WhiteBalance.MANUAL
        if (manualWb) {
            // Point de départ = derniers gains calculés par l'AWB ; on ne fait que pousser le rouge / le bleu.
            val base = lastAwbGains ?: RggbChannelVector(2.0f, 1f, 1f, 1.8f)
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            b.setCaptureRequestOption(
                CaptureRequest.COLOR_CORRECTION_GAINS,
                RggbChannelVector(base.red * s.wbRedGain, base.greenEven, base.greenOdd, base.blue * s.wbBlueGain),
            )
            lastAwbTransform?.let { b.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_TRANSFORM, it) }
        }
        awbFrozen = manualWb

        if (s.flatTonemap && c.tonemapContrastCurve) {
            b.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
            b.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, FLAT_CURVE)
        }

        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
        cam.cameraControl.setZoomRatio(s.zoomRatio.coerceIn(_zoomRange.value))
    }

    fun updateRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
        analysis?.targetRotation = rotation
    }

    /** @return true si une capture a été lancée. */
    fun takePhoto(): Boolean {
        val ic = imageCapture ?: return false
        if (busy) return false
        busy = true
        mainHandler.postDelayed({ busy = false }, CAPTURE_WATCHDOG_MS)

        val resolver = context.contentResolver
        val base = MediaOutput.baseName()
        val cb = object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                busy = false
                r.savedUri?.let { _lastCapture.value = it }
            }
            override fun onError(e: ImageCaptureException) {
                busy = false
                Log.e(TAG, "Erreur capture", e)
                onStatus("Erreur photo : ${e.message ?: "code ${e.imageCaptureError}"}")
            }
        }
        when (activePhotoFormat) {
            PhotoFormat.RAW_JPEG -> ic.takePicture(MediaOutput.dng(resolver, base), MediaOutput.jpeg(resolver, base), mainExecutor, cb)
            PhotoFormat.RAW -> ic.takePicture(MediaOutput.dng(resolver, base), mainExecutor, cb)
            PhotoFormat.JPEG -> ic.takePicture(MediaOutput.jpeg(resolver, base), mainExecutor, cb)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun toggleRecording(withAudio: Boolean) {
        val vc = videoCapture ?: run { onStatus("Vidéo non disponible"); return }
        recording?.let { it.stop(); return }

        val opts = MediaOutput.video(context.contentResolver, MediaOutput.baseName())
        val pending = vc.output.prepareRecording(context, opts)
        val audioOk = withAudio &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioOk) pending.withAudioEnabled()

        recording = pending.start(mainExecutor) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> { _isRecording.value = true; _recordingMs.value = 0 }
                is VideoRecordEvent.Status -> _recordingMs.value = ev.recordingStats.recordedDurationNanos / 1_000_000
                is VideoRecordEvent.Finalize -> {
                    _isRecording.value = false
                    recording = null
                    if (ev.hasError()) {
                        Log.e(TAG, "Erreur vidéo ${ev.error}", ev.cause)
                        onStatus("Erreur vidéo (code ${ev.error})")
                    } else {
                        _lastCapture.value = ev.outputResults.outputUri
                    }
                }
                else -> Unit
            }
        }
    }

    fun stopRecordingIfAny() { recording?.stop() }
}
