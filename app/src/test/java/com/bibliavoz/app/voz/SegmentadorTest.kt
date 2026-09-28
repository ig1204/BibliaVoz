package com.bibliavoz.app.voz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SegmentadorTest {

    @Test
    fun `tramos de un minuto cortados en final de frase y sin colas sueltas`() {
        val versos = List(60) { "Este es el versículo número ${it + 1} y termina con punto." }
        val tramos = Segmentador.segmentar(versos)
        assertEquals(0, tramos.first().desde)
        assertEquals(versos.lastIndex, tramos.last().hasta)
        for (t in tramos) {
            val letras = versos.subList(t.desde, t.hasta + 1).sumOf { it.length + 1 }
            assertTrue("Tramo de $letras letras", letras <= Segmentador.MAXIMO)
        }
        val ultimo = versos.subList(tramos.last().desde, tramos.last().hasta + 1).sumOf { it.length + 1 }
        assertTrue("El último tramo quedó suelto: $ultimo letras", ultimo >= Segmentador.OBJETIVO / 4)
    }

    @Test
    fun `una frase que sigue en el versiculo siguiente no se corta`() {
        val versos = List(40) { if (it % 2 == 0) "La frase empieza en este versículo largo, y sigue," else "y termina en este otro." }
        for (t in Segmentador.segmentar(versos)) {
            assertTrue("Cortó a media frase en el versículo ${t.hasta + 1}", versos[t.hasta].endsWith("."))
        }
    }

    @Test
    fun `un capitulo vacio no tiene tramos`() {
        assertTrue(Segmentador.segmentar(emptyList()).isEmpty())
    }

    @Test
    fun `en toda la Biblia los tramos cubren cada versiculo una sola vez`() {
        for (dir in listOf("bible", "bible-cat")) {
            val carpeta = File("src/main/assets/$dir")
            val books = JSONObject(File(carpeta, "index.json").readText(Charsets.UTF_8)).getJSONArray("books")
            var total = 0
            var letras = 0L
            var maximo = 0
            var primeros = 0L
            var capitulos = 0
            for (i in 0 until books.length()) {
                val n = books.getJSONObject(i).getInt("n")
                val chapters = JSONObject(File(carpeta, "$n.json").readText(Charsets.UTF_8)).getJSONArray("chapters")
                for (c in 0 until chapters.length()) {
                    val arr = chapters.getJSONArray(c)
                    val versos = (0 until arr.length()).map { arr.getString(it) }
                    val tramos = Segmentador.segmentar(versos)
                    capitulos++
                    var esperado = 0
                    tramos.forEachIndexed { t, tramo ->
                        assertEquals("Hueco o solape en $dir $n:${c + 1}", esperado, tramo.desde)
                        assertTrue(tramo.hasta >= tramo.desde)
                        esperado = tramo.hasta + 1
                        val largo = versos.subList(tramo.desde, tramo.hasta + 1).sumOf { it.length + 1 }
                        val sinUltimo = largo - versos[tramo.hasta].length - 1
                        // Solo se pasa del máximo por culpa del último versículo añadido.
                        assertTrue("Tramo desbordado en $dir $n:${c + 1}", sinUltimo < Segmentador.MAXIMO)
                        total++
                        letras += largo
                        maximo = maxOf(maximo, largo)
                        if (t == 0) primeros += largo
                    }
                    assertEquals("Faltan versículos en $dir $n:${c + 1}", versos.size, esperado)
                }
            }
            println("== $dir: $total tramos, media ${letras / total} letras, máximo $maximo; primer tramo: media ${primeros / capitulos} letras")
        }
    }
}
