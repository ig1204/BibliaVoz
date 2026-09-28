package com.bibliavoz.app.voz

/**
 * Parte un capítulo (o una lectura de la misa) en los tramos que la voz IA
 * graba de una sola vez.
 *
 * Cada tramo es una toma distinta de la voz, y entre toma y toma la entonación
 * puede cambiar un poco: cuanto más largo el tramo, menos costuras y más fluida
 * la lectura. Como el audio va grabado en el teléfono, no hay que esperar a
 * nada para empezar, así que los tramos pueden ser largos:
 *
 * - Rondan las [OBJETIVO] letras (alrededor de un minuto de voz) y se cortan
 *   donde termina una frase, para no partir la entonación por la mitad.
 * - Nunca queda al final un tramo diminuto: se une al anterior.
 *
 * El reparto es SIEMPRE el mismo para el mismo texto.
 */
object Segmentador {

    /** Tamaño habitual de un tramo: alrededor de un minuto de voz. */
    const val OBJETIVO = 900

    /** Por encima de esto se corta aunque la frase no haya terminado. */
    const val MAXIMO = 1_500

    private const val MAX_VERSOS = 30
    private const val MAX_VERSOS_ABSOLUTO = 45

    /** Versículos [desde]..[hasta] (índices base 0, ambos incluidos). */
    data class Tramo(val desde: Int, val hasta: Int) {
        operator fun contains(verso: Int): Boolean = verso in desde..hasta
    }

    fun segmentar(versos: List<String>): List<Tramo> {
        if (versos.isEmpty()) return emptyList()
        val largo = IntArray(versos.size) { versos[it].trim().let { v -> if (v.isEmpty()) 0 else v.length + 1 } }
        // Letras que quedan desde cada versículo hasta el final.
        val restante = IntArray(versos.size + 1)
        for (i in versos.indices.reversed()) restante[i] = restante[i + 1] + largo[i]

        val tramos = ArrayList<Tramo>()
        var inicio = 0
        var letras = 0

        for (i in versos.indices) {
            letras += largo[i]
            val cuenta = i - inicio + 1
            val cierra = cierraFrase(versos[i].trim())
            val siguiente = largo.getOrElse(i + 1) { 0 }
            val detras = restante[i + 1]

            var cortar = i == versos.lastIndex ||
                (letras >= OBJETIVO && cierra) ||
                // Si el siguiente ya no cabe, mejor cortar ahora, en un final de frase.
                (cierra && letras >= OBJETIVO / 2 && letras + siguiente > MAXIMO) ||
                (cierra && cuenta >= MAX_VERSOS) ||
                letras >= MAXIMO ||
                cuenta >= MAX_VERSOS_ABSOLUTO

            // Lo que queda detrás es tan poco que sonaría suelto: va con este tramo.
            if (cortar && i < versos.lastIndex && detras < OBJETIVO / 4 && letras + detras <= MAXIMO) {
                cortar = false
            }
            // Los huecos de numeración del final no forman un tramo propio: sería un tramo mudo.
            if (cortar && i < versos.lastIndex && detras == 0) cortar = false

            if (cortar) {
                tramos.add(Tramo(inicio, i))
                inicio = i + 1
                letras = 0
            }
        }
        return tramos
    }

    /** Final de frase, o al menos una pausa fuerte (punto y coma). Los dos puntos no: detrás suele hablar alguien. */
    private fun cierraFrase(verso: String): Boolean {
        val ultimo = verso.lastOrNull() ?: return false
        return ultimo in ".!?”»’);"
    }
}
