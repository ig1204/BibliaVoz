package com.bibliavoz.app.voz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pruebas del director de la voz IA.
 *
 * Lo que de verdad importa no se puede oír aquí (la app no corre en esta
 * máquina), pero sí se puede garantizar lo que le llega al modelo: que en toda
 * la Biblia no se cuele una etiqueta inventada, una cifra, una palabra en
 * mayúsculas o la ortografía de 1909.
 */
class DirectorTest {

    private val etiqueta = Regex("\\[[^\\]]*]")

    private fun narrar(vararg versos: String, anterior: String? = null, libro: String = "") =
        Director.narrar(versos.toList(), anterior = anterior, libro = libro).texto

    // ------------------------------------------------------------ limpieza

    @Test
    fun `limpia la ortografia de 1909 y las mayusculas de inicio`() {
        assertEquals(
            "En el principio crió Dios los cielos y la tierra.",
            Director.limpiar("EN el principio crió Dios los cielos y la tierra."),
        )
        assertEquals(
            "Y dijo Dios: Sea la luz: y fue la luz.",
            Director.limpiar("Y dijo Dios: Sea la luz: y fué la luz."),
        )
        assertEquals(
            "derribóse a sus pies, y vio a Jesús o a Pedro",
            Director.limpiar("derribóse á sus pies, y vió á Jesús ó á Pedro"),
        )
        assertEquals("Fue la tarde", Director.limpiar("FUÉ la tarde"))
        assertEquals("Alabad a Jah", Director.limpiar("ALABAD á JAH"))
    }

    @Test
    fun `quita los corchetes del texto y escribe las cifras`() {
        assertEquals(
            "Oró al Señor, haciendo mención de todas las obras del Señor.",
            Director.limpiar("[Oró al Señor, haciendo mención de todas las obras del Señor."),
        )
        assertEquals(
            "Los descendientes de Paros: dos mil ciento setenta y dos.",
            Director.limpiar("Los descendientes de Paros: 2,172."),
        )
    }

    // ------------------------------------------------------------ emociones

    @Test
    fun `llanto narrado y dialogo con la emocion arrastrada`() {
        val texto = narrar(
            "Jesús entonces, como la vió llorando, y á los Judíos que habían venido juntamente con ella llorando, se conmovió en espíritu, y turbóse,",
            "Y dijo: ¿Dónde le pusisteis? Dícenle: Señor, ven, y ve.",
            "Y lloró Jesús.",
        )
        assertTrue(texto, texto.startsWith("[sad] Jesús entonces"))
        assertTrue(texto, "Y dijo: [sad] ¿Dónde le pusisteis?" in texto)
        assertTrue(texto, "[sad] Y lloró Jesús." in texto)
    }

    @Test
    fun `la voz del cielo lleva eco`() {
        val texto = narrar("Y he aquí una voz de los cielos que decía: Este es mi Hijo amado, en el cual tengo contentamiento.")
        assertTrue(texto, "decía: [echo] Este es mi Hijo amado" in texto)
    }

    @Test
    fun `los gritos de la multitud`() {
        val texto = narrar("Pero ellos volvieron á dar voces, diciendo: Crucifícale, crucifícale.")
        assertTrue(texto, "diciendo: [shouting] Crucifícale" in texto)
    }

    @Test
    fun `cuando habla Dios, tono solemne`() {
        assertTrue(narrar("Y dijo Dios: Sea la luz: y fué la luz.").contains("Y dijo Dios: [solemn tone] Sea la luz"))
        assertTrue(narrar("Pero Yahvé le dijo a Abraham: “¿Por qué se ríe Sara?”").contains("[solemn tone] “¿Por qué"))
        // Hablarle A Dios no es que Dios hable.
        assertFalse(narrar("Y dijo Moisés á Jehová: ¿Por qué has afligido á tu siervo?").contains("[solemn tone]"))
    }

    @Test
    fun `el discurso que empieza en el versiculo siguiente`() {
        val texto = narrar(
            "Y HABLÓ Jehová á Moisés, diciendo:",
            "Habla á los hijos de Israel, y diles: Cuando alguno de vosotros ofrece ofrenda á Jehová,",
        )
        assertTrue(texto, "diciendo: [solemn tone] Habla a los hijos de Israel" in texto)
        // Y lo mismo si el versículo que abre el discurso quedó en el tramo anterior.
        val suelto = narrar(
            "Habla á los hijos de Israel, y diles: Cuando alguno de vosotros ofrece ofrenda á Jehová,",
            anterior = "Y HABLÓ Jehová á Moisés, diciendo:",
        )
        assertTrue(suelto, suelto.startsWith("[solemn tone] Habla"))
    }

    @Test
    fun `la risa de Sara y las comillas de la Biblia catolica`() {
        assertTrue(narrar("Rióse, pues, Sara entre sí, diciendo: ¿Después que he envejecido tendré deleite?").contains("diciendo: [chuckling]"))
        val sbl = narrar("Por eso Sara se rió por dentro, pensando: “¿Acaso voy a tener este placer?”")
        assertTrue(sbl, "[chuckling] “¿Acaso" in sbl)
    }

    @Test
    fun `alabanza y lamento`() {
        assertTrue(narrar("ALABAD á Dios en su santuario: alabadle en la extensión de su fortaleza.").startsWith("[delight] Alabad"))
        val lamento = narrar(
            "¡CÓMO está sentada sola la ciudad populosa!",
            libro = "Lamentaciones",
        )
        assertTrue(lamento, lamento.startsWith("[sad] ¡Cómo está"))
    }

    @Test
    fun `falsos amigos que no llevan emocion`() {
        // «Llamar» aquí es nombrar, no hablar.
        assertFalse(narrar("Y llamó Dios á la luz Día, y á las tinieblas llamó Noche: y fué la tarde y la mañana un día.").contains("["))
        // La ira de la que se habla no es enojo de nadie.
        assertFalse(narrar("Misericordioso y clemente es Jehová; Lento para la ira, y grande en misericordia.").contains("[angry]"))
        assertFalse(narrar("La blanda respuesta quita la ira: Mas la palabra áspera hace subir el furor.").contains("[angry]"))
        // Tiembla la tierra, no una persona.
        assertFalse(narrar("Y he aquí, el velo del templo se rompió en dos, de alto á bajo: y la tierra tembló, y las piedras se hendieron;").contains("[fearful]"))
        // Nombrar la alegría para decir que se acabó.
        assertFalse(narrar("Cesó el gozo de nuestro corazón.").contains("[delight]"))
        // El escarnecedor de los Proverbios es un tipo de persona.
        assertFalse(narrar("El escarnecedor no ama al que le reprende; Ni se allega á los sabios.").contains("[mocking tone]"))
    }

    @Test
    fun `la compasion del padre del hijo prodigo`() {
        val texto = narrar("Y levantándose, vino á su padre. Y como aun estuviese lejos, viólo su padre, y fué movido á misericordia, y corrió, y echóse sobre su cuello, y besóle.")
        assertTrue(texto, "[tender tone] Y como aun estuviese lejos" in texto)
    }

    @Test
    fun `sin estilo novela no hay etiquetas`() {
        val guion = Director.narrar(
            listOf("Y lloró Jesús."),
            cabecera = "Juan, capítulo once.",
            conEmociones = false,
        )
        assertEquals("Juan, capítulo once. Y lloró Jesús.", guion.texto)
    }

    @Test
    fun `cabecera con pausa, numeros de versiculo y reparto del tiempo`() {
        val guion = Director.narrar(
            listOf("Y lloró Jesús.", "", "Dijeron entonces los Judíos: Mirad cómo le amaba."),
            cabecera = "Juan, capítulo once.",
            primerNumero = 35,
        )
        assertTrue(guion.texto, guion.texto.startsWith("Juan, capítulo once. [pause] Versículo treinta y cinco. [sad] Y lloró Jesús."))
        assertTrue(guion.texto, "Versículo treinta y siete." in guion.texto)
        assertTrue(guion.inicios[0] > 0f)
        assertTrue(guion.inicios[1] >= guion.inicios[0])
        assertTrue(guion.inicios[2] > guion.inicios[0] && guion.inicios[2] < 1f)
    }

    // ------------------------------------------------------------ la Biblia entera

    private data class Libro(val nombre: String, val capitulos: List<List<String>>)

    private fun biblia(dir: String): List<Libro> {
        val carpeta = File("src/main/assets/$dir")
        val index = JSONObject(File(carpeta, "index.json").readText(Charsets.UTF_8))
        val books = index.getJSONArray("books")
        return (0 until books.length()).map { i ->
            val n = books.getJSONObject(i).getInt("n")
            val root = JSONObject(File(carpeta, "$n.json").readText(Charsets.UTF_8))
            val chapters = root.getJSONArray("chapters")
            Libro(
                nombre = root.getString("name"),
                capitulos = (0 until chapters.length()).map { c ->
                    val versos = chapters.getJSONArray(c)
                    (0 until versos.length()).map { versos.getString(it) }
                },
            )
        }
    }

    @Test
    fun `en toda la Biblia el modelo solo recibe etiquetas conocidas y texto limpio`() {
        val mayusculas = Regex("(?<!\\p{L})\\p{Lu}{2,}(?!\\p{L})")
        val vocalSuelta = Regex("(?<!\\p{L})[áéóúÁÉÓÚ](?!\\p{L})")
        for (dir in listOf("bible", "bible-cat")) {
            val cuenta = HashMap<String, Int>()
            var versos = 0
            var etiquetados = 0
            var tramos = 0
            for (libro in biblia(dir)) {
                libro.capitulos.forEachIndexed { c, capitulo ->
                    for (tramo in Segmentador.segmentar(capitulo)) {
                        tramos++
                        val guion = Director.narrar(
                            versos = capitulo.subList(tramo.desde, tramo.hasta + 1),
                            anterior = capitulo.getOrNull(tramo.desde - 1),
                            cabecera = if (tramo.desde == 0) Anuncios.capitulo(libro.nombre, c + 1) else null,
                            libro = libro.nombre,
                        )
                        val sitio = "${libro.nombre} ${c + 1}:${tramo.desde + 1}"
                        val sinEtiquetas = etiqueta.replace(guion.texto, "")
                        for (m in etiqueta.findAll(guion.texto)) {
                            assertTrue("Etiqueta desconocida ${m.value} en $sitio", m.value in Director.ETIQUETAS)
                            cuenta[m.value] = (cuenta[m.value] ?: 0) + 1
                        }
                        assertFalse("Corchete suelto en $sitio", '[' in sinEtiquetas || ']' in sinEtiquetas)
                        assertFalse("Cifra en $sitio: $sinEtiquetas", sinEtiquetas.any { it.isDigit() })
                        mayusculas.find(sinEtiquetas)?.let { throw AssertionError("Mayúsculas «${it.value}» en $sitio") }
                        vocalSuelta.find(sinEtiquetas)?.let { throw AssertionError("«${it.value}» suelta en $sitio") }
                        val conTexto = capitulo.subList(tramo.desde, tramo.hasta + 1).any { it.isNotBlank() }
                        assertFalse("Tramo mudo en $sitio", guion.texto.isBlank() && (conTexto || tramo.desde == 0))
                        for (i in 1 until guion.inicios.size) {
                            assertTrue("Tiempos desordenados en $sitio", guion.inicios[i] >= guion.inicios[i - 1])
                        }
                        assertTrue(guion.inicios.all { it in 0f..1f })

                        for (v in tramo.desde..tramo.hasta) {
                            if (capitulo[v].isNotBlank()) versos++
                        }
                        etiquetados += Regex("\\[(?!pause])").findAll(guion.texto).count()
                    }
                }
            }
            val porcentaje = 100.0 * etiquetados / versos
            println("== $dir: $versos versículos en $tramos tramos; $etiquetados etiquetas de emoción (${"%.1f".format(porcentaje)} por cada 100 versículos)")
            cuenta.entries.sortedByDescending { it.value }.forEach { println("   ${it.key} ${it.value}") }
            // Más de una emoción cada tres versículos ya sonaría a teatro.
            assertTrue("Demasiadas etiquetas en $dir: $porcentaje%", porcentaje < 33.0)
        }
    }

    /** No comprueba nada: imprime el guion de pasajes clave para revisarlo a ojo. */
    @Test
    fun `muestra de guiones`() {
        val rv = biblia("bible").associateBy { it.nombre }
        val sbl = biblia("bible-cat").associateBy { it.nombre }
        fun mostrar(libros: Map<String, Libro>, nombre: String, capitulo: Int, desde: Int, hasta: Int) {
            val versos = libros.getValue(nombre).capitulos[capitulo - 1]
            val guion = Director.narrar(
                versos.subList(desde - 1, hasta),
                anterior = versos.getOrNull(desde - 2),
                cabecera = if (desde == 1) Anuncios.capitulo(nombre, capitulo) else null,
                libro = nombre,
            )
            println("--- $nombre $capitulo:$desde-$hasta\n${guion.texto}\n")
        }
        println("===== Reina-Valera 1909")
        mostrar(rv, "Génesis", 1, 1, 5)
        mostrar(rv, "Génesis", 22, 1, 12)
        mostrar(rv, "Éxodo", 15, 1, 3)
        mostrar(rv, "1 Samuel", 17, 43, 47)
        mostrar(rv, "Salmos", 23, 1, 6)
        mostrar(rv, "Mateo", 27, 45, 54)
        mostrar(rv, "Lucas", 15, 20, 32)
        mostrar(rv, "Juan", 11, 32, 44)
        mostrar(rv, "Juan", 20, 11, 18)
        println("===== Santa Biblia Libre")
        mostrar(sbl, "Génesis", 22, 1, 12)
        mostrar(sbl, "Lucas", 15, 20, 32)
        mostrar(sbl, "Juan", 11, 32, 44)
    }
}
