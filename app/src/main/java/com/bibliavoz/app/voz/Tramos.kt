package com.bibliavoz.app.voz

/**
 * Versículos de un tramo «capítulo:versículo – capítulo:versículo» de un libro.
 *
 * La usan la app y el generador de audio de la PC: si resolvieran distinto,
 * los versículos del audio no coincidirían con los que resalta la pantalla.
 */
object Tramos {

    /**
     * @param capitulos el libro entero, un capítulo por lista.
     * Los números van en base 1, como en las citas. Los huecos de numeración
     * (versículos vacíos) se saltan; lo que se sale del libro se recorta.
     */
    fun versos(
        capitulos: List<List<String>>,
        desdeCapitulo: Int,
        desdeVerso: Int,
        hastaCapitulo: Int,
        hastaVerso: Int,
    ): List<String> {
        val out = ArrayList<String>()
        if (capitulos.isEmpty()) return out
        val primero = desdeCapitulo.coerceIn(1, capitulos.size)
        val ultimo = hastaCapitulo.coerceIn(primero, capitulos.size)
        for (numero in primero..ultimo) {
            val versos = capitulos[numero - 1]
            val inicio = if (numero == primero) desdeVerso else 1
            val fin = if (numero == ultimo) hastaVerso else versos.size
            var v = inicio.coerceAtLeast(1)
            val limite = fin.coerceAtMost(versos.size)
            while (v <= limite) {
                val texto = versos[v - 1]
                if (texto.isNotBlank()) out.add(texto)
                v++
            }
        }
        return out
    }
}
