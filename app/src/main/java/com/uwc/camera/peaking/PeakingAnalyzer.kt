package com.uwc.camera.peaking

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Focus peaking par laplacien 3×3 sur le plan Y (luminance) des frames YUV_420_888.
 *
 * On travaille uniquement dans [ImageProxy.getCropRect] : avec un ViewPort partagé,
 * ce rectangle correspond exactement au champ affiché par la PreviewView, ce qui garantit
 * l'alignement de l'overlay. Le bitmap produit est double-buffered pour que l'UI puisse
 * dessiner l'un pendant qu'on écrit l'autre.
 */
class PeakingAnalyzer : ImageAnalysis.Analyzer {

    class Overlay(val bitmap: Bitmap, val rotationDegrees: Int, val version: Long)

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay

    @Volatile var threshold: Int = 40
    @Volatile var color: Int = 0xFFFF3B30.toInt()
    @Volatile var enabled: Boolean = true

    private var yBuf = ByteArray(0)
    private var out = IntArray(0)
    private var bmpA: Bitmap? = null
    private var bmpB: Bitmap? = null
    private var useA = false
    private var version = 0L

    fun clear() { _overlay.value = null }

    override fun analyze(image: ImageProxy) {
        try {
            if (!enabled) { if (_overlay.value != null) _overlay.value = null; return }

            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pxStride = plane.pixelStride
            val crop = image.cropRect
            val buf = plane.buffer
            buf.rewind()
            val need = buf.remaining()
            if (yBuf.size < need) yBuf = ByteArray(need)
            buf.get(yBuf, 0, need)

            // Sous-échantillonnage de la sortie pour viser ~640 px de large ; le noyau reste à 1 px
            // pour ne réagir qu'aux détails fins (= zone nette), pas aux gros contrastes flous.
            val step = max(1, crop.width() / 640)
            val outW = (crop.width() - 2) / step
            val outH = (crop.height() - 2) / step
            if (outW <= 0 || outH <= 0) return
            if (out.size != outW * outH) out = IntArray(outW * outH)

            val thr = threshold
            val col = color
            var oi = 0
            for (oy in 0 until outH) {
                val y = crop.top + 1 + oy * step
                val rowC = y * rowStride
                val rowU = rowC - rowStride
                val rowD = rowC + rowStride
                var x = crop.left + 1
                for (ox in 0 until outW) {
                    val xi = x * pxStride
                    val c = yBuf[rowC + xi].toInt() and 0xFF
                    val l = yBuf[rowC + xi - pxStride].toInt() and 0xFF
                    val r = yBuf[rowC + xi + pxStride].toInt() and 0xFF
                    val u = yBuf[rowU + xi].toInt() and 0xFF
                    val d = yBuf[rowD + xi].toInt() and 0xFF
                    val lap = abs(4 * c - l - r - u - d)
                    out[oi++] = if (lap > thr) col else 0
                    x += step
                }
            }

            val bmp = obtainBitmap(outW, outH)
            bmp.setPixels(out, 0, outW, 0, 0, outW, outH)
            _overlay.value = Overlay(bmp, image.imageInfo.rotationDegrees, ++version)
        } catch (_: Throwable) {
            // Une frame perdue n'a aucune importance ; on ne veut surtout pas tuer le thread d'analyse.
        } finally {
            image.close()
        }
    }

    private fun obtainBitmap(w: Int, h: Int): Bitmap {
        useA = !useA
        val cur = if (useA) bmpA else bmpB
        if (cur != null && cur.width == w && cur.height == h) return cur
        val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (useA) bmpA = nb else bmpB = nb
        return nb
    }
}
