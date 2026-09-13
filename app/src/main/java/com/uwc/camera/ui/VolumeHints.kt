package com.uwc.camera.ui

import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Petits repères placés contre le bord de l'écran où se trouvent les boutons de volume
 * (bord droit du Pixel en portrait), pour rappeler que Vol+ = photo et Vol− = vidéo.
 * L'emplacement suit l'orientation de l'appareil.
 */
@Composable
fun VolumeHints() {
    val cfg = LocalConfiguration.current
    val view = LocalView.current
    // Recalcule l'orientation à chaque changement de configuration.
    val rotation = remember(cfg) { view.display?.rotation ?: Surface.ROTATION_0 }

    // Bord physique des boutons (bord droit naturel) projeté sur l'écran, + où se trouve Vol+.
    // vertical = bord gauche/droite (chips empilés) ; sinon bord haut/bas (chips côte à côte).
    val (align, vertical, plusFirst) = when (rotation) {
        Surface.ROTATION_0 -> Triple(Alignment.CenterEnd, true, true)      // bord droit, Vol+ en haut
        Surface.ROTATION_90 -> Triple(Alignment.TopCenter, false, true)    // bord haut, Vol+ à gauche
        Surface.ROTATION_180 -> Triple(Alignment.CenterStart, true, false) // bord gauche, Vol+ en bas
        else -> Triple(Alignment.BottomCenter, false, false)               // ROTATION_270 : bord bas, Vol+ à droite
    }

    val plus = @Composable { HintChip(plus = true) }
    val minus = @Composable { HintChip(plus = false) }

    Box(Modifier.fillMaxSize().padding(6.dp)) {
        Box(Modifier.align(align)) {
            if (vertical) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (plusFirst) { plus(); minus() } else { minus(); plus() }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (plusFirst) { plus(); minus() } else { minus(); plus() }
                }
            }
        }
    }
}

/** Puce repère : glyphe volume (+ / −) et icône de l'action (appareil photo / caméra). */
@Composable
private fun HintChip(plus: Boolean) {
    M3Surface(color = PanelBg, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (plus) "＋" else "−",
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(7.dp))
            if (plus) CameraGlyph(Modifier.size(16.dp)) else VideoGlyph(Modifier.size(16.dp))
        }
    }
}
