package com.bibliavoz.app.voz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `lecturas de la misa con su formula, capitulo y versiculos`() {
        assertEquals(
            "Primera lectura del libro del profeta Daniel, capítulo siete, versículos del nueve al catorce.",
            Anuncios.lectura("Primera lectura", "Daniel", "Daniel 7, 9-14"),
        )
        assertEquals(
            "Lectura del santo Evangelio según san Marcos, capítulo dieciséis, versículos del quince al veinte.",
            Anuncios.lectura("Evangelio", "Marcos", "Marcos 16, 15-20"),
        )
        assertEquals(
            "Primera lectura del libro de los Hechos de los Apóstoles, capítulo veintidós, versículo tres.",
            Anuncios.lectura("Primera lectura", "Hechos", "Hechos 22, 3"),
        )
        assertEquals(
            "Primera lectura de la segunda carta del apóstol san Pablo a Timoteo, " +
                "capítulo uno, versículos del uno al tres y del seis al doce.",
            Anuncios.lectura("Primera lectura", "2 Timoteo", "2 Timoteo 1, 1-3. 6-12"),
        )
        assertEquals(
            "Segunda lectura de la primera carta del apóstol san Juan, capítulo cuatro, versículos del siete al diez.",
            Anuncios.lectura("Segunda lectura", "1 Juan", "1 Juan 4, 7-10"),
        )
        assertEquals(
            "Primera lectura del libro del Eclesiástico, capítulo treinta y seis, " +
                "versículos uno, del cuatro al cinco y del diez al diecisiete.",
            Anuncios.lectura("Primera lectura", "Eclesiástico", "Eclesiástico 36, 1. 4-5. 10-17"),
        )
        assertEquals(
            "Primera lectura del libro del profeta Isaías, capítulo sesenta y tres, versículos del dieciséis al " +
                "diecisiete y diecinueve, y capítulo sesenta y cuatro, versículos del dos al siete.",
            Anuncios.lectura("Primera lectura", "Isaías", "Isaías 63, 16-17. 19; 64, 2-7"),
        )
        // Pasajes que cruzan de capítulo (la Pasión) y libros de un solo capítulo.
        assertEquals(
            "Lectura del santo Evangelio según san Mateo, capítulo veintiséis, versículo catorce, " +
                "al capítulo veintisiete, versículo sesenta y seis.",
            Anuncios.lectura("Evangelio", "Mateo", "Mateo 26, 14 - 27, 66"),
        )
        assertEquals(
            "Primera lectura de la carta del apóstol san Pablo a Filemón, versículos del siete al veinte.",
            Anuncios.lectura("Primera lectura", "Filemón", "Filemón 7-20"),
        )
        assertEquals(
            "Evangelio de la procesión. Lectura del santo Evangelio según san Marcos, capítulo once, versículos del uno al diez.",
            Anuncios.lectura("Evangelio de la procesión", "Marcos", "Marcos 11, 1-10"),
        )
        // Ester C: el capítulo es una letra, así que solo se nombra el libro.
        assertEquals("Primera lectura del libro de Ester.", Anuncios.lectura("Primera lectura", "Ester", "Ester C, 12. 14-16"))
        assertEquals("Evangelio.", Anuncios.lectura("Evangelio", "", "Juan 1, 1-18"))
    }

    @Test
    fun `los salmos se nombran y los canticos no se anuncian como lectura`() {
        // El salmo se anuncia con su número (del misal) y sus versículos citados.
        assertEquals(
            "Salmo responsorial. Salmo ochenta y cinco, versículos del nueve al catorce.",
            Anuncios.lectura("Salmo responsorial", "Salmos", "Salmo 85, 9-14"),
        )
        assertEquals(
            "Salmo responsorial. Salmo ciento diecisiete, versículos del uno al dos.",
            Anuncios.lectura("Salmo responsorial", "Salmos", "Salmo 117, 1-2"),
        )
        assertEquals(
            "Salmo responsorial. Salmo setenta y nueve, versículos nueve, del doce al dieciséis y del diecinueve al veinte.",
            Anuncios.lectura("Salmo responsorial", "Salmos", "Salmo 79, 9. 12-16. 19-20"),
        )
        // Antes decía «Lectura del santo Evangelio según san Lucas» antes del
        // Magníficat, y otra vez lo mismo en el evangelio.
        assertEquals(
            "Salmo responsorial, cántico de Lucas, capítulo uno, versículos del cuarenta y seis al cincuenta y cinco.",
            Anuncios.lectura("Salmo responsorial", "Lucas", "Lucas 1, 46-55"),
        )
        assertEquals(
            "Salmo responsorial, cántico de Primero de Samuel, capítulo dos, versículos del uno al ocho.",
            Anuncios.lectura("Salmo responsorial", "1 Samuel", "1 Samuel 2, 1-8"),
        )
    }

    @Test
    fun `todas las citas del leccionario se pueden anunciar sin cifras`() {
        val lecc = JSONObject(File("src/main/assets/liturgia/leccionario.json").readText(Charsets.UTF_8))
        val nombres = HashMap<Int, String>()
        val index = JSONObject(File("src/main/assets/bible-cat/index.json").readText(Charsets.UTF_8)).getJSONArray("books")
        for (i in 0 until index.length()) index.getJSONObject(i).let { nombres[it.getInt("n")] = it.getString("name") }
        val sinVersiculos = ArrayList<String>()
        for (grupo in listOf("fijas", "temporales")) {
            val g = lecc.getJSONObject(grupo)
            for (clave in g.keys()) {
                val lecturas = g.getJSONArray(clave)
                for (i in 0 until lecturas.length()) {
                    val l = lecturas.getJSONObject(i)
                    val titulo = l.getString("t")
                    val anuncio = Anuncios.lectura(titulo, nombres.getValue(l.getInt("b")), l.getString("c"))
                    assertTrue("Cifras en «$anuncio»", !anuncio.contains(Regex("\\d")))
                    if (!titulo.startsWith("Salmo") && !anuncio.contains("versículo")) sinVersiculos.add(l.getString("c"))
                }
            }
        }
        // Solo Ester C (capítulo con letra) se queda sin capítulo y versículos.
        assertEquals(emptyList<String>(), sinVersiculos.filterNot { it.startsWith("Ester C") })
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
