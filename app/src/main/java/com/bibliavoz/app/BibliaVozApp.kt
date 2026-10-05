package com.bibliavoz.app

import android.app.Application
import com.bibliavoz.app.data.BibleRepository
import com.bibliavoz.app.data.Prefs
import com.bibliavoz.app.player.AudioLocal
import com.bibliavoz.app.player.PlayerBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BibliaVozApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs.get(this)

        // Sembrar el estado inicial para que la interfaz muestre la última
        // posición desde el primer frame, sin esperar al servicio.
        PlayerBus.update {
            it.copy(
                position = prefs.lastPosition,
                speechRate = prefs.speechRate,
            )
        }

        // Precalentar el índice de libros fuera del hilo principal: el primer
        // acceso lee y parsea index.json desde assets.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Que exista la carpeta de la voz IA, creada por la propia app: así
            // puede leer el audio que después se le copie por cable desde la PC.
            runCatching { getExternalFilesDir(AudioLocal.CARPETA) }
            runCatching {
                val repo = BibleRepository.get(this@BibliaVozApp)
                repo.books
                repo.translationName
                val position = prefs.lastPosition
                PlayerBus.update { state ->
                    state.copy(bookName = repo.book(position.book).name)
                }
                // Precalentar el libro de la última posición: así, cuando
                // arranque el servicio de lectura, ya lo encuentra en caché y no
                // tiene que parsearlo dentro de su ventana de arranque.
                repo.chapter(position.book, position.chapter)
            }
        }
    }
}
