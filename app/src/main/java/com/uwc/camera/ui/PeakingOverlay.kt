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

/**
 * Dessine le masque de peaking par-dessus la preview. Le bitmap est dans l'orientation capteur ;
 * on le tourne de rotationDegrees puis on l'étire sur toute la surface (même champ grâce au ViewPort).
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
        // Après rotation de 90/270°, le bitmap doit occuper (hauteur × largeur) pour remplir l'écran.
        val (dw, dh) = if (rot % 180 == 0) size.width to size.height else size.height to size.width
        withTransform({
            rotate(rot.toFloat(), pivot = center)
            scale(dw / bw, dh / bh, pivot = center)
            translate(center.x - bw / 2f, center.y - bh / 2f)
        }) {
            drawImage(img)
        }
    }
}
