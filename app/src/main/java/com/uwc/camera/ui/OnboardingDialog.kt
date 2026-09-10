package com.uwc.camera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** « Les boutons de volume font tout » — affiché au premier lancement, puis depuis les réglages. */
@Composable
fun OnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Les boutons de volume font tout", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sous l'eau, l'écran tactile ne répond plus. Règle tout ici, puis verrouille : ensuite seuls les boutons physiques comptent.", color = Muted, fontSize = 14.sp, lineHeight = 19.sp)
                HelpRow("Vol +", "court", "Photo")
                HelpRow("Vol +", "1,5 s", "Verrouiller / déverrouiller l'écran")
                HelpRow("Vol −", "court", "Démarrer / arrêter la vidéo")
                HelpRow("Vol −", "1 s", "Objectif suivant (0,5× · 1× · 2× · 5×)")
                Text("Chaque action donne une vibration.", color = Muted, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("COMPRIS", fontWeight = FontWeight.Bold) } },
    )
}

@Composable
private fun HelpRow(key: String, press: String, action: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(key, Modifier.width(56.dp), color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(press, Modifier.width(48.dp), color = Muted, fontSize = 14.sp)
        Text(action, fontSize = 15.sp)
    }
}
