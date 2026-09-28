package com.bibliavoz.app.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.json.JSONObject
import org.junit.Test
import java.io.File

class AnunciosTest {

    @Test
    fun `los libros con numero se nombran como se dicen`() {
        assertEquals("Primero de Reyes", Anuncios.nombreHablado("1 Reyes"))
        assertEquals("Segundo de Samuel", Anuncios.nombreHablado("2 Samuel"))
        assertEquals("Primero de Macabeos", Anuncios.nombreHablado("1 Macabeos"))
        assertEquals("Segunda de Corintios", Anuncios.nombreHablado("2 Corintios"))
        assertEquals("Tercera de Juan", Anuncios.nombreHablado("3 Juan"))
        assertEquals("Génesis", Anuncios.nombreHablado("Génesis"))
    }

    @Test
    fun `anuncio de capitulo`() {
        assertEquals("Juan, capítulo once.", Anuncios.capitulo("Juan", 11))
        assertEquals("Primera de Corintios, capítulo trece.", Anuncios.capitulo("1 Corintios", 13))
        assertEquals("Salmo veintitrés.", Anuncios.capitulo("Salmos", 23))
        assertEquals("Salmo ciento diecinueve.", Anuncios.capitulo("Salmos", 119))
    }

    @Test
    fun `lecturas de la misa con su formula`() {
        assertEquals(
            "Evangelio. Lectura del santo Evangelio según san Marcos.",
            Anuncios.lectura("Evangelio", "Marcos", 16),
        )
        assertEquals(
            "Primera lectura. Lectura del libro de los Hechos de los Apóstoles.",
            Anuncios.lectura("Primera lectura", "Hechos", 22),
        )
        assertEquals(
            "Primera lectura. Lectura de la segunda carta del apóstol san Pablo a Timoteo.",
            Anuncios.lectura("Primera lectura", "2 Timoteo", 1),
        )
        assertEquals(
            "Salmo responsorial. Salmo ciento diecisiete.",
            Anuncios.lectura("Salmo responsorial", "Salmos", 117),
        )
        assertEquals(
            "Primera lectura. Lectura del libro del profeta Isaías.",
            Anuncios.lectura("Primera lectura", "Isaías", 55),
        )
        assertEquals(
            "Segunda lectura. Lectura de la primera carta del apóstol san Juan.",
            Anuncios.lectura("Segunda lectura", "1 Juan", 4),
        )
        assertEquals(
            "Primera lectura. Lectura del segundo libro de los Macabeos.",
            Anuncios.lectura("Primera lectura", "2 Macabeos", 7),
        )
        assertEquals("Evangelio.", Anuncios.lectura("Evangelio", "", 0))
    }

    @Test
    fun `todos los libros de las dos biblias tienen formula de lectura`() {
        for (dir in listOf("bible", "bible-cat")) {
            val index = JSONObject(File("src/main/assets/$dir/index.json").readText(Charsets.UTF_8))
            val books = index.getJSONArray("books")
            for (i in 0 until books.length()) {
                val nombre = books.getJSONObject(i).getString("name")
                if (nombre == "Salmos") continue
                assertNotNull("Sin fórmula para «$nombre» ($dir)", Anuncios.formula(nombre))
            }
        }
    }
}
