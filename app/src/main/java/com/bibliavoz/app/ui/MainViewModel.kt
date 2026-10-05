package com.bibliavoz.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bibliavoz.app.data.BibleRepository
import com.bibliavoz.app.data.BookInfo
import com.bibliavoz.app.data.Chapter
import com.bibliavoz.app.data.Position
import com.bibliavoz.app.data.Prefs
import com.bibliavoz.app.liturgia.Leccionario
import com.bibliavoz.app.liturgia.LecturaConTexto
import com.bibliavoz.app.player.AudioLocal
import com.bibliavoz.app.player.PlaybackService
import com.bibliavoz.app.voz.VocesIa
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes que la interfaz necesita observar en vivo. */
data class SettingsState(
    val speechRate: Float = 1f,
    val fontScale: Float = 1f,
    val announceChapter: Boolean = true,
    val autoContinue: Boolean = true,
    val keepScreenOn: Boolean = false,
    val themeMode: Int = 0,
)

/** Lo que la pantalla de la voz IA necesita mostrar: qué audio grabado hay en el teléfono. */
data class VozIaState(
    val capitulos: Int = 0,
    val lecturas: Int = 0,
    val bytes: Long = 0L,
    /** Lo que el manifiesto dice que se copió; si hay más que lo completo, faltan archivos. */
    val capitulosDeclarados: Int = 0,
    val lecturasDeclaradas: Int = 0,
    val voz: String = VocesIa.VOZ_NOMBRE,
    /** El audio viene dentro de la app, no copiado por cable. */
    val enApk: Boolean = false,
    /** Ya se miró qué hay instalado (antes de eso no se sabe si hay audio). */
    val revisado: Boolean = false,
) {
    val hayAudio: Boolean get() = capitulos > 0 || lecturas > 0

    /** El manifiesto nombra audio que no está entero en el teléfono (una copia a medias). */
    val faltanArchivos: Boolean
        get() = capitulos < capitulosDeclarados || lecturas < lecturasDeclaradas
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BibleRepository.get(app)
    private val prefs = Prefs.get(app)

    private val _books = MutableStateFlow<List<BookInfo>>(emptyList())
    val books: StateFlow<List<BookInfo>> = _books.asStateFlow()

    private val _chapter = MutableStateFlow<Chapter?>(null)
    val chapter: StateFlow<Chapter?> = _chapter.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    val translationName: String get() = repo.translationName

    /** Última posición guardada, ya recortada a algo que exista. */
    val savedPosition: Position get() = prefs.lastPosition

    private var loadingKey: Pair<Int, Int>? = null

    init {
        viewModelScope.launch {
            _books.value = withContext(Dispatchers.IO) { repo.books }
        }
    }

    private fun readSettings() = SettingsState(
        speechRate = prefs.speechRate,
        fontScale = prefs.fontScale,
        announceChapter = prefs.announceChapter,
        autoContinue = prefs.autoContinue,
        keepScreenOn = prefs.keepScreenOn,
        themeMode = prefs.themeMode,
    )

    fun bookName(number: Int): String =
        _books.value.firstOrNull { it.number == number }?.name ?: ""

    fun chapterCount(number: Int): Int =
        _books.value.firstOrNull { it.number == number }?.chapterCount ?: 1

    fun nextChapter(book: Int, chapterNumber: Int): Pair<Int, Int>? = when {
        chapterNumber < chapterCount(book) -> book to (chapterNumber + 1)
        book < (_books.value.lastOrNull()?.number ?: 73) -> (book + 1) to 1
        else -> null
    }

    fun previousChapter(book: Int, chapterNumber: Int): Pair<Int, Int>? = when {
        chapterNumber > 1 -> book to (chapterNumber - 1)
        book > 1 -> (book - 1) to chapterCount(book - 1)
        else -> null
    }

    /** Guarda dónde está leyendo el usuario aunque no esté sonando la voz. */
    fun savePosition(position: Position) {
        prefs.lastPosition = position
    }

    /** Carga un capítulo en segundo plano; ignora peticiones repetidas. */
    fun loadChapter(book: Int, chapterNumber: Int) {
        val key = book to chapterNumber
        val current = _chapter.value
        if (current?.bookNumber == book && current.chapterNumber == chapterNumber) {
            // Se volvió al capítulo que ya estaba: la carga de otro que quedó a
            // medias ya no sirve y no debe pisarlo cuando termine.
            loadingKey = null
            return
        }
        if (loadingKey == key) return
        loadingKey = key
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repo.chapter(book, chapterNumber) }
            if (loadingKey == key) {
                _chapter.value = loaded
                loadingKey = null
            }
        }
    }

    // ------------------------------------------------------------ ajustes

    fun setFontScale(value: Float) {
        prefs.fontScale = value
        _settings.value = _settings.value.copy(fontScale = prefs.fontScale)
    }

    fun setAnnounceChapter(value: Boolean) {
        prefs.announceChapter = value
        _settings.value = _settings.value.copy(announceChapter = value)
    }

    fun setAutoContinue(value: Boolean) {
        prefs.autoContinue = value
        _settings.value = _settings.value.copy(autoContinue = value)
    }

    fun setKeepScreenOn(value: Boolean) {
        prefs.keepScreenOn = value
        _settings.value = _settings.value.copy(keepScreenOn = value)
    }

    fun setThemeMode(value: Int) {
        prefs.themeMode = value
        _settings.value = _settings.value.copy(themeMode = value)
    }

    /** El servicio es quien manda de verdad; aquí solo reflejamos el valor. */
    fun onSpeechRateChanged(value: Float) {
        _settings.value = _settings.value.copy(speechRate = value)
    }

    // ------------------------------------------------------------ voz IA

    private val audioLocal = AudioLocal(app)

    private val _vozIa = MutableStateFlow(VozIaState())
    val vozIa: StateFlow<VozIaState> = _vozIa.asStateFlow()

    init {
        refrescarVozIa()
    }

    /** Mira qué audio de voz IA hay instalado (lee el manifiesto en segundo plano). */
    fun refrescarVozIa() {
        viewModelScope.launch {
            val resumen = withContext(Dispatchers.IO) {
                audioLocal.recargarSiCambio()
                audioLocal.resumen
            }
            _vozIa.update {
                it.copy(
                    capitulos = resumen.capitulos,
                    lecturas = resumen.lecturas,
                    bytes = resumen.bytes,
                    capitulosDeclarados = resumen.capitulosDeclarados,
                    lecturasDeclaradas = resumen.lecturasDeclaradas,
                    voz = resumen.voz.ifBlank { VocesIa.VOZ_NOMBRE },
                    enApk = resumen.enApk,
                    revisado = true,
                )
            }
        }
    }

    /** Capítulos de la Biblia en total, para decir cuántos tienen voz IA. */
    val totalCapitulos: Int get() = _books.value.sumOf { it.chapterCount }

    // ------------------------------------------------------------ lecturas de la misa

    private val leccionario = Leccionario.get(app)

    private val _lecturas = MutableStateFlow(LecturasUiState())
    val lecturas: StateFlow<LecturasUiState> = _lecturas.asStateFlow()

    private var cargaLecturas: Job? = null

    fun cargarLecturas(fecha: LocalDate) {
        // Al pulsar rápido las flechas de día, cada carga tarda distinto según
        // los libros que haya que leer: la vieja no debe pisar a la nueva.
        cargaLecturas?.cancel()
        _lecturas.value = _lecturas.value.copy(fecha = fecha, cargando = true)
        cargaLecturas = viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val dia = leccionario.delDia(fecha)
                if (dia == null) null else dia to leccionario.conTexto(dia)
            }
            // Cancelar no corta una lectura de disco ya empezada: se comprueba aquí.
            if (_lecturas.value.fecha != fecha) return@launch
            _lecturas.value = if (resultado == null) {
                LecturasUiState(fecha = fecha, cargando = false, disponible = false)
            } else {
                LecturasUiState(
                    fecha = fecha,
                    cargando = false,
                    disponible = true,
                    descripcion = resultado.first.descripcion,
                    lecturas = resultado.second,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

/** Estado de la pantalla de lecturas de la misa. */
data class LecturasUiState(
    val fecha: LocalDate = LocalDate.now(),
    val cargando: Boolean = true,
    val disponible: Boolean = true,
    val descripcion: String = "",
    val lecturas: List<LecturaConTexto> = emptyList(),
)
