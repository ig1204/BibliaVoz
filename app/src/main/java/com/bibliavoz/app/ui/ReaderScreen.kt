package com.bibliavoz.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliavoz.app.data.Position
import com.bibliavoz.app.player.PlaybackService
import com.bibliavoz.app.player.PlayerBus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: MainViewModel,
    bookNumber: Int,
    chapterNumber: Int,
    onBack: () -> Unit,
    onPickChapter: (Int) -> Unit,
    onChapterShown: (Int, Int) -> Unit,
    ensureNotificationPermission: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val playerState by PlayerBus.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chapter by viewModel.chapter.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showVozIa by remember { mutableStateOf(false) }

    // Posición que se está mostrando. Si se vuelve al capítulo donde se quedó
    // se conserva el versículo; si se abre otro capítulo, empieza por el principio.
    var displayed by remember {
        val saved = viewModel.savedPosition
        mutableStateOf(
            if (saved.book == bookNumber && saved.chapter == chapterNumber) {
                saved
            } else {
                Position(bookNumber, chapterNumber, 0)
            }
        )
    }

    // Suena la Biblia. Durante las lecturas de la misa la posición del
    // reproductor es la del capítulo guardado, no lo que se oye.
    val bibleSounding = playerState.isPlaying && !playerState.enLecturas

    // Mientras la voz suena, la pantalla la sigue. Pero si el usuario se va a
    // hojear otro capítulo, deja de seguirla para no arrastrarlo de vuelta.
    // También se sigue si se abre el capítulo guardado: «Continuar escuchando
    // ▶» manda la voz aquí antes de que el servicio lo haya publicado.
    var following by remember {
        val current = PlayerBus.state.value.position
        val saved = viewModel.savedPosition
        mutableStateOf(
            (current.book == bookNumber && current.chapter == chapterNumber) ||
                (saved.book == bookNumber && saved.chapter == chapterNumber)
        )
    }

    // También la última publicación, la de cuando deja de sonar: con «Continuar
    // solo» apagado la voz se para ya en el versículo 1 del capítulo siguiente,
    // y la pantalla tiene que ir ahí para que ▶ siga desde ese punto.
    var sonabaBiblia by remember { mutableStateOf(false) }
    LaunchedEffect(bibleSounding, playerState.position) {
        if (following && !playerState.enLecturas && (bibleSounding || sonabaBiblia)) {
            displayed = playerState.position
        }
        sonabaBiblia = bibleSounding
    }

    LaunchedEffect(displayed.book, displayed.chapter) {
        viewModel.loadChapter(displayed.book, displayed.chapter)
        onChapterShown(displayed.book, displayed.chapter)
    }

    // Solo se guarda lo que el usuario cambia estando aquí (capítulo o
    // versículo). La primera vez no: la pantalla puede ser un lector que
    // vuelve con Atrás, y pisaría la posición buena con la suya.
    // Mientras suena la Biblia, la posición guardada la manda el servicio.
    var firstPass by remember { mutableStateOf(true) }
    LaunchedEffect(displayed) {
        if (firstPass) {
            firstPass = false
        } else if (!bibleSounding) {
            viewModel.savePosition(displayed)
        }
    }

    val listState = rememberLazyListState()
    val onSameChapter = playerState.position.sameChapter(displayed)
    val highlighted = if (bibleSounding && onSameChapter) {
        playerState.position.verse
    } else {
        displayed.verse
    }

    // Desplazar la lista para que el versículo que suena quede a la vista,
    // dejando uno de contexto por encima.
    LaunchedEffect(highlighted, bibleSounding, displayed.book, displayed.chapter) {
        if (bibleSounding && onSameChapter) {
            runCatching { listState.animateScrollToItem((highlighted - 1).coerceAtLeast(0)) }
        }
    }

    // Sin la voz en este capítulo, llevar a la vista el versículo marcado: al
    // abrir el capítulo guardado (Salmos 119:150), al cambiar de capítulo o al
    // moverse con ⏪/⏩ en pausa. Si ya se ve (por ejemplo, el que se acaba de
    // tocar), la lista no se mueve.
    LaunchedEffect(chapter, displayed.verse) {
        val loaded = chapter ?: return@LaunchedEffect
        if (bibleSounding && onSameChapter) return@LaunchedEffect
        if (loaded.bookNumber != displayed.book || loaded.chapterNumber != displayed.chapter) {
            return@LaunchedEffect
        }
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == displayed.verse }
        val inView = item != null && item.offset >= layout.viewportStartOffset &&
            item.offset + item.size <= layout.viewportEndOffset
        if (!inView) {
            val target = (displayed.verse - 1).coerceAtLeast(0)
            runCatching {
                // Recién cargado aún no hay nada dibujado: salto directo.
                if (layout.visibleItemsInfo.isEmpty()) {
                    listState.scrollToItem(target)
                } else {
                    listState.animateScrollToItem(target)
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false },
            onOpenVozIa = { showSettings = false; showVozIa = true },
        )
    }
    if (showVozIa) {
        VozIaSheet(viewModel = viewModel, onDismiss = { showVozIa = false })
    }

    val moveChapter: (Int) -> Unit = { delta ->
        val target = if (delta > 0) {
            viewModel.nextChapter(displayed.book, displayed.chapter)
        } else {
            viewModel.previousChapter(displayed.book, displayed.chapter)
        }
        if (target != null) {
            displayed = Position(target.first, target.second, 0)
            if (playerState.isPlaying) {
                // Los botones de la barra son controles del reproductor: mueven
                // la lectura de verdad, así que la pantalla vuelve a seguirla.
                following = true
                PlaybackService.seek(context, displayed, autoPlay = false)
            }
        }
    }

    val moveVerse: (Int) -> Unit = { delta ->
        if (playerState.isPlaying) {
            if (delta > 0) PlaybackService.nextVerse(context) else PlaybackService.previousVerse(context)
        } else {
            val verses = chapter?.verseCount ?: 0
            val target = displayed.verse + delta
            when {
                verses == 0 -> Unit
                target < 0 -> moveChapter(-1)
                target >= verses -> moveChapter(+1)
                else -> displayed = displayed.copy(verse = target)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = chapter?.reference ?: viewModel.bookName(displayed.book),
                            fontWeight = FontWeight.SemiBold,
                        )
                        val count = chapter?.verseCount ?: 0
                        if (count > 0) {
                            Text(
                                text = "$count versículos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { onPickChapter(displayed.book) }) {
                        Icon(Icons.Rounded.FormatListNumbered, contentDescription = "Elegir capítulo")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        bottomBar = {
            PlayerBar(
                state = playerState,
                reference = displayed.reference(chapter?.bookName ?: viewModel.bookName(displayed.book)),
                onTogglePlay = {
                    ensureNotificationPermission()
                    if (playerState.isPlaying) {
                        PlaybackService.pause(context)
                    } else {
                        following = true
                        if (PlaybackService.running && !playerState.enLecturas &&
                            playerState.position == displayed
                        ) {
                            // Se ve justo donde se pausó: se reanuda, y la voz IA
                            // sigue en la misma palabra en vez de repetir el versículo.
                            PlaybackService.play(context)
                        } else {
                            PlaybackService.seek(context, displayed, autoPlay = true)
                        }
                    }
                },
                onPrevVerse = { moveVerse(-1) },
                onNextVerse = { moveVerse(+1) },
                onPrevChapter = { moveChapter(-1) },
                onNextChapter = { moveChapter(+1) },
                onSpeedChange = { rate ->
                    PlaybackService.setSpeechRate(context, rate)
                    viewModel.onSpeechRateChanged(rate)
                },
                onSleepTimer = { minutes -> PlaybackService.sleepTimer(context, minutes) },
            )
        },
    ) { innerPadding ->
        val verses = chapter?.verses
        if (verses == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando capítulo…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                itemsIndexed(verses) { index, text ->
                    VerseRow(
                        number = index + 1,
                        text = text,
                        isCurrent = index == highlighted,
                        fontScale = settings.fontScale,
                        onClick = {
                            ensureNotificationPermission()
                            following = true
                            displayed = displayed.copy(verse = index)
                            PlaybackService.seek(
                                context,
                                Position(displayed.book, displayed.chapter, index),
                                autoPlay = true,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VerseRow(
    number: Int,
    text: String,
    isCurrent: Boolean,
    fontScale: Float,
    onClick: () -> Unit,
) {
    val background = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val numberColor = MaterialTheme.colorScheme.primary

    val annotated: AnnotatedString = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = numberColor,
                fontWeight = FontWeight.Bold,
                fontSize = (13 * fontScale).sp,
            )
        ) {
            append("$number ")
        }
        append(text)
    }

    Text(
        text = annotated,
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        fontSize = (17 * fontScale).sp,
        lineHeight = (27 * fontScale).sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
