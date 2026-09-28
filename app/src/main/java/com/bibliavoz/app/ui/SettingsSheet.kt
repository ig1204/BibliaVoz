package com.bibliavoz.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.data.Prefs
import com.bibliavoz.app.player.PlaybackService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onOpenVoicePicker: () -> Unit,
    onOpenVozIa: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vozIa by viewModel.vozIa.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            SectionTitle("Voz")

            EntryCard(
                title = "Voz IA · ${vozIa.voz}",
                subtitle = when {
                    !vozIa.activa -> "Desactivada: lee la voz del teléfono"
                    !vozIa.revisado -> "Voz humana con emociones, sin internet"
                    !vozIa.hayAudio -> "Voz humana con emociones. Aún no hay audio instalado"
                    else -> "${vozIa.capitulos} capítulos y ${vozIa.lecturas} lecturas, sin internet"
                },
                icon = Icons.Rounded.AutoAwesome,
                highlighted = true,
                onClick = onOpenVozIa,
            )

            EntryCard(
                title = "Voz del teléfono",
                subtitle = if (vozIa.activa && vozIa.hayAudio) {
                    "Para lo que no tiene voz IA grabada"
                } else {
                    "Escucha las voces del teléfono y quédate con la que prefieras"
                },
                icon = Icons.Rounded.RecordVoiceOver,
                highlighted = false,
                onClick = onOpenVoicePicker,
            )

            SliderSetting(
                label = "Velocidad",
                value = settings.speechRate,
                valueLabel = String.format("%.2fx", settings.speechRate),
                range = Prefs.MIN_RATE..Prefs.MAX_RATE,
                steps = 15,
                onValueChange = { viewModel.onSpeechRateChanged(it) },
                onValueChangeFinished = { PlaybackService.setSpeechRate(context, settings.speechRate) },
            )

            SliderSetting(
                label = "Tono de voz",
                value = settings.pitch,
                valueLabel = String.format("%.2f", settings.pitch),
                range = Prefs.MIN_PITCH..Prefs.MAX_PITCH,
                steps = 12,
                onValueChange = { viewModel.onPitchChanged(it) },
                onValueChangeFinished = { PlaybackService.setPitch(context, settings.pitch) },
            )

            SwitchSetting(
                title = "Anunciar el capítulo",
                subtitle = "Dice «Génesis, capítulo 1» al empezar; la voz IA lo dice siempre",
                checked = settings.announceChapter,
                onCheckedChange = viewModel::setAnnounceChapter,
            )

            SwitchSetting(
                title = "Leer el número de versículo",
                subtitle = "Dice «Versículo 3» antes de cada uno; solo la voz del teléfono",
                checked = settings.announceVerseNumbers,
                onCheckedChange = viewModel::setAnnounceVerseNumbers,
            )

            SwitchSetting(
                title = "Continuar solo",
                subtitle = "Al terminar un capítulo sigue con el siguiente",
                checked = settings.autoContinue,
                onCheckedChange = viewModel::setAutoContinue,
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            SectionTitle("Lectura")

            SliderSetting(
                label = "Tamaño de letra",
                value = settings.fontScale,
                valueLabel = "${(settings.fontScale * 100).toInt()}%",
                range = 0.8f..2.0f,
                steps = 11,
                onValueChange = viewModel::setFontScale,
                onValueChangeFinished = {},
            )

            SwitchSetting(
                title = "Mantener la pantalla encendida",
                subtitle = "Útil para leer siguiendo la voz",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::setKeepScreenOn,
            )

            Spacer(Modifier.height(8.dp))
            Text("Tema", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sistema", "Claro", "Oscuro").forEachIndexed { index, label ->
                    FilterChip(
                        selected = settings.themeMode == index,
                        onClick = { viewModel.setThemeMode(index) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(
                text = "Texto: ${viewModel.translationName} · dominio público.\n" +
                    "Las dos voces funcionan sin conexión a internet. " +
                    "Voz IA: Built with Fish Audio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(horizontal = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
