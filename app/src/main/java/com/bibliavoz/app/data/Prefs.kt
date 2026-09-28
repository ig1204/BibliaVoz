package com.bibliavoz.app.data

import android.content.Context
import android.content.SharedPreferences

/** Preferencias del usuario y última posición de lectura. */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("biblia_voz", Context.MODE_PRIVATE)

    var lastPosition: Position
        get() = Position(
            book = sp.getInt(KEY_BOOK, 1),
            chapter = sp.getInt(KEY_CHAPTER, 1),
            verse = sp.getInt(KEY_VERSE, 0),
        )
        set(value) {
            sp.edit()
                .putInt(KEY_BOOK, value.book)
                .putInt(KEY_CHAPTER, value.chapter)
                .putInt(KEY_VERSE, value.verse)
                .apply()
        }

    /** Velocidad de la voz. 1.0 = normal. */
    var speechRate: Float
        get() = sp.getFloat(KEY_RATE, 1.0f).coerceIn(MIN_RATE, MAX_RATE)
        set(value) { sp.edit().putFloat(KEY_RATE, value.coerceIn(MIN_RATE, MAX_RATE)).apply() }

    /** Tono de la voz. 1.0 = normal. */
    var pitch: Float
        get() = sp.getFloat(KEY_PITCH, 1.0f).coerceIn(MIN_PITCH, MAX_PITCH)
        set(value) { sp.edit().putFloat(KEY_PITCH, value.coerceIn(MIN_PITCH, MAX_PITCH)).apply() }

    /** Multiplicador del tamaño de letra en el lector. */
    var fontScale: Float
        get() = sp.getFloat(KEY_FONT, 1.0f).coerceIn(0.8f, 2.0f)
        set(value) { sp.edit().putFloat(KEY_FONT, value.coerceIn(0.8f, 2.0f)).apply() }

    /** Anunciar "Libro, capítulo N" al empezar cada capítulo. */
    var announceChapter: Boolean
        get() = sp.getBoolean(KEY_ANNOUNCE_CHAPTER, true)
        set(value) { sp.edit().putBoolean(KEY_ANNOUNCE_CHAPTER, value).apply() }

    /** Leer en voz alta el número de cada versículo. */
    var announceVerseNumbers: Boolean
        get() = sp.getBoolean(KEY_ANNOUNCE_VERSE, false)
        set(value) { sp.edit().putBoolean(KEY_ANNOUNCE_VERSE, value).apply() }

    /** Encadenar automáticamente con el capítulo siguiente. */
    var autoContinue: Boolean
        get() = sp.getBoolean(KEY_AUTO_CONTINUE, true)
        set(value) { sp.edit().putBoolean(KEY_AUTO_CONTINUE, value).apply() }

    /** Mantener la pantalla encendida mientras se escucha. */
    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) { sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply() }

    /** Paquete del motor de texto a voz elegido. `null` = el que traiga el sistema. */
    var ttsEngine: String?
        get() = sp.getString(KEY_ENGINE, null)
        set(value) { sp.edit().putString(KEY_ENGINE, value).apply() }

    /** Nombre técnico de la voz elegida (Voice.getName()). `null` = la mejor automática. */
    var voiceName: String?
        get() = sp.getString(KEY_VOICE, null)
        set(value) { sp.edit().putString(KEY_VOICE, value).apply() }

    /** Versión de la Biblia elegida para la lectura libre. */
    var versionId: String
        get() = sp.getString(KEY_VERSION, "rv1909") ?: "rv1909"
        set(value) { sp.edit().putString(KEY_VERSION, value).apply() }

    /** Permitir voces que necesitan internet (suelen sonar mejor). */
    var allowNetworkVoices: Boolean
        get() = sp.getBoolean(KEY_NETWORK_VOICES, false)
        set(value) { sp.edit().putBoolean(KEY_NETWORK_VOICES, value).apply() }

    /** 0 = seguir al sistema, 1 = claro, 2 = oscuro. */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME, 0).coerceIn(0, 2)
        set(value) { sp.edit().putInt(KEY_THEME, value.coerceIn(0, 2)).apply() }

    /** `true` la primera vez que se abre la app (para mostrar la bienvenida). */
    var isFirstRun: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN, true)
        set(value) { sp.edit().putBoolean(KEY_FIRST_RUN, value).apply() }

    /**
     * Leer con la voz IA grabada donde haya audio instalado. Donde no lo hay,
     * lee la voz del teléfono igual que siempre.
     */
    var vozIaActiva: Boolean
        get() = sp.getBoolean(KEY_IA_ACTIVA, true)
        set(value) { sp.edit().putBoolean(KEY_IA_ACTIVA, value).apply() }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.5f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 1.8f

        private const val KEY_BOOK = "last_book"
        private const val KEY_CHAPTER = "last_chapter"
        private const val KEY_VERSE = "last_verse"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "speech_pitch"
        private const val KEY_FONT = "font_scale"
        private const val KEY_ANNOUNCE_CHAPTER = "announce_chapter"
        private const val KEY_ANNOUNCE_VERSE = "announce_verse"
        private const val KEY_AUTO_CONTINUE = "auto_continue"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_ENGINE = "tts_engine"
        private const val KEY_VOICE = "tts_voice"
        private const val KEY_NETWORK_VOICES = "allow_network_voices"
        private const val KEY_VERSION = "bible_version"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_IA_ACTIVA = "voz_ia_activa"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
