package com.uwc.camera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.video.Quality
import androidx.camera.video.Recorder

/**
 * Ce que le téléphone sait réellement faire, sondé au démarrage. Sert à la fois à borner
 * l'UI (griser ce qui n'existe pas) et à produire un rapport de diagnostic copiable.
 */
class CameraCapabilities(
    val cameraId: String,
    val hardwareLevel: String,
    val photoFormats: Set<Int>,
    val videoDynamicRanges: Set<DynamicRange>,
    val videoQualitiesSdr: List<Quality>,
    val videoQualitiesHlg: List<Quality>,
    val stabilizationSupported: Boolean,
    val tonemapContrastCurve: Boolean,
    val minFocusDiopters: Float,
    val zoomRange: ClosedFloatingPointRange<Float>,
    val physicalCameraIds: Set<String>,
    val rawSizes: List<Size>,
) {
    val supportsRaw get() = ImageCapture.OUTPUT_FORMAT_RAW in photoFormats
    val supportsRawJpeg get() = ImageCapture.OUTPUT_FORMAT_RAW_JPEG in photoFormats
    val supportsHlg10 get() = DynamicRange.HLG_10_BIT in videoDynamicRanges
    val hasManualFocus get() = minFocusDiopters > 0f

    fun coercePhotoFormat(f: PhotoFormat): PhotoFormat = when (f) {
        PhotoFormat.RAW_JPEG -> if (supportsRawJpeg) f else if (supportsRaw) PhotoFormat.RAW else PhotoFormat.JPEG
        PhotoFormat.RAW -> if (supportsRaw) f else PhotoFormat.JPEG
        PhotoFormat.JPEG -> f
    }

    fun report(): String = buildString {
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} — Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Caméra arrière logique : id=$cameraId, niveau=$hardwareLevel")
        appendLine("Caméras physiques : ${physicalCameraIds.ifEmpty { setOf("(non exposées)") }.joinToString()}")
        appendLine()
        appendLine("PHOTO")
        appendLine("  Formats : ${photoFormats.map(::formatLabel).joinToString()}")
        appendLine("  Tailles RAW : ${rawSizes.take(4).joinToString { "${it.width}×${it.height}" }.ifEmpty { "aucune" }}")
        appendLine()
        appendLine("VIDÉO")
        appendLine("  Plages dynamiques : ${videoDynamicRanges.map(::dynamicRangeLabel).joinToString()}")
        appendLine("  Qualités SDR : ${videoQualitiesSdr.map(::qualityLabel).joinToString()}")
        appendLine("  Qualités HLG10 : ${videoQualitiesHlg.map(::qualityLabel).joinToString().ifEmpty { "—" }}")
        appendLine("  Stabilisation : ${if (stabilizationSupported) "oui" else "non"}")
        appendLine("  Courbe de tonemap personnalisée (flat) : ${if (tonemapContrastCurve) "oui" else "non"}")
        appendLine()
        appendLine("OPTIQUE")
        appendLine("  Focus manuel : ${if (hasManualFocus) "oui (min ${UnderwaterOptics.label(minFocusDiopters)} réel, $minFocusDiopters dpt)" else "non"}")
        appendLine("  Zoom : ${"%.1f".format(zoomRange.start)}× – ${"%.1f".format(zoomRange.endInclusive)}×")
    }

    companion object {
        fun probe(context: Context, info: CameraInfo): CameraCapabilities {
            val c2 = Camera2CameraInfo.from(info)
            val id = c2.cameraId

            val level = when (c2.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "?"
            }

            val formats: Set<Int> = runCatching {
                ImageCapture.getImageCaptureCapabilities(info).supportedOutputFormats
            }.getOrDefault(setOf(ImageCapture.OUTPUT_FORMAT_JPEG))

            val vc = Recorder.getVideoCapabilities(info)
            val ranges = vc.supportedDynamicRanges
            val qSdr = runCatching { vc.getSupportedQualities(DynamicRange.SDR) }.getOrDefault(emptyList())
            val qHlg = if (DynamicRange.HLG_10_BIT in ranges)
                runCatching { vc.getSupportedQualities(DynamicRange.HLG_10_BIT) }.getOrDefault(emptyList()) else emptyList()

            val tonemapModes = c2.getCameraCharacteristic(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
            val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val zoom = info.zoomState.value?.let { it.minZoomRatio..it.maxZoomRatio } ?: (1f..1f)

            val physical: Set<String> = runCatching {
                (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(id).physicalCameraIds
            }.getOrDefault(emptySet())

            val raw = c2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() ?: emptyList()

            return CameraCapabilities(
                cameraId = id,
                hardwareLevel = level,
                photoFormats = formats,
                videoDynamicRanges = ranges,
                videoQualitiesSdr = qSdr,
                videoQualitiesHlg = qHlg,
                stabilizationSupported = vc.isStabilizationSupported,
                tonemapContrastCurve = CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE in tonemapModes,
                minFocusDiopters = minFocus,
                zoomRange = zoom,
                physicalCameraIds = physical,
                rawSizes = raw,
            )
        }

        fun qualityLabel(q: Quality): String = when (q) {
            Quality.UHD -> "4K"
            Quality.FHD -> "1080p"
            Quality.HD -> "720p"
            Quality.SD -> "480p"
            else -> q.toString()
        }

        fun formatLabel(f: Int): String = when (f) {
            ImageCapture.OUTPUT_FORMAT_JPEG -> "JPEG"
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> "JPEG UltraHDR"
            ImageCapture.OUTPUT_FORMAT_RAW -> "RAW"
            ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "RAW+JPEG"
            else -> "format#$f"
        }

        fun dynamicRangeLabel(d: DynamicRange): String = when (d) {
            DynamicRange.SDR -> "SDR"
            DynamicRange.HLG_10_BIT -> "HLG10"
            DynamicRange.HDR10_10_BIT -> "HDR10"
            DynamicRange.HDR10_PLUS_10_BIT -> "HDR10+"
            else -> d.toString()
        }
    }
}
