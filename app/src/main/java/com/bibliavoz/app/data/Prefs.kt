package com.bibliavoz.app.data

import android.content.Context
import android.content.SharedPreferences

/** Preferencias del usuario y última posición de lectura. */
class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("biblia_voz", Context.MODE_PRIVATE)

    init {
        migrarNumeracionSbl()
    }

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

    /** Multiplicador del tamaño de letra en el lector. */
    var fontScale: Float
        get() = sp.getFloat(KEY_FONT, 1.0f).coerceIn(0.8f, 2.0f)
        set(value) { sp.edit().putFloat(KEY_FONT, value.coerceIn(0.8f, 2.0f)).apply() }

    /** Anunciar "Libro, capítulo N" al empezar cada capítulo. */
    var announceChapter: Boolean
        get() = sp.getBoolean(KEY_ANNOUNCE_CHAPTER, true)
        set(value) { sp.edit().putBoolean(KEY_ANNOUNCE_CHAPTER, value).apply() }

    /** Encadenar automáticamente con el capítulo siguiente. */
    var autoContinue: Boolean
        get() = sp.getBoolean(KEY_AUTO_CONTINUE, true)
        set(value) { sp.edit().putBoolean(KEY_AUTO_CONTINUE, value).apply() }

    /** Mantener la pantalla encendida mientras se escucha. */
    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) { sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply() }

    /** 0 = seguir al sistema, 1 = claro, 2 = oscuro. */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME, 0).coerceIn(0, 2)
        set(value) { sp.edit().putInt(KEY_THEME, value.coerceIn(0, 2)).apply() }

    /** `true` la primera vez que se abre la app (para mostrar la bienvenida). */
    var isFirstRun: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN, true)
        set(value) { sp.edit().putBoolean(KEY_FIRST_RUN, value).apply() }

    /**
     * Una sola vez: pasa la posición guardada de la numeración de la Reina-Valera
     * (66 libros) a la de la Santa Biblia Libre (73 libros, con los
     * deuterocanónicos intercalados). Quien actualiza desde la 2.2 tiene el libro
     * en numeración RV; sin esto, «Continuar escuchando» apuntaría a otro libro
     * (p. ej. Juan RV 43 → Sofonías SBL 43). La marca evita aplicarlo dos veces.
     */
    private fun migrarNumeracionSbl() {
        if (sp.getString(KEY_NUMERACION, null) == NUM_SBL) return
        val rv = sp.getInt(KEY_BOOK, 0)
        val sbl = rvASbl(rv)
        sp.edit().apply {
            if (sbl != rv) putInt(KEY_BOOK, sbl)
            putString(KEY_NUMERACION, NUM_SBL)
        }.apply()
    }

    /** Número de libro: Reina-Valera (66) → Santa Biblia Libre (73). */
    private fun rvASbl(rv: Int): Int = when (rv) {
        in 1..16 -> rv
        17 -> 19
        in 18..22 -> rv + 4
        in 23..25 -> rv + 6
        in 26..66 -> rv + 7
        else -> rv
    }

    companion object {
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.5f

        private const val KEY_BOOK = "last_book"
        private const val KEY_CHAPTER = "last_chapter"
        private const val KEY_VERSE = "last_verse"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_FONT = "font_scale"
        private const val KEY_ANNOUNCE_CHAPTER = "announce_chapter"
        private const val KEY_AUTO_CONTINUE = "auto_continue"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_NUMERACION = "numeracion"
        private const val NUM_SBL = "sbl"

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}
