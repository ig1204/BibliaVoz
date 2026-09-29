package com.bibliavoz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.liturgia.ColaLecturas
import com.bibliavoz.app.player.PlaybackService
import com.bibliavoz.app.player.PlayerBus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATO_FECHA: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturasScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    ensureNotificationPermission: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val estado by viewModel.lecturas.collectAsStateWithLifecycle()
    val player by PlayerBus.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var fecha by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(fecha) { viewModel.cargarLecturas(fecha) }

    // Las lecturas se entregan al servicio (ColaLecturas.preparar) solo al
    // pulsar ▶: prepararlas al cambiar de día haría que la notificación de lo
    // que suena mostrara el nombre del otro día.

    // Lo que tiene cargado el servicio es la misa de este mismo día: solo
    // entonces se marca la tarjeta que suena y se ofrece pausar o seguir.
    val esteDia = player.enLecturas && !estado.cargando &&
        estado.lecturas.isNotEmpty() && player.lecturasTitulo == estado.descripcion
    val sonandoEsteDia = esteDia && player.isPlaying
    val pausadoEsteDia = esteDia && !player.isPlaying
    // En un día sin lecturas no hay otra cosa que hacer con el botón: si suena
    // la misa de otro día, al menos se puede pausar desde aquí.
    val soloPausar = player.enLecturas && player.isPlaying &&
        !estado.cargando && estado.lecturas.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lecturas de la misa", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        bottomBar = {
            if (estado.lecturas.isNotEmpty() || soloPausar) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        if (player.enLecturas) AvisosVozIa(player)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val pausar = sonandoEsteDia || soloPausar
                            Button(
                                onClick = {
                                    ensureNotificationPermission()
                                    when {
                                        pausar -> PlaybackService.pause(context)
                                        // Sigue donde se pausó, en la misma palabra. Si el
                                        // servicio ya se apagó, no queda nada que reanudar.
                                        pausadoEsteDia && PlaybackService.running ->
                                            PlaybackService.play(context)
                                        else -> {
                                            ColaLecturas.preparar(estado.descripcion, estado.lecturas)
                                            PlaybackService.leerLecturas(context, 0)
                                        }
                                    }
                                },
                                // Mientras llega el día nuevo, lo de pantalla es aún el anterior.
                                enabled = pausar || !estado.cargando,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    if (pausar) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    when {
                                        pausar -> "Pausar"
                                        pausadoEsteDia -> "Seguir escuchando"
                                        else -> "Escuchar la misa completa"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {

            SelectorDeFecha(
                fecha = fecha,
                descripcion = estado.descripcion,
                onAnterior = { fecha = fecha.minusDays(1) },
                onSiguiente = { fecha = fecha.plusDays(1) },
                onHoy = { fecha = LocalDate.now() },
            )

            when {
                estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                !estado.disponible -> SinLecturas()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(estado.lecturas) { index, lectura ->
                        // El botón de cada tarjeta hace lo mismo que el de abajo, pero
                        // para su lectura: ⏸ mientras suena, ▶ para seguir donde se pausó.
                        val sonando = sonandoEsteDia && player.lecturaIndex == index
                        val enPausa = pausadoEsteDia && player.lecturaIndex == index
                        TarjetaLectura(
                            titulo = lectura.titulo,
                            cita = lectura.cita,
                            texto = lectura.versiculos,
                            fontScale = settings.fontScale,
                            sonando = sonando,
                            actual = sonando || enPausa,
                            onEscuchar = {
                                ensureNotificationPermission()
                                when {
                                    sonando -> PlaybackService.pause(context)
                                    enPausa && PlaybackService.running -> PlaybackService.play(context)
                                    else -> {
                                        ColaLecturas.preparar(estado.descripcion, estado.lecturas)
                                        PlaybackService.leerLecturas(context, index)
                                    }
                                }
                            },
                        )
                    }
                    item { AvisoLeccionario() }
                }
            }
        }
    }
}

@Composable
private fun SelectorDeFecha(
    fecha: LocalDate,
    descripcion: String,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onHoy: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAnterior) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Día anterior")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = fecha.format(FORMATO_FECHA).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (descripcion.isNotBlank()) {
                        Text(
                            text = descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                IconButton(onClick = onSiguiente) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Día siguiente")
                }
            }
            if (fecha != LocalDate.now()) {
                TextButton(onClick = onHoy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Volver a hoy")
                }
            }
        }
    }
}

@Composable
private fun TarjetaLectura(
    titulo: String,
    cita: String,
    texto: List<String>,
    fontScale: Float,
    /** Suena ahora esta lectura. */
    sonando: Boolean,
    /** Es la lectura en curso, suene o esté en pausa. */
    actual: Boolean,
    onEscuchar: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (actual) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titulo.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = cita,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (sonando) {
                    FilledIconButton(onClick = onEscuchar) {
                        Icon(Icons.Rounded.Pause, contentDescription = "Pausar esta lectura")
                    }
                } else {
                    FilledTonalIconButton(onClick = onEscuchar) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = if (actual) "Seguir con esta lectura" else "Escuchar esta lectura",
                        )
                    }
                }
            }

            if (texto.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Esta lectura no se pudo localizar en el texto empaquetado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = texto.joinToString(" "),
                    fontSize = (16 * fontScale).sp,
                    lineHeight = (25 * fontScale).sp,
                )
            }
        }
    }
}

@Composable
private fun SinLecturas() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "No hay lecturas guardadas para este día.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AvisoLeccionario() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
    ) {
        Text(
            text = "Basado en el leccionario romano general. Los domingos y las " +
                "ferias coinciden con México, pero algunas memorias del santoral " +
                "propio mexicano pueden variar.\n\n" +
                "El texto es la «Santa Biblia Libre», de dominio público. No es la " +
                "traducción que se proclama en misa, que tiene derechos reservados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}
