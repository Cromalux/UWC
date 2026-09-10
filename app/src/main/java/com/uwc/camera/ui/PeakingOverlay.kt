package com.uwc.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import com.uwc.camera.peaking.PeakingAnalyzer
import kotlin.math.min

/**
 * Dessine le masque de peaking par-dessus la preview. Le bitmap est dans l'orientation capteur ;
 * on le tourne de rotationDegrees puis on l'inscrit en FIT (contain) dans la surface — même cadrage
 * que la PreviewView en FIT_CENTER, aperçu et masque partageant le même champ (même aspect, pas de ViewPort).
 */
@Composable
fun PeakingOverlay(analyzer: PeakingAnalyzer, modifier: Modifier = Modifier) {
    val overlay by analyzer.overlay.collectAsState()
    val ov = overlay ?: return
    val img = remember(ov.version) { ov.bitmap.asImageBitmap() }

    Canvas(modifier.fillMaxSize()) {
        val rot = ov.rotationDegrees
        val bw = img.width.toFloat()
        val bh = img.height.toFloat()
        // Dimensions du bitmap une fois tourné, à inscrire dans la surface sans le déformer.
        val (rw, rh) = if (rot % 180 == 0) bw to bh else bh to bw
        val s = min(size.width / rw, size.height / rh)
        withTransform({
            rotate(rot.toFloat(), pivot = center)
            scale(s, s, pivot = center)
            translate(center.x - bw / 2f, center.y - bh / 2f)
        }) {
            drawImage(img)
        }
    }
}
