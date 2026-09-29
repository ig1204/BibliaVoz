package com.bibliavoz.app.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.player.PlaybackService
import com.bibliavoz.app.player.VoiceCatalog

/**
 * Selector de voz.
 *
 * Android no expone el sexo de una voz por ninguna API, y los nombres técnicos
 * ("es-es-x-eed-local") no lo dicen de forma fiable. Así que en vez de etiquetar
 * voces como masculinas —y arriesgarse a mentir— se ofrece escucharlas: el
 * usuario elige con el oído en unos segundos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePickerSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.voiceCatalog.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        // La voz de prueba y la lectura pelearían por el altavoz. Si el servicio
        // no existe no hay nada que pausar, y despertarlo dejaría una
        // notificación de reproducción sin que suene nada.
        if (PlaybackService.running) PlaybackService.pause(context)
        viewModel.loadVoiceCatalog()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Elegir voz",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Toca ▶ para oír cada voz y quédate con la que más te guste. " +
                    "Las voces las pone el sistema Android, no la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !state.engineWorks -> NoEngineMessage()

                else -> {
                    if (state.engines.size > 1) {
                        Text("Motor de voz", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.engines.take(3).forEach { engine ->
                                FilterChip(
                                    selected = state.selectedEngine == engine.packageName,
                                    onClick = {
                                        viewModel.onEngineSelected(engine.packageName)
                                        PlaybackService.setEngine(context, engine.packageName)
                                    },
                                    label = { Text(engine.label, maxLines = 1) },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Voces que necesitan internet", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Suelen sonar mejor, pero no funcionan sin conexión",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.allowNetwork,
                            onCheckedChange = { allow ->
                                viewModel.onAllowNetworkChanged(allow)
                                PlaybackService.setAllowNetworkVoices(context, allow)
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    if (state.voices.isEmpty()) {
                        NoSpanishVoiceMessage()
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                            items(state.voices, key = { it.name }) { voice ->
                                VoiceRow(
                                    voice = voice,
                                    selected = state.selectedVoice == voice.name,
                                    onPreview = { viewModel.previewVoice(voice.name) },
                                    onSelect = {
                                        viewModel.onVoiceSelected(voice.name)
                                        PlaybackService.setVoice(context, voice.name)
                                    },
                                )
                            }
                            item {
                                TextButton(onClick = {
                                    viewModel.onVoiceSelected(null)
                                    PlaybackService.setVoice(context, null)
                                }) {
                                    Text("Usar la mejor voz automáticamente")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceCatalog.VoiceOption,
    selected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(voice.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Calidad ${voice.qualityLabel.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (voice.requiresNetwork) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            Icons.Rounded.CloudQueue,
                            contentDescription = "Necesita internet",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Elegida",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
            }
            FilledTonalIconButton(onClick = onPreview) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Escuchar esta voz")
            }
        }
    }
}

@Composable
private fun NoEngineMessage() {
    val context = LocalContext.current
    Column {
        Text(
            text = "No se pudo arrancar el motor de voz del teléfono. " +
                "Instala «Google Text-to-Speech» desde Play Store y vuelve a intentarlo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { openTtsSettings(context) }) {
            Text("Abrir ajustes de texto a voz")
        }
    }
}

@Composable
private fun NoSpanishVoiceMessage() {
    val context = LocalContext.current
    Column {
        Text(
            text = "Este motor no tiene ninguna voz en español instalada. " +
                "Descárgala desde los ajustes de texto a voz, o prueba otro motor.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { openTtsSettings(context) }) {
            Text("Descargar voz en español")
        }
    }
}

private fun openTtsSettings(context: android.content.Context) {
    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val settings = Intent("com.android.settings.TTS_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!runCatching { context.startActivity(install) }.isSuccess) {
        runCatching { context.startActivity(settings) }
    }
}
