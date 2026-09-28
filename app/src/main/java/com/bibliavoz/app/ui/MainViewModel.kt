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
import com.bibliavoz.app.player.VoiceCatalog
import com.bibliavoz.app.voz.VocesIa
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes que la interfaz necesita observar en vivo. */
data class SettingsState(
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    val fontScale: Float = 1f,
    val announceChapter: Boolean = true,
    val announceVerseNumbers: Boolean = false,
    val autoContinue: Boolean = true,
    val keepScreenOn: Boolean = false,
    val themeMode: Int = 0,
)

/** Lo que la pantalla de la voz IA necesita mostrar: qué audio grabado hay en el teléfono. */
data class VozIaState(
    val activa: Boolean = true,
    val capitulos: Int = 0,
    val lecturas: Int = 0,
    val bytes: Long = 0L,
    val voz: String = VocesIa.VOZ_NOMBRE,
    /** Ya se miró qué hay instalado (antes de eso no se sabe si hay audio). */
    val revisado: Boolean = false,
) {
    val hayAudio: Boolean get() = capitulos > 0 || lecturas > 0
}

/** Lo que la pantalla de selección de voz necesita mostrar. */
data class VoiceCatalogState(
    val loading: Boolean = true,
    val engineWorks: Boolean = true,
    val engines: List<VoiceCatalog.EngineOption> = emptyList(),
    val voices: List<VoiceCatalog.VoiceOption> = emptyList(),
    val selectedEngine: String? = null,
    val selectedVoice: String? = null,
    val allowNetwork: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BibleRepository.get(app)
    private val prefs = Prefs.get(app)
    private val catalog = VoiceCatalog(app)

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
        pitch = prefs.pitch,
        fontScale = prefs.fontScale,
        announceChapter = prefs.announceChapter,
        announceVerseNumbers = prefs.announceVerseNumbers,
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
        book < 66 -> (book + 1) to 1
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
        if (current?.bookNumber == book && current.chapterNumber == chapterNumber) return
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

    fun setAnnounceVerseNumbers(value: Boolean) {
        prefs.announceVerseNumbers = value
        _settings.value = _settings.value.copy(announceVerseNumbers = value)
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

    fun onPitchChanged(value: Float) {
        _settings.value = _settings.value.copy(pitch = value)
    }

    // ------------------------------------------------------------ voces

    private val _voiceCatalog = MutableStateFlow(VoiceCatalogState())
    val voiceCatalog: StateFlow<VoiceCatalogState> = _voiceCatalog.asStateFlow()

    /** Arranca el motor y lee el catálogo. El aviso de listo llega en otro hilo. */
    fun loadVoiceCatalog() {
        _voiceCatalog.value = _voiceCatalog.value.copy(loading = true)
        catalog.start(prefs.ttsEngine) { ok -> publishCatalog(ok) }
    }

    private fun publishCatalog(engineWorks: Boolean) {
        val allowNetwork = prefs.allowNetworkVoices
        _voiceCatalog.value = VoiceCatalogState(
            loading = false,
            engineWorks = engineWorks,
            engines = if (engineWorks) catalog.engines() else emptyList(),
            voices = if (engineWorks) catalog.spanishVoices(allowNetwork) else emptyList(),
            selectedEngine = prefs.ttsEngine,
            selectedVoice = prefs.voiceName,
            allowNetwork = allowNetwork,
        )
    }

    fun previewVoice(voiceName: String) {
        catalog.preview(voiceName, prefs.speechRate, prefs.pitch)
    }

    fun stopPreview() {
        catalog.stopPreview()
    }

    /** Solo refleja la elección: quien la guarda y la aplica es [PlaybackService]. */
    fun onVoiceSelected(voiceName: String?) {
        _voiceCatalog.value = _voiceCatalog.value.copy(selectedVoice = voiceName)
    }

    fun onEngineSelected(enginePackage: String?) {
        _voiceCatalog.value = _voiceCatalog.value.copy(
            selectedEngine = enginePackage,
            selectedVoice = null,
            loading = true,
        )
        catalog.start(enginePackage) { ok -> publishCatalog(ok) }
    }

    fun onAllowNetworkChanged(allow: Boolean) {
        _voiceCatalog.value = _voiceCatalog.value.copy(
            allowNetwork = allow,
            voices = catalog.spanishVoices(allow),
        )
    }

    // ------------------------------------------------------------ voz IA

    private val audioLocal = AudioLocal(app)

    private val _vozIa = MutableStateFlow(VozIaState(activa = prefs.vozIaActiva))
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
                    activa = prefs.vozIaActiva,
                    capitulos = resumen.capitulos,
                    lecturas = resumen.lecturas,
                    bytes = resumen.bytes,
                    voz = resumen.voz.ifBlank { VocesIa.VOZ_NOMBRE },
                    revisado = true,
                )
            }
        }
    }

    fun setVozIaActiva(activa: Boolean) {
        prefs.vozIaActiva = activa
        _vozIa.update { it.copy(activa = activa) }
        PlaybackService.vozIaCambio(getApplication<Application>())
    }

    /** Capítulos de la Reina-Valera en total, para decir cuántos tienen voz IA. */
    val totalCapitulos: Int get() = _books.value.sumOf { it.chapterCount }

    // ------------------------------------------------------------ lecturas de la misa

    private val leccionario = Leccionario.get(app)

    private val _lecturas = MutableStateFlow(LecturasUiState())
    val lecturas: StateFlow<LecturasUiState> = _lecturas.asStateFlow()

    fun cargarLecturas(fecha: LocalDate) {
        _lecturas.value = _lecturas.value.copy(fecha = fecha, cargando = true)
        viewModelScope.launch {
            val resultado = withContext(Dispatchers.IO) {
                val dia = leccionario.delDia(fecha)
                if (dia == null) null else dia to leccionario.conTexto(dia)
            }
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
        catalog.shutdown()
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
