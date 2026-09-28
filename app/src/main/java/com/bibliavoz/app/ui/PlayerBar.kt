package com.bibliavoz.app.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.bibliavoz.app.player.EngineError
import com.bibliavoz.app.player.PlayerBus
import com.bibliavoz.app.player.PlayerState
import kotlinx.coroutines.delay

private val SPEEDS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val TIMER_OPTIONS = listOf(10, 15, 30, 45, 60, 90)

@Composable
fun PlayerBar(
    state: PlayerState,
    reference: String,
    onTogglePlay: () -> Unit,
    onPrevVerse: () -> Unit,
    onNextVerse: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimer: (Int) -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // targetSdk 35 obliga al modo pantalla completa: sin esto los botones
        // quedarían debajo de la barra de navegación del sistema.
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {

            // Mientras suena la voz IA, un fallo del motor del teléfono no impide escuchar.
            if (state.engineError != null && !state.vozIa) {
                VoiceProblemBanner(state.engineError)
            }
            AvisosVozIa(state)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (state.vozIa) "$reference · Voz IA" else reference,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpeedChip(current = state.speechRate, onSpeedChange = onSpeedChange)
                    Spacer(Modifier.width(8.dp))
                    SleepTimerChip(endsAt = state.sleepTimerEndsAt, onSleepTimer = onSleepTimer)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onPrevChapter) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Capítulo anterior")
                }
                IconButton(onClick = onPrevVerse) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Versículo anterior")
                }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Escuchar",
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(onClick = onNextVerse) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Versículo siguiente")
                }
                IconButton(onClick = onNextChapter) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Capítulo siguiente")
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(current: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(formatSpeed(current)) },
            leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, Modifier.size(18.dp)) },
            colors = AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(speed),
                            fontWeight = if (speed == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSpeedChange(speed)
                    },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerChip(endsAt: Long, onSleepTimer: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endsAt) {
        while (endsAt > 0L) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remainingMinutes = if (endsAt > 0L) {
        (((endsAt - now).coerceAtLeast(0L)) / 60_000L + 1).toInt()
    } else {
        0
    }

    Column {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(if (remainingMinutes > 0) "$remainingMinutes min" else "Temporizador") },
            leadingIcon = { Icon(Icons.Rounded.Bedtime, contentDescription = null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TIMER_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("Apagar en $minutes min") },
                    onClick = {
                        expanded = false
                        onSleepTimer(minutes)
                    },
                )
            }
            if (endsAt > 0L) {
                DropdownMenuItem(
                    text = { Text("Cancelar temporizador") },
                    onClick = {
                        expanded = false
                        onSleepTimer(0)
                    },
                )
            }
        }
    }
}

/** Aviso de por qué se pasó de la voz IA a la del teléfono. */
@Composable
fun AvisosVozIa(state: PlayerState) {
    val aviso = state.avisoVoz ?: return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = aviso,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { PlayerBus.update { it.copy(avisoVoz = null) } }) {
                Text("Entendido")
            }
        }
    }
}

/** Explica por qué no se oye nada y ofrece el atajo para arreglarlo. */
@Composable
private fun VoiceProblemBanner(error: EngineError) {
    val context = LocalContext.current
    val message = when (error) {
        EngineError.NO_ENGINE ->
            "Tu teléfono no tiene instalado un motor de voz. Instala «Google Text-to-Speech» para escuchar."
        EngineError.MISSING_SPANISH ->
            "Falta la voz en español. Descárgala desde los ajustes de texto a voz."
        EngineError.INIT_FAILED ->
            "El motor de voz no respondió. Revisa los ajustes de texto a voz del sistema."
    }

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { openVoiceSettings(context) }) {
                Text("Arreglar")
            }
        }
    }
}

private fun openVoiceSettings(context: android.content.Context) {
    // Primero se intenta la descarga de datos de voz; si el motor no la
    // soporta, se abre la pantalla de ajustes de texto a voz del sistema.
    val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val settings = Intent("com.android.settings.TTS_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val started = runCatching { context.startActivity(install) }.isSuccess
    if (!started) runCatching { context.startActivity(settings) }
}

private fun formatSpeed(value: Float): String {
    val rounded = Math.round(value * 100f) / 100f
    return if (rounded == rounded.toInt().toFloat()) {
        "${rounded.toInt()}x"
    } else {
        "${rounded.toString().trimEnd('0').trimEnd('.')}x"
    }
}
