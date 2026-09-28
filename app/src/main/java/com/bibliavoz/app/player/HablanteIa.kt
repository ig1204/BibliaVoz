package com.bibliavoz.app.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reproduce la voz IA ya grabada ([AudioLocal]) con MediaPlayer, un archivo por
 * tramo. No necesita internet: todo el audio está en el teléfono.
 *
 * Todo corre en el hilo principal, igual que el resto del servicio.
 */
class HablanteIa(private val oyente: Oyente) {

    interface Oyente {
        fun onEmpezo(id: String)

        /** Cada cuarto de segundo: por dónde va el tramo, de 0 a 1. */
        fun onProgreso(id: String, fraccion: Float)
        fun onTermino(id: String)

        /** El archivo no se pudo reproducir (dañado o incompleto). */
        fun onFallo(id: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var reproductor: MediaPlayer? = null
    private var idActual: String? = null
    private var archivoActual: File? = null
    private var pausado = false
    private var progreso: Job? = null
    private var velocidad = 1f
    private var tono = 1f

    /** Reproduce [archivo] desde [desdeFraccion] (0 = el principio). Corta lo que estuviera sonando. */
    fun hablar(id: String, archivo: File, desdeFraccion: Float) {
        detener()
        idActual = id
        archivoActual = archivo

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(archivo.absolutePath)
            mp.prepare()
        } catch (e: Exception) {
            runCatching { mp.release() }
            idActual = null
            archivoActual = null
            // Se avisa en la vuelta siguiente del bucle: quien llamó a hablar()
            // todavía no terminó de actualizar su estado.
            handler.post { oyente.onFallo(id) }
            return
        }

        mp.setOnCompletionListener {
            if (idActual == id && reproductor === mp) {
                pararProgreso()
                oyente.onTermino(id)
            }
        }
        mp.setOnErrorListener { _, _, _ ->
            if (reproductor === mp) {
                soltarReproductor()
                if (idActual == id) oyente.onFallo(id)
            }
            true
        }

        reproductor = mp
        pausado = false
        val duracion = mp.duration
        if (desdeFraccion > 0.01f && duracion > 0) {
            // Mejor oír el final del versículo anterior que perder la primera palabra.
            val ms = (duracion * desdeFraccion).toInt() - MARGEN_AL_SALTAR_MS
            if (ms > 0) saltar(mp, ms)
        }
        mp.start()
        aplicarVelocidad(mp)
        oyente.onEmpezo(id)
        seguirProgreso(id, mp)
    }

    /** Pausa conservando el punto exacto, para seguir con [reanudar]. */
    fun pausar() {
        pararProgreso()
        val mp = reproductor ?: return
        if (!pausado) {
            runCatching { mp.pause() }
            pausado = true
        }
    }

    /**
     * Sigue donde se pausó, si lo pausado es [archivo]. Devuelve `false` si no
     * hay nada que reanudar y hay que llamar a [hablar].
     */
    fun reanudar(id: String, archivo: File): Boolean {
        val mp = reproductor ?: return false
        if (!pausado || archivoActual != archivo) return false
        if (runCatching { mp.start() }.isFailure) {
            soltarReproductor()
            return false
        }
        idActual = id
        pausado = false
        aplicarVelocidad(mp)
        oyente.onEmpezo(id)
        seguirProgreso(id, mp)
        return true
    }

    fun detener() {
        pararProgreso()
        soltarReproductor()
        idActual = null
        archivoActual = null
    }

    fun velocidad(rate: Float, pitch: Float) {
        val cambio = rate != velocidad || pitch != tono
        velocidad = rate
        tono = pitch
        val mp = reproductor ?: return
        if (cambio && !pausado) aplicarVelocidad(mp, forzar = true)
    }

    fun liberar() {
        detener()
        scope.cancel()
    }

    private fun saltar(mp: MediaPlayer, ms: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(ms)
            }
        }
    }

    /**
     * Velocidad y tono se aplican después de start(): en algunos teléfonos,
     * cambiarlos con el reproductor preparado lo arranca por su cuenta.
     */
    private fun aplicarVelocidad(mp: MediaPlayer, forzar: Boolean = false) {
        if (!forzar && velocidad == 1f && tono == 1f) return
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(velocidad).setPitch(tono) }
    }

    private fun seguirProgreso(id: String, mp: MediaPlayer) {
        pararProgreso()
        progreso = scope.launch {
            while (isActive && reproductor === mp && !pausado) {
                val duracion = runCatching { mp.duration }.getOrDefault(0)
                val posicion = runCatching { mp.currentPosition }.getOrDefault(0)
                if (duracion > 0 && idActual == id) oyente.onProgreso(id, posicion.toFloat() / duracion)
                delay(PASO_PROGRESO_MS)
            }
        }
    }

    private fun pararProgreso() {
        progreso?.cancel()
        progreso = null
    }

    private fun soltarReproductor() {
        val mp = reproductor ?: return
        reproductor = null
        pausado = false
        runCatching { mp.stop() }
        runCatching { mp.release() }
    }

    private companion object {
        /**
         * Dónde empieza cada versículo dentro de un tramo es una estimación por
         * letras; en tramos de un minuto puede errar un segundo o así.
         */
        const val MARGEN_AL_SALTAR_MS = 900
        const val PASO_PROGRESO_MS = 250L
    }
}
