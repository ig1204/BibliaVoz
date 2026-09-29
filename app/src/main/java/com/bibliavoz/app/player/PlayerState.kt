package com.bibliavoz.app.player

import com.bibliavoz.app.data.Position
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Problemas del motor de voz que la interfaz necesita explicar al usuario. */
enum class EngineError {
    /** No hay ningún motor de texto-a-voz instalado en el teléfono. */
    NO_ENGINE,

    /** Hay motor, pero le falta la voz en español. */
    MISSING_SPANISH,

    /** El motor existe pero no arrancó. */
    INIT_FAILED,
}

/** Todo lo que la interfaz necesita saber sobre la lectura en voz alta. */
data class PlayerState(
    val engineReady: Boolean = false,
    val engineError: EngineError? = null,
    val isPlaying: Boolean = false,
    val position: Position = Position(),
    val bookName: String = "",
    val verseCount: Int = 0,
    val speechRate: Float = 1f,
    val pitch: Float = 1f,
    /** Momento (millis del reloj del sistema) en que el temporizador apagará la voz; 0 = sin temporizador. */
    val sleepTimerEndsAt: Long = 0L,
    /** `true` mientras se están leyendo las lecturas de la misa. */
    val enLecturas: Boolean = false,
    /** Índice de la lectura que suena, dentro de las del día. */
    val lecturaIndex: Int = 0,
    /** Título de la lectura que suena. */
    val lecturaTitulo: String = "",
    /**
     * Título del día cuyas lecturas lee el servicio ([com.bibliavoz.app.liturgia.ColaLecturas.titulo]
     * al empezar); vacío fuera de las lecturas. Sirve para saber si lo que suena
     * es el día que muestra la pantalla.
     */
    val lecturasTitulo: String = "",
    /** Lo que suena es la voz IA grabada, no la del teléfono. */
    val vozIa: Boolean = false,
    /** Aviso de la voz IA para el usuario (un archivo dañado…); `null` si no hay. */
    val avisoVoz: String? = null,
) {
    val hasSleepTimer: Boolean get() = sleepTimerEndsAt > 0L

    /** El mismo estado sin misa en curso ni voz sonando: lo que queda al apagarse el servicio. */
    fun sinMisa(): PlayerState = copy(
        enLecturas = false,
        lecturaIndex = 0,
        lecturaTitulo = "",
        lecturasTitulo = "",
        vozIa = false,
        avisoVoz = null,
    )
}

/**
 * Estado compartido entre [PlaybackService] y la interfaz.
 *
 * El servicio es el único que escribe; la interfaz solo observa. Así la
 * pantalla no necesita hacer bind al servicio ni preocuparse por su ciclo de
 * vida: si el servicio muere, el último estado conocido sigue siendo válido.
 */
object PlayerBus {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    internal fun update(transform: (PlayerState) -> PlayerState) {
        _state.update(transform)
    }
}
