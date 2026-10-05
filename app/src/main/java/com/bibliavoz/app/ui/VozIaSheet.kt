package com.bibliavoz.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.voz.VocesIa
import java.util.Locale

/**
 * La voz IA: qué audio grabado hay en el teléfono y el interruptor para usarlo.
 * Todo funciona sin internet; el audio se genera una vez en la PC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VozIaSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val estado by viewModel.vozIa.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.refrescarVozIa() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Voz IA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${estado.voz}: una voz humana, con emociones, que lee la Biblia como un " +
                    "audiolibro. Está grabada en el teléfono, así que no necesita internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                color = if (estado.hayAudio) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = if (estado.enApk) "Incluida en la app" else "Instalado en este teléfono",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    when {
                        !estado.revisado -> Text("Revisando…", style = MaterialTheme.typography.bodyMedium)
                        estado.hayAudio -> {
                            val total = viewModel.totalCapitulos
                            Text(
                                text = "${estado.capitulos}" + (if (total > 0) " de $total" else "") +
                                    " capítulos de la Biblia",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${estado.lecturas} lecturas de la misa",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (estado.bytes > 0) {
                                Text(
                                    text = "Ocupa ${tamano(estado.bytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> Text(
                            text = "Esta copia de la app no trae el audio de la voz. Instala el " +
                                "APK completo de Biblia en Voz para poder escucharla.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // Solo cuenta lo que tiene todos sus archivos: si el manifiesto
                    // nombra más, el APK o la copia por cable quedó a medias.
                    if (estado.revisado && estado.faltanArchivos) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Faltan archivos de audio; reinstala el APK completo de Biblia en Voz.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Leída como novela", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "La voz pone emoción donde el texto la pide: triste si alguien llora, gritando si " +
                    "clama a gran voz, en susurro si habla en secreto, con eco la voz del cielo y solemne " +
                    "cuando habla Dios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                text = "Built with Fish Audio. Voz generada con ${VocesIa.MODELO_NOMBRE}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun tamano(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024)
        mb >= 10 -> "${mb.toInt()} MB"
        else -> String.format(Locale.US, "%.1f MB", mb)
    }
}
