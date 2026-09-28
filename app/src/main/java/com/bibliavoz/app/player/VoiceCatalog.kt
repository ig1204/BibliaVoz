package com.bibliavoz.app.player

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Lista los motores y las voces de texto a voz que tiene el teléfono, y permite
 * escuchar una muestra antes de elegir.
 *
 * Usa una instancia propia de [TextToSpeech], separada de la del servicio: sirve
 * solo para el selector de la pantalla de ajustes y se apaga al cerrarla.
 *
 * Android no expone el sexo de una voz por ninguna API, así que el nombre técnico
 * es la única pista y no es fiable. Por eso el selector se apoya en el botón de
 * prueba: el usuario elige con el oído, no con una etiqueta que podría mentir.
 */
class VoiceCatalog(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var currentEngine: String? = null

    data class EngineOption(
        val packageName: String,
        val label: String,
    )

    data class VoiceOption(
        val name: String,
        val label: String,
        val localeLabel: String,
        val quality: Int,
        val requiresNetwork: Boolean,
        val notInstalled: Boolean,
    ) {
        /** Etiqueta corta de calidad para la interfaz. */
        val qualityLabel: String
            get() = when {
                quality >= Voice.QUALITY_VERY_HIGH -> "Muy alta"
                quality >= Voice.QUALITY_HIGH -> "Alta"
                quality >= Voice.QUALITY_NORMAL -> "Normal"
                else -> "Básica"
            }
    }

    /**
     * Arranca el motor [enginePackage] (o el del sistema si es `null`) y avisa
     * cuando ya se puede consultar el catálogo.
     */
    fun start(enginePackage: String?, onReady: (Boolean) -> Unit) {
        if (tts != null && currentEngine == enginePackage) {
            onReady(true)
            return
        }
        shutdown()
        currentEngine = enginePackage
        var notified = false
        val engine = if (enginePackage.isNullOrBlank()) {
            TextToSpeech(appContext, { status ->
                if (!notified) { notified = true; onReady(status == TextToSpeech.SUCCESS) }
            })
        } else {
            TextToSpeech(appContext, { status ->
                if (!notified) { notified = true; onReady(status == TextToSpeech.SUCCESS) }
            }, enginePackage)
        }
        tts = engine
    }

    /** Motores de texto a voz instalados en el teléfono. */
    fun engines(): List<EngineOption> =
        tts?.engines.orEmpty().map { EngineOption(it.name, it.label ?: it.name) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

    /**
     * Voces en español, de mejor a peor. Las que necesitan internet van al final
     * salvo que el usuario las haya permitido: la app está pensada para funcionar
     * sin conexión.
     */
    fun spanishVoices(allowNetwork: Boolean): List<VoiceOption> {
        val voices = runCatching { tts?.voices }.getOrNull().orEmpty()
        return voices
            .filter { it.locale.language.equals("es", ignoreCase = true) }
            .filter { allowNetwork || !it.isNetworkConnectionRequired }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .map { voice ->
                VoiceOption(
                    name = voice.name,
                    label = prettyLabel(voice),
                    localeLabel = regionLabel(voice.locale),
                    quality = voice.quality,
                    requiresNetwork = voice.isNetworkConnectionRequired,
                    notInstalled = false,
                )
            }
            .sortedWith(
                compareByDescending<VoiceOption> { it.quality }
                    .thenBy { it.requiresNetwork }
                    .thenBy { it.label }
            )
    }

    /** La mejor voz disponible, para usarla como valor por defecto. */
    fun bestVoice(allowNetwork: Boolean): VoiceOption? = spanishVoices(allowNetwork).firstOrNull()

    fun previewText(): String =
        "Así sonará la lectura. En el principio creó Dios los cielos y la tierra."

    fun preview(voiceName: String, rate: Float, pitch: Float) {
        val engine = tts ?: return
        val voice = runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
        if (voice != null) runCatching { engine.setVoice(voice) }
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(previewText(), TextToSpeech.QUEUE_FLUSH, params, "preview-$voiceName")
    }

    fun stopPreview() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
        currentEngine = null
    }

    /**
     * Los nombres técnicos ("es-es-x-eed-local") no dicen nada al usuario, así que
     * se muestran como "Español (España) · voz 3" y se distingue por el oído.
     */
    private fun prettyLabel(voice: Voice): String {
        val region = regionLabel(voice.locale)
        val variant = voice.name
            .substringAfterLast('-', "")
            .takeIf { it.isNotBlank() && it != "local" && it != "network" }
            ?: voice.name.split('-').getOrNull(3).orEmpty()
        return if (variant.isBlank()) region else "$region · $variant"
    }

    private fun regionLabel(locale: Locale): String = when (locale.country.uppercase(Locale.ROOT)) {
        "MX" -> "Español (México)"
        "ES" -> "Español (España)"
        "US" -> "Español (Estados Unidos)"
        "AR" -> "Español (Argentina)"
        "CO" -> "Español (Colombia)"
        "CL" -> "Español (Chile)"
        "PE" -> "Español (Perú)"
        "VE" -> "Español (Venezuela)"
        "" -> "Español"
        else -> "Español (${locale.country})"
    }
}
