package com.uwc.camera.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwc.camera.UwcViewModel

/** Rapport des capacités réelles du téléphone, copiable pour le partager. */
@Composable
fun DiagnosticsDialog(vm: UwcViewModel, onClose: () -> Unit) {
    val caps by vm.controller.capabilities.collectAsState()
    val ctx = LocalContext.current
    val report = caps?.report() ?: "Sondage en cours…"

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Diagnostic caméra") },
        text = {
            Text(
                report,
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("UWC diagnostic", report))
                vm.setStatus("Rapport copié")
            }) { Text("COPIER") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("FERMER") } },
    )
}
