package com.bibliavoz.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliavoz.app.ui.BooksScreen
import com.bibliavoz.app.ui.ChaptersScreen
import com.bibliavoz.app.ui.LecturasScreen
import com.bibliavoz.app.ui.MainViewModel
import com.bibliavoz.app.ui.ReaderScreen
import com.bibliavoz.app.ui.theme.BibliaVozTheme

/** Pantallas de la app. La pila es tan corta que no hace falta una librería de navegación. */
sealed interface Screen {
    data object Books : Screen
    data object Lecturas : Screen
    data class Chapters(val book: Int) : Screen
    data class Reader(val book: Int, val chapter: Int) : Screen
}

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
    val backStack: SnapshotStateList<Screen> = remember { mutableStateListOf<Screen>(Screen.Books) }

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
            onOpenChapter = { chapter -> backStack.add(Screen.Reader(current.book, chapter)) },
        )

        is Screen.Reader -> ReaderScreen(
            viewModel = viewModel,
            bookNumber = current.book,
            chapterNumber = current.chapter,
            onBack = pop,
            onPickChapter = { book -> backStack.add(Screen.Chapters(book)) },
            ensureNotificationPermission = ensureNotificationPermission,
        )
    }
}
