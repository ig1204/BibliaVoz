package com.bibliavoz.app

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliavoz.app.data.Position
import com.bibliavoz.app.player.PlayerBus
import com.bibliavoz.app.ui.BooksScreen
import com.bibliavoz.app.ui.ChaptersScreen
import com.bibliavoz.app.ui.LecturasScreen
import com.bibliavoz.app.ui.MainViewModel
import com.bibliavoz.app.ui.ReaderScreen
import com.bibliavoz.app.ui.theme.BibliaVozTheme
import com.bibliavoz.app.ui.theme.temaOscuro

/** Pantallas de la app. La pila es tan corta que no hace falta una librería de navegación. */
sealed interface Screen {
    data object Books : Screen
    data object Lecturas : Screen
    data class Chapters(val book: Int) : Screen
    data class Reader(val book: Int, val chapter: Int) : Screen
}

/**
 * La pila se guarda como números (tipo, libro, capítulo): así sobrevive a que
 * el sistema recree la pantalla o cierre la app en segundo plano.
 */
private val PilaSaver = listSaver<SnapshotStateList<Screen>, Int>(
    save = { pila ->
        pila.flatMap { pantalla ->
            when (pantalla) {
                is Screen.Books -> listOf(0, 0, 0)
                is Screen.Lecturas -> listOf(1, 0, 0)
                is Screen.Chapters -> listOf(2, pantalla.book, 0)
                is Screen.Reader -> listOf(3, pantalla.book, pantalla.chapter)
            }
        }
    },
    restore = { plano ->
        val pila = mutableStateListOf<Screen>()
        plano.chunked(3).forEach { trio ->
            if (trio.size == 3) {
                when (trio[0]) {
                    0 -> pila.add(Screen.Books)
                    1 -> pila.add(Screen.Lecturas)
                    2 -> pila.add(Screen.Chapters(trio[1]))
                    3 -> pila.add(Screen.Reader(trio[1], trio[2]))
                }
            }
        }
        if (pila.isEmpty() || pila.first() != Screen.Books) pila.add(0, Screen.Books)
        pila
    },
)

// Velos de la barra de navegación de tres botones: los mismos que pone
// enableEdgeToEdge() por defecto (la librería no los publica).
private val VELO_CLARO = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val VELO_OSCURO = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // "Mantener pantalla encendida" se aplica sobre la ventana real.
            DisposableEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Los iconos de la barra de estado siguen al tema de la app, no al
            // del sistema: si no, con Tema Oscuro en un teléfono en modo claro
            // la hora y la batería quedan negras sobre fondo negro. Se recalcula
            // también cuando el sistema cambia de modo con la app abierta.
            val dark = temaOscuro(settings.themeMode)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(VELO_CLARO, VELO_OSCURO) { dark },
                )
                onDispose { }
            }

            BibliaVozTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val backStack: SnapshotStateList<Screen> = rememberSaveable(saver = PilaSaver) {
        mutableStateListOf<Screen>(Screen.Books)
    }

    // En Android 13+ la notificación de reproducción necesita permiso. La voz
    // funciona igual sin él, así que se pide una sola vez y no se bloquea nada.
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el resultado no cambia el comportamiento de la lectura */ }

    val ensureNotificationPermission: () -> Unit = {
        if (!alreadyAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            alreadyAsked = true
            runCatching { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    val pop: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    when (val current = backStack.last()) {
        is Screen.Books -> BooksScreen(
            viewModel = viewModel,
            onOpenBook = { book -> backStack.add(Screen.Chapters(book)) },
            onOpenReader = { book, chapter -> backStack.add(Screen.Reader(book, chapter)) },
            onOpenLecturas = { backStack.add(Screen.Lecturas) },
            ensureNotificationPermission = ensureNotificationPermission,
        )

        is Screen.Lecturas -> LecturasScreen(
            viewModel = viewModel,
            onBack = pop,
            ensureNotificationPermission = ensureNotificationPermission,
        )

        is Screen.Chapters -> ChaptersScreen(
            viewModel = viewModel,
            bookNumber = current.book,
            onBack = pop,
            onOpenChapter = { chapter ->
                // Elegir un capítulo es un cambio de verdad: pasa a ser la
                // posición guardada. El lector no guarda al abrirse porque
                // también se reabre al volver con Atrás. Mientras suena la
                // Biblia, la posición la manda el servicio.
                val player = PlayerBus.state.value
                if (!player.isPlaying || player.enLecturas) {
                    val saved = viewModel.savedPosition
                    if (saved.book != current.book || saved.chapter != chapter) {
                        viewModel.savePosition(Position(current.book, chapter, 0))
                    }
                }
                // Si la lista se abrió desde un lector, el nuevo lo sustituye:
                // así Atrás no vuelve a un capítulo viejo y la pila no crece.
                val top = backStack.lastIndex
                if (top >= 1 && backStack[top - 1] is Screen.Reader) {
                    backStack.removeAt(top)
                    backStack.removeAt(top - 1)
                }
                backStack.add(Screen.Reader(current.book, chapter))
            },
        )

        is Screen.Reader -> ReaderScreen(
            viewModel = viewModel,
            bookNumber = current.book,
            chapterNumber = current.chapter,
            onBack = pop,
            onPickChapter = { book -> backStack.add(Screen.Chapters(book)) },
            onChapterShown = { book, chapter ->
                // La entrada de la pila sigue al capítulo que se ve (al pasar
                // de capítulo con los botones o siguiendo a la voz): al volver
                // de la lista de capítulos se reabre este, no el primero.
                val top = backStack.lastIndex
                val shown = Screen.Reader(book, chapter)
                if (backStack[top] is Screen.Reader && backStack[top] != shown) backStack[top] = shown
            },
            ensureNotificationPermission = ensureNotificationPermission,
        )
    }
}
