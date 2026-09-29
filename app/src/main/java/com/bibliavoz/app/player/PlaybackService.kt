package com.bibliavoz.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.bibliavoz.app.MainActivity
import com.bibliavoz.app.R
import com.bibliavoz.app.data.BibleRepository
import com.bibliavoz.app.data.Chapter
import com.bibliavoz.app.data.Position
import com.bibliavoz.app.data.Prefs
import com.bibliavoz.app.liturgia.ColaLecturas
import com.bibliavoz.app.liturgia.LecturaConTexto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Servicio en primer plano que lee la Biblia en voz alta.
 *
 * Vive fuera de la Activity a propósito: así la lectura continúa con la
 * pantalla apagada o con la app en segundo plano, que es justamente para lo
 * que sirve la aplicación.
 *
 * Encadenado: se habla un versículo a la vez y, cuando el motor avisa que
 * terminó ([UtteranceProgressListener.onDone]), se avanza al siguiente. Al
 * acabar el capítulo sigue con el siguiente, y al acabar el libro, con el
 * siguiente libro.
 *
 * Dos voces: la **voz IA** ya grabada e instalada en el teléfono ([AudioLocal],
 * [HablanteIa]) para los capítulos que la tengan, y el motor de texto a voz del
 * teléfono para el resto. Ninguna de las dos necesita internet. Si un archivo
 * de la voz IA no se puede reproducir, la lectura sigue con la del teléfono
 * desde el mismo versículo, sin quedarse muda.
 */
class PlaybackService : Service() {

    private lateinit var repo: BibleRepository
    private lateinit var prefs: Prefs
    private lateinit var audioManager: AudioManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat

    private var tts: TextToSpeech? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var currentChapter: Chapter? = null
    private var position = Position()
    private var playing = false
    private var engineReady = false

    /**
     * El motor ya respondió y no puede hablar. Distinto de `!engineReady`, que
     * también es cierto mientras el motor todavía está arrancando.
     */
    private var engineFailed = false

    /** Id de la frase que se está pronunciando ahora mismo. */
    private var activeUtteranceId: String? = null
    private var utteranceSeq = 0L

    /** Fallos de síntesis seguidos, sin ningún versículo leído en medio. */
    private var consecutiveErrors = 0

    /** Se perdió el foco de audio temporalmente y hay que reanudar después. */
    private var resumeOnFocusGain = false

    private var sleepJob: Job? = null
    private var idleStopJob: Job? = null
    private var isForeground = false

    /** El servicio se está apagando: no volver a programar nada sobre él. */
    private var stopping = false

    /** Contador de cargas de capítulo: una carga vieja no debe pisar a una nueva. */
    private var chapterLoadSeq = 0L

    /** Tramo de texto que se está pronunciando de corrido. */
    private var currentBlock: SpeechBlock? = null

    // --- voz IA grabada ---
    private lateinit var audioLocal: AudioLocal
    private lateinit var vozIa: HablanteIa

    /** Tramo de voz IA que suena ahora, para saber qué versículo resaltar. */
    private var tramoIa: AudioLocal.Tramo? = null

    /** Lo que suena ahora es la voz IA (y no la del teléfono). */
    private var iaSonando = false

    /**
     * Archivos de la voz IA que fallaron: hasta el próximo ▶ esos tramos los lee
     * la voz del teléfono. Los demás siguen con la voz IA.
     */
    private val iaFallidos = HashSet<AudioLocal.Fuente>()

    /**
     * Versículo al que se saltó con la voz IA. El audio arranca un poco antes
     * (el margen de [HablanteIa]); mientras no llegue a este versículo, el
     * progreso no baja el resaltado. -1 = sin salto pendiente.
     */
    private var versoPedidoIa = -1

    /** La lectura espera a que arranque el motor del teléfono para poder seguir. */
    private var esperandoMotor = false

    /** Está registrado el aviso de "se desconectaron los audífonos". */
    private var escuchandoAudifonos = false

    // --- modo "lecturas de la misa" ---
    // Cuando hay lecturas cargadas, la voz recorre esa lista y se detiene al
    // acabar, en vez de seguir con el capítulo siguiente de la Biblia.
    private var lecturas: List<LecturaConTexto> = emptyList()
    private var lecturaIndex = 0
    private var lecturaVerso = 0

    /**
     * Título del día de esas lecturas, copiado al empezar: la pantalla puede
     * preparar otro día en [ColaLecturas] mientras estas suenan.
     */
    private var lecturasTitulo = ""
    private val enModoLecturas: Boolean get() = lecturas.isNotEmpty()

    /**
     * Varios versículos seguidos, pronunciados en una sola frase.
     *
     * Leer versículo a versículo mete un silencio en cada corte, y como los
     * versículos parten frases por la mitad, el resultado suena entrecortado.
     * Pronunciando el tramo entero la entonación es la natural del motor.
     *
     * [verseStarts] guarda en qué posición del texto empieza cada versículo, para
     * poder resaltar el correcto con los avisos de `onRangeStart`.
     */
    private class SpeechBlock(
        val startVerse: Int,
        val endVerse: Int,
        val text: String,
        val verseStarts: IntArray,
    )

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate() {
        super.onCreate()
        running = true
        repo = BibleRepository.get(this)
        prefs = Prefs.get(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        // Normalización sin entrada/salida: onCreate corre en el hilo principal
        // y dentro de la ventana de 5 s de startForegroundService(). El recorte
        // fino lo hace ensureChapterLoaded() cuando el capítulo ya está cargado.
        position = repo.normalizeCheap(prefs.lastPosition)

        mediaSession = MediaSessionCompat(this, "BibliaVoz").apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }

        audioLocal = AudioLocal(this)
        vozIa = HablanteIa(this, oyenteIa).apply { velocidad(prefs.speechRate) }
        scope.launch(Dispatchers.IO) { audioLocal.recargarSiCambio() }

        publishState()
        initTts()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() obliga a llamar a startForeground() en menos de
        // 5 segundos o el sistema mata la app, así que se hace siempre y de
        // primero, antes de procesar nada.
        promoteToForeground()

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> playDesdeControles()
            ACTION_PAUSE -> userPause()
            ACTION_TOGGLE -> if (playing) userPause() else playDesdeControles()
            ACTION_STOP -> stopEverything()
            ACTION_NEXT_VERSE -> pasoVerso(+1, intent.getBooleanExtra(EXTRA_DESDE_BIBLIA, false))
            ACTION_PREV_VERSE -> pasoVerso(-1, intent.getBooleanExtra(EXTRA_DESDE_BIBLIA, false))
            ACTION_NEXT_CHAPTER -> pasoCapitulo(+1, intent.getBooleanExtra(EXTRA_DESDE_BIBLIA, false))
            ACTION_PREV_CHAPTER -> pasoCapitulo(-1, intent.getBooleanExtra(EXTRA_DESDE_BIBLIA, false))
            ACTION_SEEK -> {
                val target = Position(
                    book = intent.getIntExtra(EXTRA_BOOK, position.book),
                    chapter = intent.getIntExtra(EXTRA_CHAPTER, position.chapter),
                    verse = intent.getIntExtra(EXTRA_VERSE, 0),
                )
                val autoPlay = intent.getBooleanExtra(EXTRA_AUTOPLAY, true)
                seekTo(target, autoPlay)
            }
            ACTION_SET_RATE -> {
                val rate = intent.getFloatExtra(EXTRA_VALUE, prefs.speechRate)
                prefs.speechRate = rate
                applyVoiceSettings()
                // Reiniciar el versículo actual para que el cambio se oiga ya.
                // La voz IA no lo necesita: cambia de velocidad sin cortarse.
                if (playing && !iaSonando) speakCurrentVerse()
                publishState()
            }
            ACTION_SET_PITCH -> {
                val pitch = intent.getFloatExtra(EXTRA_VALUE, prefs.pitch)
                prefs.pitch = pitch
                applyVoiceSettings()
                if (playing && !iaSonando) speakCurrentVerse()
                publishState()
            }
            ACTION_SET_VOICE -> {
                val engineChanged = intent.getBooleanExtra(EXTRA_ENGINE_CHANGED, false)
                if (engineChanged) {
                    // Cambiar de motor obliga a crear otro TextToSpeech: la
                    // instancia actual está atada al paquete con el que nació.
                    stopSpeaking()
                    runCatching { tts?.shutdown() }
                    tts = null
                    engineReady = false
                    engineFailed = false
                    // Si estaba sonando, se sigue en cuanto responda el motor nuevo.
                    esperandoMotor = playing
                    initTts()
                } else {
                    tts?.let { applySelectedVoice(it) }
                    if (playing && !iaSonando) speakCurrentVerse()
                }
                publishState()
            }
            ACTION_VOZ_IA -> {
                // Se activó o desactivó la voz IA: si está sonando, el cambio se
                // oye ya, desde el versículo actual.
                iaFallidos.clear()
                PlayerBus.update { it.copy(avisoVoz = null) }
                if (playing) {
                    stopSpeaking()
                    speakCurrentVerse()
                } else {
                    // Un tramo IA en pausa no debe reanudarse con la voz IA apagada.
                    stopSpeaking()
                }
                publishState()
            }
            ACTION_PLAY_LECTURAS -> {
                stopSpeaking()
                lecturas = ColaLecturas.items
                lecturasTitulo = if (lecturas.isEmpty()) "" else ColaLecturas.titulo
                lecturaIndex = intent.getIntExtra(EXTRA_LECTURA, 0).coerceIn(0, (lecturas.size - 1).coerceAtLeast(0))
                lecturaVerso = 0
                if (lecturas.isEmpty()) {
                    publishState()
                } else if (playing) {
                    speakCurrentVerse()
                    publishState()
                } else {
                    play()
                }
            }
            ACTION_SALIR_LECTURAS -> salirDeLecturas()
            ACTION_SLEEP_TIMER -> startSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0))
            else -> Unit
        }

        // Cualquier acción obliga a entrar en primer plano por el plazo de 5 s,
        // pero si no hay nada sonando (un cambio de velocidad, un seek en pausa)
        // se baja enseguida la prioridad en vez de quedarse ahí indefinidamente.
        when {
            stopping -> Unit
            playing -> cancelIdleStop()
            // Esperando recuperar el foco (una llamada): se sigue en primer plano
            // para poder reanudar solos, igual que en pause(abandonFocus = false).
            resumeOnFocusGain -> Unit
            else -> {
                demoteFromForeground()
                scheduleIdleStop()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        sleepJob?.cancel()
        idleStopJob?.cancel()
        dejarDeEscucharAudifonos()
        releaseWakeLock()
        abandonAudioFocus()
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
        vozIa.liberar()
        mediaSession.isActive = false
        mediaSession.release()
        // Sin esto, una notificación ya separada del primer plano sobreviviría
        // al servicio con botones que no responden a nada.
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        scope.cancel()
        PlayerBus.update { it.copy(isPlaying = false).sinMisa() }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si el usuario cierra la app deslizándola y no hay nada sonando,
        // no dejamos el servicio colgado.
        if (!playing) stopEverything()
        super.onTaskRemoved(rootIntent)
    }

    // ---------------------------------------------------------------- motor de voz

    private fun initTts() {
        val listener = TextToSpeech.OnInitListener { status -> handler.post { onTtsInit(status) } }
        val selected = prefs.ttsEngine
        tts = if (selected.isNullOrBlank()) {
            TextToSpeech(applicationContext, listener)
        } else {
            // Si el motor elegido ya no está instalado, TextToSpeech devuelve
            // ERROR en onInit y se cae al aviso de "sin motor" de la interfaz.
            TextToSpeech(applicationContext, listener, selected)
        }
    }

    /** Aplica la voz concreta que eligió el usuario, si sigue existiendo. */
    private fun applySelectedVoice(engine: TextToSpeech) {
        val wanted = prefs.voiceName
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        val chosen = when {
            !wanted.isNullOrBlank() -> voices.firstOrNull { it.name == wanted }
            else -> null
        } ?: bestSpanishVoice(voices)

        if (chosen != null) runCatching { engine.setVoice(chosen) }
    }

    /**
     * Mejor voz española disponible: primero la de más calidad y, a igualdad,
     * la que no necesite internet, porque la app está pensada para usarse sin datos.
     */
    private fun bestSpanishVoice(voices: Collection<android.speech.tts.Voice>): android.speech.tts.Voice? =
        voices.asSequence()
            .filter { it.locale.language.equals("es", ignoreCase = true) }
            .filterNot { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true }
            .filter { prefs.allowNetworkVoices || !it.isNetworkConnectionRequired }
            .sortedWith(
                compareByDescending<android.speech.tts.Voice> { it.quality }
                    .thenBy { it.isNetworkConnectionRequired }
            )
            .firstOrNull()

    private fun onTtsInit(status: Int) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            engineReady = false
            engineFailed = true
            PlayerBus.update {
                it.copy(engineReady = false, engineError = EngineError.NO_ENGINE)
            }
            // El usuario pudo pulsar play antes de saber que el motor falló:
            // hay que soltar wake lock y foco de audio, no solo la interfaz.
            // Si suena la voz IA, no hace falta el motor del teléfono para seguir.
            if (playing && !iaSonando && sinVozIaQueCubra()) pause()
            return
        }

        val spanishResult = selectSpanishVoice(engine)
        engineReady = spanishResult != null
        engineFailed = !engineReady

        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(utteranceListener)
        applySelectedVoice(engine)
        applyVoiceSettings()

        PlayerBus.update {
            it.copy(
                engineReady = engineReady,
                engineError = if (engineReady) null else EngineError.MISSING_SPANISH,
            )
        }

        // Si el usuario pidió reproducir antes de que el motor estuviera listo.
        // Con la voz IA la lectura ya arrancó sin esperarlo.
        if (playing && !iaSonando) {
            if (engineReady) speakCurrentVerse() else if (sinVozIaQueCubra()) pause()
        }
    }

    /**
     * El motor del teléfono no puede hablar: ¿hay que parar ya? No, si la voz IA
     * está activada y la lectura todavía no llegó a pedir el motor (el capítulo
     * se está cargando y quizá tenga voz IA). En ese caso decide
     * speakCurrentVerse() al terminar la carga: si no hay voz IA, pausa.
     */
    private fun sinVozIaQueCubra(): Boolean = esperandoMotor || !prefs.vozIaActiva

    /** Prueba variantes de español, de la más cercana a México a la más genérica. */
    private fun selectSpanishVoice(engine: TextToSpeech): Locale? {
        val candidates = listOf(
            Locale("es", "MX"),
            Locale("es", "US"),
            Locale("es", "419"),
            Locale("es", "ES"),
            Locale("es"),
        )
        for (locale in candidates) {
            val result = runCatching { engine.setLanguage(locale) }.getOrDefault(
                TextToSpeech.LANG_NOT_SUPPORTED
            )
            if (result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return locale
            }
        }
        return null
    }

    private fun applyVoiceSettings() {
        tts?.let { engine ->
            engine.setSpeechRate(prefs.speechRate)
            engine.setPitch(prefs.pitch)
        }
        // La voz IA solo cambia de velocidad: el tono deformaría la voz grabada.
        vozIa.velocidad(prefs.speechRate)
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            // Llega en un hilo del binder: hay que volver al hilo principal.
            handler.post {
                if (utteranceId != null && utteranceId == activeUtteranceId && playing) {
                    // Única señal fiable de que el motor sí está sintetizando.
                    consecutiveErrors = 0
                    advanceAfterVerse()
                }
            }
        }

        @Deprecated("Reemplazado por onError(String, Int)", ReplaceWith(""))
        override fun onError(utteranceId: String?) {
            handler.post { handleUtteranceError(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            handler.post { handleUtteranceError(utteranceId) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit

        /**
         * El motor avisa por qué parte del texto va leyendo. Con eso se resalta
         * el versículo correcto aunque el tramo abarque varios. Existe desde
         * Android 8; en versiones anteriores los tramos son de un solo versículo.
         */
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            handler.post {
                if (utteranceId != activeUtteranceId || !playing) return@post
                val block = currentBlock ?: return@post

                var index = 0
                for (i in block.verseStarts.indices) {
                    if (start >= block.verseStarts[i]) index = i else break
                }
                val verse = block.startVerse + index
                if (verse > block.endVerse) return@post
                if (enModoLecturas) {
                    // El tramo es de la lectura de la misa: se recuerda por dónde
                    // va para reanudar ahí, sin tocar la posición de la Biblia.
                    lecturaVerso = verse
                    return@post
                }
                if (verse != position.verse) {
                    position = position.copy(verse = verse)
                    savePosition()
                    publishState()
                }
            }
        }
    }

    private fun handleUtteranceError(utteranceId: String?) {
        if (utteranceId != activeUtteranceId) return

        consecutiveErrors++
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            // El motor falla en todos los versículos (voz de red sin conexión,
            // error de síntesis, motor caído...). Seguir saltando recorrería la
            // Biblia entera en silencio y borraría la posición del usuario.
            consecutiveErrors = 0
            PlayerBus.update { it.copy(engineError = EngineError.INIT_FAILED) }
            pause()
            return
        }

        // Un versículo suelto que falla no debe congelar la lectura: se salta.
        if (playing) advanceAfterVerse()
    }

    // ---------------------------------------------------------------- reproducción

    private fun play() {
        if (playing) return
        // Cada ▶ le da otra oportunidad a la voz IA si un archivo falló.
        iaFallidos.clear()
        if (engineFailed && !hayVozIa()) {
            // El motor ya confirmó que no puede hablar: no se toma wake lock ni
            // foco de audio, ni se deja una notificación de «reproduciendo».
            // El banner de la interfaz ya explica el porqué.
            publishState()
            updateNotification()
            scheduleIdleStop()
            return
        }
        if (!requestAudioFocus()) return

        playing = true
        consecutiveErrors = 0
        resumeOnFocusGain = false
        acquireWakeLock()
        escucharAudifonos()
        cancelIdleStop()
        publishState()
        updateNotification()

        // Se relee el manifiesto por si se copió audio nuevo desde la PC. Si el
        // capítulo no tiene voz IA y el motor del teléfono aún no está listo,
        // onTtsInit() arrancará la lectura.
        scope.launch {
            withContext(Dispatchers.IO) { audioLocal.recargarSiCambio() }
            if (playing) ensureChapterLoaded { speakCurrentVerse() }
        }
    }

    /**
     * ▶ de la notificación, la pantalla de bloqueo, los audífonos o «Seguir».
     * Si en la pausa la pantalla movió la posición guardada (se hojeó otro
     * capítulo), se sigue desde ahí, como dice «Continuar escuchando»; si no
     * cambió, se reanuda en la misma palabra.
     */
    private fun playDesdeControles() {
        if (!playing && !enModoLecturas) {
            val guardada = repo.normalizeCheap(prefs.lastPosition)
            if (guardada != position) {
                stopSpeaking()
                position = guardada
            }
        }
        play()
    }

    /** Hay voz IA instalada y activada, aunque quizá no para este capítulo. */
    private fun hayVozIa(): Boolean =
        prefs.vozIaActiva && (audioLocal.resumen.capitulos > 0 || audioLocal.resumen.lecturas > 0)

    /**
     * @param abandonFocus `false` cuando la pausa la causa una pérdida temporal
     * de foco (una llamada entrante). Hay que conservar el AudioFocusRequest
     * para recibir después el AUDIOFOCUS_GAIN y poder reanudar solos.
     */
    private fun pause(abandonFocus: Boolean = true) {
        if (!playing) return
        playing = false
        activeUtteranceId = null
        esperandoMotor = false
        runCatching { tts?.stop() }
        // La voz IA se pausa de verdad: al volver sigue en la misma palabra.
        vozIa.pausar()
        releaseWakeLock()
        // Sin savePosition(): lo que suena ya se guardó versículo a versículo, y
        // guardar aquí pisaría la posición que la pantalla guardó mientras tanto
        // (por ejemplo, un capítulo elegido mientras sonaba la misa).
        publishState()
        if (abandonFocus) {
            resumeOnFocusGain = false
            dejarDeEscucharAudifonos()
            abandonAudioFocus()
            demoteFromForeground()
            scheduleIdleStop()
        } else {
            // El aviso de los audífonos sigue puesto: si se desconectan durante
            // la llamada, no hay que reanudar después por el altavoz.
            // Se mantiene en primer plano: así, al recuperar el foco, se sigue
            // hablando sin volver a llamar a startForeground() desde segundo
            // plano, que está restringido desde Android 12.
            updateNotification()
        }
    }

    /** Pausa pedida por el usuario: suelta el foco y anula cualquier reanudación pendiente. */
    private fun userPause() {
        if (playing) {
            pause(abandonFocus = true)
        } else {
            resumeOnFocusGain = false
            dejarDeEscucharAudifonos()
            abandonAudioFocus()
            demoteFromForeground()
            scheduleIdleStop()
        }
    }

    /**
     * Al desconectar los audífonos (cable o Bluetooth) Android avisa con
     * ACTION_AUDIO_BECOMING_NOISY: se pausa, para no seguir por el altavoz.
     */
    private val audifonosReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) userPause()
        }
    }

    /** Solo mientras suena (o espera reanudar tras una llamada). */
    private fun escucharAudifonos() {
        if (escuchandoAudifonos) return
        escuchandoAudifonos = runCatching {
            // Lo envía el sistema; EXPORTED para recibirlo igual en todas las versiones.
            ContextCompat.registerReceiver(
                this,
                audifonosReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
    }

    private fun dejarDeEscucharAudifonos() {
        if (!escuchandoAudifonos) return
        escuchandoAudifonos = false
        runCatching { unregisterReceiver(audifonosReceiver) }
    }

    private fun stopEverything() {
        stopping = true
        playing = false
        resumeOnFocusGain = false
        activeUtteranceId = null
        runCatching { tts?.stop() }
        vozIa.detener()
        sleepJob?.cancel()
        sleepJob = null
        // La misa que había en memoria se pierde con el servicio: que la
        // pantalla no ofrezca «Seguir escuchando» algo que ya no se puede seguir.
        PlayerBus.update { it.copy(isPlaying = false, sleepTimerEndsAt = 0L).sinMisa() }
        dejarDeEscucharAudifonos()
        releaseWakeLock()
        abandonAudioFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    /**
     * Corta la voz en curso y olvida su id, para que un `onDone` tardío del
     * versículo anterior no avance sobre una posición que ya cambió.
     */
    private fun stopSpeaking() {
        activeUtteranceId = null
        currentBlock = null
        tramoIa = null
        iaSonando = false
        versoPedidoIa = -1
        esperandoMotor = false
        runCatching { tts?.stop() }
        vozIa.detener()
    }

    /** Carga el capítulo de [position] si aún no está en memoria, y luego ejecuta [then]. */
    private fun ensureChapterLoaded(then: () -> Unit) {
        val loaded = currentChapter
        if (loaded != null &&
            loaded.bookNumber == position.book &&
            loaded.chapterNumber == position.chapter
        ) {
            then()
            return
        }
        val token = ++chapterLoadSeq
        scope.launch {
            val chapter = withContext(Dispatchers.IO) {
                audioLocal.recargarSiCambio()
                repo.chapter(position.book, position.chapter)
            }
            // Si mientras se cargaba se pidió otro capítulo, esta carga ya no
            // vale: dejarla pasar pisaría la posición con el capítulo viejo.
            if (token != chapterLoadSeq) return@launch
            currentChapter = chapter
            // Alinear la posición con el capítulo que de verdad se cargó:
            // repo.chapter() recorta libro y capítulo, y si eso no se refleja
            // aquí, la guarda de arriba nunca acierta y se recarga en bucle.
            position = Position(
                book = chapter.bookNumber,
                chapter = chapter.chapterNumber,
                verse = position.verse.coerceIn(0, chapter.verses.lastIndex.coerceAtLeast(0)),
            )
            publishState()
            updateNotification()
            then()
        }
    }

    private fun speakCurrentVerse() {
        if (enModoLecturas) {
            speakLecturaActual()
            return
        }
        if (!playing) return
        esperandoMotor = false
        // Se renueva el plazo del wake lock en cada tramo: si no, caducaría a
        // las 3 horas de escucha seguida.
        acquireWakeLock()

        val chapter = currentChapter
        if (chapter == null || chapter.bookNumber != position.book ||
            chapter.chapterNumber != position.chapter
        ) {
            ensureChapterLoaded { speakCurrentVerse() }
            return
        }

        val tramosIa = if (prefs.vozIaActiva) {
            audioLocal.capitulo(chapter.bookNumber, chapter.chapterNumber, chapter.verses.size)
        } else {
            null
        }
        val tramo = tramosIa?.firstOrNull { position.verse in it }?.takeIf { it.fuente !in iaFallidos }
        if (tramo != null) {
            hablarConIa("ia-${chapter.bookNumber}-${chapter.chapterNumber}", tramo, position.verse)
            savePosition()
            publishState()
            updateNotification()
            return
        }

        iaSonando = false
        val engine = tts ?: return
        if (!engineReady) {
            // Sin motor del teléfono y sin voz IA para este capítulo: no hay con qué
            // leerlo. Si el motor aún está arrancando, onTtsInit() seguirá.
            if (engineFailed) pause() else esperandoMotor = true
            return
        }

        val block = buildBlock(chapter, position.verse)
        if (block == null) {
            advanceAfterVerse()
            return
        }
        currentBlock = block

        val id = "b-${position.book}-${position.chapter}-${block.startVerse}-${utteranceSeq++}"
        activeUtteranceId = id

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }

        // QUEUE_FLUSH descarta lo pendiente: evita que se solapen tramos si el
        // usuario salta rápido de un sitio a otro.
        val result = engine.speak(block.text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            PlayerBus.update { it.copy(engineError = EngineError.INIT_FAILED) }
            pause()
            return
        }

        savePosition()
        publishState()
        updateNotification()
    }

    /**
     * Arma el tramo que se pronunciará de corrido a partir de [from].
     *
     * Se corta por número de versículos o por longitud, lo que llegue antes, para
     * que el motor no tarde en empezar a hablar. Si el sistema no sabe avisar por
     * dónde va leyendo, el tramo se reduce a un solo versículo: sin ese aviso no
     * se podría resaltar el versículo correcto en la pantalla.
     */
    private fun buildBlock(chapter: Chapter, from: Int): SpeechBlock? {
        val cabecera = if (from == 0 && prefs.announceChapter) {
            "${chapter.bookName}, capítulo ${chapter.chapterNumber}. "
        } else {
            null
        }
        return buildBlockFrom(chapter.verses, from, cabecera, prefs.announceVerseNumbers)
    }

    /**
     * Arma un tramo hablado a partir de una lista cualquiera de versículos, sea
     * un capítulo de la Biblia o una lectura de la misa.
     */
    private fun buildBlockFrom(
        verses: List<String>,
        from: Int,
        header: String?,
        numerarVersiculos: Boolean,
    ): SpeechBlock? {
        if (from < 0 || from > verses.lastIndex) return null

        val trackable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val maxVerses = if (trackable && !numerarVersiculos) MAX_VERSES_PER_BLOCK else 1

        val builder = StringBuilder()
        val starts = ArrayList<Int>()

        if (!header.isNullOrBlank()) builder.append(header)

        var index = from
        while (index <= verses.lastIndex) {
            if (index > from) {
                if (index - from >= maxVerses) break
                if (builder.length >= MAX_BLOCK_CHARS) break
                builder.append(' ')
            }
            starts.add(builder.length)
            if (numerarVersiculos) {
                builder.append("Versículo ").append(index + 1).append(". ")
            }
            builder.append(verses[index])
            index++
        }

        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
        val text = builder.toString().let { if (it.length > max) it.take(max) else it }
        return SpeechBlock(from, index - 1, text, starts.toIntArray())
    }

    // ---------------------------------------------------------- lecturas de la misa

    private fun speakLecturaActual() {
        if (!playing) return
        esperandoMotor = false
        acquireWakeLock()

        val lectura = lecturas.getOrNull(lecturaIndex)
        if (lectura == null) {
            salirDeLecturas()
            return
        }
        if (lectura.versiculos.isEmpty()) {
            avanzarLectura()
            return
        }

        val tramosIa = if (prefs.vozIaActiva) {
            audioLocal.lectura(lectura.clave, lectura.versiculos.size)
        } else {
            null
        }
        val tramo = tramosIa?.firstOrNull { lecturaVerso in it }?.takeIf { it.fuente !in iaFallidos }
        if (tramo != null) {
            hablarConIa("il-$lecturaIndex", tramo, lecturaVerso)
            publishState()
            updateNotification()
            return
        }

        iaSonando = false
        val engine = tts ?: return
        if (!engineReady) {
            if (engineFailed) pause() else esperandoMotor = true
            return
        }

        // Al empezar cada lectura se anuncia cuál es y de dónde viene, igual
        // que se hace en misa antes de proclamarla (y con las mismas palabras
        // que la voz IA: «Primera lectura del libro del profeta Daniel…»).
        val cabecera = if (lecturaVerso == 0) "${lectura.anuncio} " else null
        val block = buildBlockFrom(lectura.versiculos, lecturaVerso, cabecera, false)
        if (block == null) {
            avanzarLectura()
            return
        }
        currentBlock = block

        val id = "l-$lecturaIndex-$lecturaVerso-${utteranceSeq++}"
        activeUtteranceId = id
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }

        val result = engine.speak(block.text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            PlayerBus.update { it.copy(engineError = EngineError.INIT_FAILED) }
            pause()
            return
        }
        publishState()
        updateNotification()
    }

    private fun avanzarLectura() {
        val lectura = lecturas.getOrNull(lecturaIndex)
        val siguienteVerso = (currentBlock?.endVerse ?: lecturaVerso) + 1

        if (lectura != null && siguienteVerso <= lectura.versiculos.lastIndex) {
            lecturaVerso = siguienteVerso
            speakLecturaActual()
            return
        }
        if (lecturaIndex + 1 <= lecturas.lastIndex) {
            lecturaIndex++
            lecturaVerso = 0
            speakLecturaActual()
            return
        }
        // Se terminaron las lecturas del día: no se sigue con la Biblia.
        salirDeLecturas()
    }

    /** Se terminaron las lecturas (o se pidió salir): pausa y vuelve a la Biblia. */
    private fun salirDeLecturas() {
        pause()
        stopSpeaking()
        olvidarLecturas()
        publishState()
        updateNotification()
    }

    /**
     * Deja el modo lecturas sin tocar la reproducción: lo siguiente que suene
     * es la Biblia, desde su posición guardada.
     */
    private fun olvidarLecturas() {
        lecturas = emptyList()
        lecturaIndex = 0
        lecturaVerso = 0
        lecturasTitulo = ""
        ColaLecturas.limpiar()
    }

    /** ⏩/⏪ en las lecturas de la misa: otro versículo dentro de la misma lectura. */
    private fun pasoVersoLectura(delta: Int) {
        val lectura = lecturas.getOrNull(lecturaIndex) ?: return
        stopSpeaking()
        lecturaVerso = (lecturaVerso + delta).coerceIn(0, lectura.versiculos.lastIndex.coerceAtLeast(0))
        reiniciarLectura()
    }

    /** ⏭/⏮ en las lecturas de la misa: la lectura siguiente o la anterior. */
    private fun pasoLectura(delta: Int) {
        val destino = lecturaIndex + delta
        if (destino !in lecturas.indices) return
        stopSpeaking()
        lecturaIndex = destino
        lecturaVerso = 0
        reiniciarLectura()
    }

    /** Como [restartCurrent], para las lecturas: no guarda nada de la Biblia. */
    private fun reiniciarLectura() {
        publishState()
        updateNotification()
        if (playing) speakLecturaActual()
    }

    // ---------------------------------------------------------------- voz IA

    /**
     * Hace sonar un tramo de la voz IA grabada. [prefijo] identifica el
     * capítulo o la lectura; [verso], desde dónde empezar dentro del tramo.
     */
    private fun hablarConIa(prefijo: String, tramo: AudioLocal.Tramo, verso: Int) {
        val id = "$prefijo-${tramo.desde}-${utteranceSeq++}"
        activeUtteranceId = id
        // advanceAfterVerse() y avanzarLectura() siguen desde currentBlock.endVerse,
        // igual que con la voz del teléfono.
        currentBlock = SpeechBlock(tramo.desde, tramo.hasta, "", IntArray(0))
        tramoIa = tramo
        iaSonando = true
        versoPedidoIa = -1
        if (!vozIa.reanudar(id, tramo.fuente)) {
            val dentro = verso - tramo.desde
            val fraccion = if (dentro > 0) tramo.inicios.getOrElse(dentro) { 0f } else 0f
            // Antes de hablar(): el primer aviso de progreso llega dentro de esa llamada.
            if (fraccion > 0f) versoPedidoIa = verso
            vozIa.hablar(id, tramo.fuente, fraccion)
        }
    }

    private val oyenteIa = object : HablanteIa.Oyente {
        override fun onEmpezo(id: String) {
            if (id == activeUtteranceId) consecutiveErrors = 0
        }

        override fun onProgreso(id: String, fraccion: Float) {
            if (id != activeUtteranceId || !playing) return
            val tramo = tramoIa ?: return
            var indice = 0
            for (i in tramo.inicios.indices) {
                if (fraccion >= tramo.inicios[i]) indice = i else break
            }
            val verse = (tramo.desde + indice).coerceAtMost(tramo.hasta)
            // Tras saltar a un versículo el audio empieza un poco antes: no se
            // retrocede el resaltado hasta que llegue al pedido. Así tampoco
            // se quedan en el sitio varios ⏩ seguidos.
            if (versoPedidoIa >= 0) {
                if (verse < versoPedidoIa) return
                versoPedidoIa = -1
            }
            if (enModoLecturas) {
                // En las lecturas de la misa no se toca la posición de la Biblia:
                // se recuerda el versículo de la lectura, para reanudar ahí.
                lecturaVerso = verse
                return
            }
            if (verse != position.verse) {
                position = position.copy(verse = verse)
                savePosition()
                publishState()
            }
        }

        override fun onTermino(id: String) {
            if (id == activeUtteranceId && playing) advanceAfterVerse()
        }

        override fun onFallo(id: String) {
            if (id != activeUtteranceId) return
            // Un archivo dañado o a medio copiar: ese tramo sigue con la voz del
            // teléfono desde el mismo versículo; los demás, con la voz IA. El
            // archivo que falló se vuelve a probar al pulsar ▶.
            tramoIa?.let { iaFallidos.add(it.fuente) }
            iaSonando = false
            tramoIa = null
            versoPedidoIa = -1
            vozIa.detener()
            PlayerBus.update {
                it.copy(avisoVoz = "Un tramo de la voz IA no se pudo reproducir; sigo con la voz del teléfono.")
            }
            publishState()
            // Si el motor del teléfono no puede hablar, speakCurrentVerse() pausa;
            // si aún está arrancando, espera a onTtsInit().
            if (playing) speakCurrentVerse()
        }
    }

    /** Avanza al tramo siguiente al terminar de hablar, encadenando capítulos y libros. */
    private fun advanceAfterVerse() {
        if (enModoLecturas) {
            avanzarLectura()
            return
        }
        val chapter = currentChapter ?: return
        // Si la posición ya apunta a otro capítulo, su carga sigue en curso:
        // no se avanza, hablará el callback pendiente de ensureChapterLoaded.
        if (chapter.bookNumber != position.book || chapter.chapterNumber != position.chapter) return
        val nextVerse = (currentBlock?.endVerse ?: position.verse) + 1

        if (nextVerse <= chapter.verses.lastIndex) {
            position = position.copy(verse = nextVerse)
            speakCurrentVerse()
            return
        }

        val next = repo.nextChapter(position.book, position.chapter)

        if (!prefs.autoContinue) {
            // Se para al acabar el capítulo, pero ya en el versículo 1 del
            // siguiente: así ▶ sigue ahí en vez de repetir el final de este.
            // stopSpeaking() suelta el tramo terminado para que no se reanude.
            stopSpeaking()
            if (next != null) {
                position = Position(next.first, next.second, 0)
                savePosition()
            }
            pause()
            if (next != null) ensureChapterLoaded { }
            return
        }

        if (next == null) {
            // Fin de Apocalipsis: se terminó la Biblia.
            stopSpeaking()
            pause()
            return
        }
        position = Position(next.first, next.second, 0)
        ensureChapterLoaded { speakCurrentVerse() }
    }

    /**
     * ⏩/⏪. En las lecturas de la misa se mueve dentro de la lectura; si la
     * orden viene de la pantalla de la Biblia ([desdeBiblia]), se sale antes de
     * las lecturas y se mueve la Biblia.
     */
    private fun pasoVerso(delta: Int, desdeBiblia: Boolean) {
        if (enModoLecturas && desdeBiblia) {
            stopSpeaking()
            olvidarLecturas()
        }
        if (enModoLecturas) pasoVersoLectura(delta) else stepVerse(delta)
    }

    /** ⏭/⏮: igual que [pasoVerso], pero de lectura en lectura o de capítulo en capítulo. */
    private fun pasoCapitulo(delta: Int, desdeBiblia: Boolean) {
        if (enModoLecturas && desdeBiblia) {
            stopSpeaking()
            olvidarLecturas()
        }
        if (enModoLecturas) pasoLectura(delta) else stepChapter(delta)
    }

    private fun stepVerse(delta: Int) {
        val chapter = currentChapter
        if (chapter == null) {
            ensureChapterLoaded { stepVerse(delta) }
            return
        }
        stopSpeaking()
        val target = position.verse + delta
        when {
            target < 0 -> {
                val prev = repo.previousChapter(position.book, position.chapter)
                if (prev == null) {
                    position = position.copy(verse = 0)
                    restartCurrent()
                } else {
                    // Mismo control que ensureChapterLoaded(): una carga vieja no
                    // debe pisar a una nueva, ni al revés.
                    val token = ++chapterLoadSeq
                    scope.launch {
                        val prevChapter = withContext(Dispatchers.IO) {
                            repo.chapter(prev.first, prev.second)
                        }
                        if (token != chapterLoadSeq) return@launch
                        currentChapter = prevChapter
                        position = Position(prev.first, prev.second, prevChapter.verses.lastIndex.coerceAtLeast(0))
                        restartCurrent()
                    }
                }
            }
            target > chapter.verses.lastIndex -> {
                val next = repo.nextChapter(position.book, position.chapter)
                if (next == null) {
                    position = position.copy(verse = chapter.verses.lastIndex.coerceAtLeast(0))
                    restartCurrent()
                } else {
                    position = Position(next.first, next.second, 0)
                    ensureChapterLoaded { restartCurrent() }
                }
            }
            else -> {
                position = position.copy(verse = target)
                restartCurrent()
            }
        }
    }

    private fun stepChapter(delta: Int) {
        val target = if (delta > 0) {
            repo.nextChapter(position.book, position.chapter)
        } else {
            repo.previousChapter(position.book, position.chapter)
        } ?: return

        stopSpeaking()
        position = Position(target.first, target.second, 0)
        ensureChapterLoaded { restartCurrent() }
    }

    private fun seekTo(target: Position, autoPlay: Boolean) {
        // Elegir un sitio de la Biblia (el lector, «Continuar escuchando») sale
        // de las lecturas de la misa: si no, seguiría sonando la misa.
        val veniaDeLecturas = enModoLecturas
        if (veniaDeLecturas) {
            stopSpeaking()
            olvidarLecturas()
        }
        val destino = repo.normalizeCheap(target)
        if (autoPlay && !playing && !veniaDeLecturas && destino == position) {
            // ▶ en el mismo versículo donde se pausó: se reanuda en la misma
            // palabra (voz IA) en vez de repetir el versículo desde el principio.
            play()
            return
        }
        stopSpeaking()
        position = destino
        if (autoPlay && !playing) {
            play()
        } else {
            ensureChapterLoaded { restartCurrent() }
        }
    }

    /** Re-pronuncia el versículo actual si está sonando; si no, solo actualiza la interfaz. */
    private fun restartCurrent() {
        savePosition()
        publishState()
        updateNotification()
        if (playing) speakCurrentVerse()
    }

    private fun savePosition() {
        prefs.lastPosition = position
    }

    private fun publishState() {
        val chapter = currentChapter
        PlayerBus.update {
            it.copy(
                engineReady = engineReady,
                isPlaying = playing,
                position = position,
                bookName = nombreDelLibro(),
                verseCount = chapter?.verseCount ?: 0,
                speechRate = prefs.speechRate,
                pitch = prefs.pitch,
                enLecturas = enModoLecturas,
                lecturaIndex = lecturaIndex,
                lecturaTitulo = lecturas.getOrNull(lecturaIndex)?.titulo ?: "",
                lecturasTitulo = if (enModoLecturas) lecturasTitulo else "",
                vozIa = iaSonando,
            )
        }
        updateMediaSession()
    }

    /** Nombre del libro de [position], aunque el capítulo cargado sea de otro libro. */
    private fun nombreDelLibro(): String =
        currentChapter?.takeIf { it.bookNumber == position.book }?.bookName
            ?: runCatching { repo.book(position.book).name }.getOrDefault("")

    /**
     * Título y subtítulo de lo que suena, para la notificación y para la sesión
     * multimedia (la pantalla de bloqueo): la lectura de la misa o el versículo.
     */
    private fun textosDeLoQueSuena(): Pair<String, String> {
        val lectura = lecturas.getOrNull(lecturaIndex)
        if (lectura != null) {
            val subtitulo = if (lecturasTitulo.isBlank()) lectura.titulo else "${lectura.titulo} · $lecturasTitulo"
            return lectura.cita to subtitulo
        }
        return position.reference(nombreDelLibro()) to getString(R.string.translation_name)
    }

    // ---------------------------------------------------------------- temporizador

    private fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepJob = null
            PlayerBus.update { it.copy(sleepTimerEndsAt = 0L) }
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        PlayerBus.update { it.copy(sleepTimerEndsAt = endsAt) }
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            PlayerBus.update { it.copy(sleepTimerEndsAt = 0L) }
            userPause()
        }
    }

    /** Apaga el servicio si lleva un rato en pausa y nadie lo usa. */
    private fun scheduleIdleStop() {
        idleStopJob?.cancel()
        // Si se está esperando a recuperar el foco (una llamada, un aviso del
        // GPS...) no se apaga el servicio: tiene que seguir vivo para reanudar.
        if (resumeOnFocusGain) {
            idleStopJob = null
            return
        }
        idleStopJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (!playing && !resumeOnFocusGain) stopEverything()
        }
    }

    private fun cancelIdleStop() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    // ---------------------------------------------------------------- foco de audio

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Otra app tomó el audio para quedarse. userPause() cubre también el
            // caso de estar ya en pausa esperando reanudar: suelta el foco, baja
            // de primer plano y programa el cierre.
            AudioManager.AUDIOFOCUS_LOSS -> userPause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // La voz hablada no se entiende con el volumen bajado, así que
                // también en el caso "can duck" preferimos pausar y reanudar.
                val wasPlaying = playing
                pause(abandonFocus = false)
                resumeOnFocusGain = wasPlaying
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    play()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Se reutiliza la petición ya registrada (por ejemplo tras una pausa
            // por llamada): crear una nueva dejaría la anterior sin poder
            // abandonarse, porque abandonAudioFocusRequest identifica por objeto.
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .setWillPauseWhenDucked(true)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // ---------------------------------------------------------------- wake lock

    /** Toma el wake lock, o renueva su plazo si ya estaba tomado. */
    private fun acquireWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.let {
            // Sin conteo de referencias, volver a llamar a acquire() solo
            // reinicia la cuenta del plazo.
            runCatching { it.acquire(WAKE_LOCK_TIMEOUT_MS) }
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BibliaVoz::lectura").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    // ---------------------------------------------------------------- MediaSession

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = playDesdeControles()
        override fun onPause() = userPause()
        override fun onStop() = stopEverything()
        // Pantalla de bloqueo, audífonos, coche: en las lecturas de la misa pasan
        // de lectura en lectura, sin mover la posición de la Biblia.
        override fun onSkipToNext() = pasoCapitulo(+1, desdeBiblia = false)
        override fun onSkipToPrevious() = pasoCapitulo(-1, desdeBiblia = false)
    }

    private fun updateMediaSession() {
        // Lo mismo que la notificación: Android 13+ muestra esto, no la notificación.
        val (titulo, subtitulo) = textosDeLoQueSuena()
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titulo)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitulo)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    0L,
                    if (playing) 1f else 0f,
                )
                .build()
        )
    }

    // ---------------------------------------------------------------- notificación

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_playback_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, action.hashCode(), intent, flags)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Iconos de acción del sistema: siempre existen y se renderizan bien
        // en la notificación de cualquier fabricante.
        val playPauseIcon =
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel =
            getString(if (playing) R.string.action_pause else R.string.action_play)

        val (titulo, subtitulo) = textosDeLoQueSuena()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(subtitulo)
            .setContentIntent(contentIntent)
            .setDeleteIntent(actionIntent(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.action_prev_chapter), actionIntent(ACTION_PREV_CHAPTER))
            .addAction(android.R.drawable.ic_media_rew, getString(R.string.action_prev_verse), actionIntent(ACTION_PREV_VERSE))
            .addAction(playPauseIcon, playPauseLabel, actionIntent(ACTION_TOGGLE))
            .addAction(android.R.drawable.ic_media_ff, getString(R.string.action_next_verse), actionIntent(ACTION_NEXT_VERSE))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.action_next_chapter), actionIntent(ACTION_NEXT_CHAPTER))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(actionIntent(ACTION_STOP))
            )
            .build()
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        // Desde Android 12, volver a primer plano con la app en segundo plano
        // puede estar prohibido (ForegroundServiceStartNotAllowedException).
        // Mejor seguir leyendo con la notificación normal que cerrar la app.
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (ok) {
            isForeground = true
        } else {
            runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
        }
    }

    /** En pausa dejamos la notificación pero el servicio deja de ser "primer plano". */
    private fun demoteFromForeground() {
        if (!isForeground) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        isForeground = false
        updateNotification()
    }

    private fun updateNotification() {
        if (playing && !isForeground) {
            promoteToForeground()
            return
        }
        runCatching { notificationManager.notify(NOTIFICATION_ID, buildNotification()) }
    }

    companion object {
        const val ACTION_PLAY = "com.bibliavoz.app.action.PLAY"
        const val ACTION_PAUSE = "com.bibliavoz.app.action.PAUSE"
        const val ACTION_TOGGLE = "com.bibliavoz.app.action.TOGGLE"
        const val ACTION_STOP = "com.bibliavoz.app.action.STOP"
        const val ACTION_NEXT_VERSE = "com.bibliavoz.app.action.NEXT_VERSE"
        const val ACTION_PREV_VERSE = "com.bibliavoz.app.action.PREV_VERSE"
        const val ACTION_NEXT_CHAPTER = "com.bibliavoz.app.action.NEXT_CHAPTER"
        const val ACTION_PREV_CHAPTER = "com.bibliavoz.app.action.PREV_CHAPTER"
        const val ACTION_SEEK = "com.bibliavoz.app.action.SEEK"
        const val ACTION_SET_RATE = "com.bibliavoz.app.action.SET_RATE"
        const val ACTION_SET_PITCH = "com.bibliavoz.app.action.SET_PITCH"
        const val ACTION_SLEEP_TIMER = "com.bibliavoz.app.action.SLEEP_TIMER"
        const val ACTION_SET_VOICE = "com.bibliavoz.app.action.SET_VOICE"
        const val ACTION_PLAY_LECTURAS = "com.bibliavoz.app.action.PLAY_LECTURAS"
        const val ACTION_SALIR_LECTURAS = "com.bibliavoz.app.action.SALIR_LECTURAS"
        const val ACTION_VOZ_IA = "com.bibliavoz.app.action.VOZ_IA"

        const val EXTRA_BOOK = "book"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_VERSE = "verse"
        const val EXTRA_VALUE = "value"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_AUTOPLAY = "autoplay"
        const val EXTRA_ENGINE_CHANGED = "engine_changed"
        const val EXTRA_LECTURA = "lectura"
        const val EXTRA_DESDE_BIBLIA = "desde_biblia"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
        private const val IDLE_TIMEOUT_MS = 5 * 60_000L
        private const val MAX_CONSECUTIVE_ERRORS = 3

        /** Versículos que se pronuncian de corrido en una sola frase. */
        private const val MAX_VERSES_PER_BLOCK = 6
        private const val MAX_BLOCK_CHARS = 550
        private const val WAKE_LOCK_TIMEOUT_MS = 3 * 60 * 60_000L

        /** `true` mientras el servicio existe: evita despertarlo por ajustes menores. */
        @Volatile
        var running: Boolean = false
            private set

        private fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(action)
                .apply(configure)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun play(context: Context) = send(context, ACTION_PLAY)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun toggle(context: Context) = send(context, ACTION_TOGGLE)
        fun stop(context: Context) = send(context, ACTION_STOP)
        /*
         * ⏩⏪⏭⏮ pedidos desde una pantalla. Con [desdeBiblia] (el lector) se sale
         * de las lecturas de la misa y se mueve la Biblia; sin él (como la
         * notificación), en las lecturas se mueve dentro de ellas.
         */
        fun nextVerse(context: Context, desdeBiblia: Boolean = true) =
            send(context, ACTION_NEXT_VERSE) { putExtra(EXTRA_DESDE_BIBLIA, desdeBiblia) }
        fun previousVerse(context: Context, desdeBiblia: Boolean = true) =
            send(context, ACTION_PREV_VERSE) { putExtra(EXTRA_DESDE_BIBLIA, desdeBiblia) }
        fun nextChapter(context: Context, desdeBiblia: Boolean = true) =
            send(context, ACTION_NEXT_CHAPTER) { putExtra(EXTRA_DESDE_BIBLIA, desdeBiblia) }
        fun previousChapter(context: Context, desdeBiblia: Boolean = true) =
            send(context, ACTION_PREV_CHAPTER) { putExtra(EXTRA_DESDE_BIBLIA, desdeBiblia) }

        fun seek(context: Context, position: Position, autoPlay: Boolean) =
            send(context, ACTION_SEEK) {
                putExtra(EXTRA_BOOK, position.book)
                putExtra(EXTRA_CHAPTER, position.chapter)
                putExtra(EXTRA_VERSE, position.verse)
                putExtra(EXTRA_AUTOPLAY, autoPlay)
            }

        fun sleepTimer(context: Context, minutes: Int) =
            send(context, ACTION_SLEEP_TIMER) { putExtra(EXTRA_MINUTES, minutes) }

        /** Lee las lecturas ya preparadas en [ColaLecturas], empezando por [desde]. */
        fun leerLecturas(context: Context, desde: Int = 0) =
            send(context, ACTION_PLAY_LECTURAS) { putExtra(EXTRA_LECTURA, desde) }

        fun salirDeLecturas(context: Context) = send(context, ACTION_SALIR_LECTURAS)

        /**
         * Cambios de voz: si el servicio no existe basta con guardar la
         * preferencia; arrancarlo solo para esto encendería una notificación
         * de primer plano sin motivo.
         */
        fun setSpeechRate(context: Context, rate: Float) {
            Prefs.get(context).speechRate = rate
            if (running) send(context, ACTION_SET_RATE) { putExtra(EXTRA_VALUE, rate) }
            else PlayerBus.update { it.copy(speechRate = rate) }
        }

        fun setPitch(context: Context, pitch: Float) {
            Prefs.get(context).pitch = pitch
            if (running) send(context, ACTION_SET_PITCH) { putExtra(EXTRA_VALUE, pitch) }
            else PlayerBus.update { it.copy(pitch = pitch) }
        }

        /** La voz elegida se guarda siempre; el servicio solo se avisa si ya existe. */
        fun setVoice(context: Context, voiceName: String?) {
            Prefs.get(context).voiceName = voiceName
            if (running) send(context, ACTION_SET_VOICE) { putExtra(EXTRA_ENGINE_CHANGED, false) }
        }

        fun setEngine(context: Context, enginePackage: String?) {
            val prefs = Prefs.get(context)
            prefs.ttsEngine = enginePackage
            // Cada motor trae sus propias voces: la elegida ya no tiene sentido.
            prefs.voiceName = null
            if (running) send(context, ACTION_SET_VOICE) { putExtra(EXTRA_ENGINE_CHANGED, true) }
        }

        fun setAllowNetworkVoices(context: Context, allow: Boolean) {
            Prefs.get(context).allowNetworkVoices = allow
            if (running) send(context, ACTION_SET_VOICE) { putExtra(EXTRA_ENGINE_CHANGED, false) }
        }

        /**
         * Se activó o desactivó la voz IA (ya guardado en [Prefs]). Si el
         * servicio no existe no hay nada que cambiar: lo leerá al arrancar.
         */
        fun vozIaCambio(context: Context) {
            if (running) send(context, ACTION_VOZ_IA)
        }
    }
}
