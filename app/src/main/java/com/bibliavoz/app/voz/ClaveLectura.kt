package com.bibliavoz.app.voz

import java.security.MessageDigest

/**
 * Identifica una lectura de la misa para encontrar su audio grabado.
 *
 * Se calcula con lo que define la lectura en el leccionario (título, libro y
 * tramos de versículos), no con su texto: la app y el generador de la PC la
 * reconocen igual aunque una resolviera el texto de otra forma.
 */
object ClaveLectura {

    fun de(titulo: String, libro: Int, tramos: List<IntArray>): String {
        val firma = buildString {
            append(titulo.trim()).append('|').append(libro)
            for (t in tramos) {
                append('|')
                t.joinTo(this, ",")
            }
        }
        val bytes = MessageDigest.getInstance("SHA-1").digest(firma.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
