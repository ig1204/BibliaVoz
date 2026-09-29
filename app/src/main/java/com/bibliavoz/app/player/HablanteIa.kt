package com.bibliavoz.app.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reproduce la voz IA ya grabada ([AudioLocal]) con MediaPlayer, un archivo por
 * tramo. No necesita internet: todo el audio está en el teléfono.
 *
 * Todo corre en el hilo principal, igual que el resto del servicio.
 */
class HablanteIa(context: Context, private val oyente: Oyente) {

    interface Oyente {
        fun onEmpezo(id: String)

        /** Cada cuarto de segundo: por dónde va el tramo, de 0 a 1. */
        fun onProgreso(id: String, fraccion: Float)
        fun onTermino(id: String)

        /** El archivo no se pudo reproducir (dañado o incompleto). */
        fun onFallo(id: String)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var reproductor: MediaPlayer? = null
    private var idActual: String? = null
    private var fuenteActual: AudioLocal.Fuente? = null
    private var pausado = false

    /** El tramo llegó al final: un start() lo volvería a empezar desde el principio. */
    private var terminado = false
    private var progreso: Job? = null
    private var velocidad = 1f

    /** Reproduce [fuente] desde [desdeFraccion] (0 = el principio). Corta lo que estuviera sonando. */
    fun hablar(id: String, fuente: AudioLocal.Fuente, desdeFraccion: Float) {
        detener()
        idActual = id
        fuenteActual = fuente

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // Mantiene el procesador despierto mientras suena, también con la
            // pantalla apagada. Si el teléfono lo negara, el tramo suena igual.
            runCatching { mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK) }
            abrir(mp, fuente)
            mp.prepare()
        } catch (e: Exception) {
            runCatching { mp.release() }
            idActual = null
            fuenteActual = null
            // Se avisa en la vuelta siguiente del bucle: quien llamó a hablar()
            // todavía no terminó de actualizar su estado.
            handler.post { oyente.onFallo(id) }
            return
        }

        // Los avisos usan el id vigente y no el de esta llamada: reanudar() lo
        // cambia por el id nuevo, y con el viejo el final nunca llegaría.
        mp.setOnCompletionListener {
            val actual = idActual
            if (actual != null && reproductor === mp) {
                terminado = true
                pararProgreso()
                oyente.onTermino(actual)
            }
        }
        mp.setOnErrorListener { _, _, _ ->
            if (reproductor === mp) {
                val actual = idActual
                soltarReproductor()
                if (actual != null) oyente.onFallo(actual)
            }
            true
        }

        reproductor = mp
        pausado = false
        terminado = false
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
     * Sigue donde se pausó, si lo pausado es [fuente]. Devuelve `false` si no
     * hay nada que reanudar y hay que llamar a [hablar].
     */
    fun reanudar(id: String, fuente: AudioLocal.Fuente): Boolean {
        val mp = reproductor ?: return false
        // Un tramo que ya terminó no se reanuda: start() lo repetiría entero.
        if (!pausado || terminado || fuenteActual != fuente) return false
        if (runCatching { mp.start() }.isFailure) {
            soltarReproductor()
            return false
        }
        idActual = id
        pausado = false
        // Forzado: si en la pausa se volvió a la velocidad normal, el
        // reproductor seguiría con la de antes.
        aplicarVelocidad(mp, forzar = true)
        oyente.onEmpezo(id)
        seguirProgreso(id, mp)
        return true
    }

    fun detener() {
        pararProgreso()
        soltarReproductor()
        idActual = null
        fuenteActual = null
    }

    /**
     * El audio de la app se lee directamente del APK: va sin comprimir, así que
     * MediaPlayer lo abre por su posición dentro del archivo, sin copiarlo.
     */
    private fun abrir(mp: MediaPlayer, fuente: AudioLocal.Fuente) {
        val archivo = fuente.archivo
        if (archivo != null) {
            mp.setDataSource(archivo.absolutePath)
        } else {
            appContext.assets.openFd("${AudioLocal.CARPETA}/${fuente.nombre}").use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        }
    }

    /**
     * Solo la velocidad: el tono de la voz IA se queda siempre en 1, porque
     * cambiarlo deforma una voz humana grabada. El ajuste de tono es para la
     * voz del teléfono.
     */
    fun velocidad(rate: Float) {
        val cambio = rate != velocidad
        velocidad = rate
        val mp = reproductor ?: return
        // En pausa o con el tramo terminado no se toca: cambiar la velocidad lo
        // arrancaría. reanudar() la aplica al seguir.
        if (cambio && !pausado && !terminado) aplicarVelocidad(mp, forzar = true)
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
     * La velocidad se aplica después de start(): en algunos teléfonos,
     * cambiarla con el reproductor preparado lo arranca por su cuenta.
     */
    private fun aplicarVelocidad(mp: MediaPlayer, forzar: Boolean = false) {
        if (!forzar && velocidad == 1f) return
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(velocidad).setPitch(1f) }
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
        terminado = false
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
