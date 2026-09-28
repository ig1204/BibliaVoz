package com.bibliavoz.app.liturgia

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Pruebas del calendario litúrgico y de la cobertura del leccionario.
 *
 * Importan más de lo habitual porque la app no se puede ejecutar en esta
 * máquina: son la única comprobación real de que la función estrella
 * (las lecturas de la misa) hace lo que dice.
 */
class CalendarioLiturgicoTest {

    private val assets = File("src/main/assets")

    private val leccionario: JSONObject by lazy {
        JSONObject(File(assets, "liturgia/leccionario.json").readText(Charsets.UTF_8))
    }

    // ------------------------------------------------------------ calendario

    @Test
    fun `la Pascua cae donde debe`() {
        // Fechas reales de Domingo de Resurrección.
        assertEquals(LocalDate.of(2023, 4, 9), CalendarioLiturgico.pascua(2023))
        assertEquals(LocalDate.of(2024, 3, 31), CalendarioLiturgico.pascua(2024))
        assertEquals(LocalDate.of(2025, 4, 20), CalendarioLiturgico.pascua(2025))
        assertEquals(LocalDate.of(2026, 4, 5), CalendarioLiturgico.pascua(2026))
        assertEquals(LocalDate.of(2027, 3, 28), CalendarioLiturgico.pascua(2027))
        assertEquals(LocalDate.of(2030, 4, 21), CalendarioLiturgico.pascua(2030))
    }

    @Test
    fun `el Adviento empieza el cuarto domingo antes de Navidad`() {
        assertEquals(LocalDate.of(2025, 11, 30), CalendarioLiturgico.adviento1(2025))
        assertEquals(LocalDate.of(2026, 11, 29), CalendarioLiturgico.adviento1(2026))
        assertEquals(LocalDate.of(2027, 11, 28), CalendarioLiturgico.adviento1(2027))
        // Siempre es domingo y siempre cae entre el 27 de noviembre y el 3 de diciembre.
        for (year in 2020..2060) {
            val a = CalendarioLiturgico.adviento1(year)
            assertEquals("Adviento de $year no cae en domingo", 7, a.dayOfWeek.value)
            assertTrue("Adviento de $year fuera de rango: $a", a >= LocalDate.of(year, 11, 27))
            assertTrue("Adviento de $year fuera de rango: $a", a <= LocalDate.of(year, 12, 3))
        }
    }

    @Test
    fun `los ciclos dominical y ferial avanzan como toca`() {
        assertEquals("A", CalendarioLiturgico.cicloDominical(2023))
        assertEquals("B", CalendarioLiturgico.cicloDominical(2024))
        assertEquals("C", CalendarioLiturgico.cicloDominical(2025))
        assertEquals("A", CalendarioLiturgico.cicloDominical(2026))
        assertEquals("I", CalendarioLiturgico.cicloFerial(2025))
        assertEquals("II", CalendarioLiturgico.cicloFerial(2026))
    }

    @Test
    fun `el ano liturgico cambia en el primer domingo de Adviento`() {
        // 29 de noviembre de 2026 es el primer domingo de Adviento: ahí empieza 2027.
        assertEquals(2026, CalendarioLiturgico.anioLiturgico(LocalDate.of(2026, 11, 28)))
        assertEquals(2027, CalendarioLiturgico.anioLiturgico(LocalDate.of(2026, 11, 29)))
        assertEquals(2027, CalendarioLiturgico.anioLiturgico(LocalDate.of(2027, 6, 1)))
    }

    // ------------------------------------------------------------ cobertura

    /**
     * La prueba decisiva: los datos de origen terminan el 31 de octubre de 2027.
     * Si el diseño de claves litúrgicas funciona, los años siguientes tienen que
     * encontrar sus lecturas igualmente, sin ningún dato nuevo.
     */
    @Test
    fun `hay lecturas para los anos posteriores a los datos de origen`() {
        val fijas = leccionario.getJSONObject("fijas")
        val temporales = leccionario.getJSONObject("temporales")

        var total = 0
        var encontradas = 0
        val faltan = mutableListOf<String>()

        var fecha = LocalDate.of(2028, 1, 1)
        val fin = LocalDate.of(2032, 12, 31)
        while (fecha <= fin) {
            total++
            val porFecha = fijas.optJSONArray(CalendarioLiturgico.claveFija(fecha))
            val porTiempo = temporales.optJSONArray(CalendarioLiturgico.claveTemporal(fecha))
            if (porFecha != null || porTiempo != null) {
                encontradas++
            } else if (faltan.size < 25) {
                faltan.add("$fecha -> ${CalendarioLiturgico.claveTemporal(fecha)}")
            }
            fecha = fecha.plusDays(1)
        }

        val porcentaje = 100.0 * encontradas / total
        println("Cobertura 2028-2032: $encontradas de $total días ($porcentaje %)")
        if (faltan.isNotEmpty()) println("Sin lecturas, primeros casos: " + faltan.joinToString("\n  ", "\n  "))

        assertTrue(
            "Cobertura insuficiente en años futuros: $porcentaje %",
            porcentaje >= 95.0
        )
    }

    /** Las citas deben resolverse a texto real dentro de la Biblia empaquetada. */
    @Test
    fun `las citas apuntan a versiculos que existen`() {
        val indice = JSONObject(File(assets, "bible-cat/index.json").readText(Charsets.UTF_8))
        val libros = indice.getJSONArray("books")
        val capitulosPorLibro = HashMap<Int, Int>()
        for (i in 0 until libros.length()) {
            val o = libros.getJSONObject(i)
            capitulosPorLibro[o.getInt("n")] = o.getInt("chapters")
        }

        val temporales = leccionario.getJSONObject("temporales")
        var citas = 0
        var malas = 0
        val ejemplos = mutableListOf<String>()

        for (clave in temporales.keys()) {
            val array = temporales.getJSONArray(clave)
            for (i in 0 until array.length()) {
                val lectura = array.getJSONObject(i)
                val libro = lectura.getInt("b")
                val tramos = lectura.getJSONArray("r")
                for (j in 0 until tramos.length()) {
                    citas++
                    val t = tramos.getJSONArray(j)
                    if (t.length() < 4) {
                        malas++
                        if (ejemplos.size < 15) ejemplos.add("$clave: tramo mal formado ${t}")
                        continue
                    }
                    val capitulo = t.getInt(0)
                    val maxCapitulos = capitulosPorLibro[libro]
                    if (libro !in 1..73 || maxCapitulos == null || capitulo !in 1..maxCapitulos) {
                        malas++
                        if (ejemplos.size < 15) {
                            ejemplos.add("$clave: libro=$libro cap=$capitulo (máx ${maxCapitulos ?: "?"}) ${lectura.optString("c")}")
                        }
                    }
                }
            }
        }

        println("Citas comprobadas: $citas, fuera de rango: $malas")
        if (ejemplos.isNotEmpty()) println("Ejemplos:\n  " + ejemplos.joinToString("\n  "))
        assertEquals("Hay citas que apuntan fuera del canon empaquetado", 0, malas)
    }

    /** El texto tiene que estar realmente ahí, no solo el rango ser plausible. */
    @Test
    fun `una lectura conocida trae texto de verdad`() {
        val temporales = leccionario.getJSONObject("temporales")
        var conTexto = 0
        var revisadas = 0

        for (clave in temporales.keys().asSequence().take(60)) {
            val array = temporales.getJSONArray(clave)
            for (i in 0 until array.length()) {
                val lectura = array.getJSONObject(i)
                val libro = lectura.getInt("b")
                val rangos = lectura.getJSONArray("r")
                if (rangos.length() == 0) continue
                val t = rangos.getJSONArray(0)
                if (t.length() < 4) continue
                val texto = leerVersiculos(libro, t.getInt(0), t.getInt(1), t.getInt(2), t.getInt(3))
                revisadas++
                if (texto.isNotEmpty() && texto.any { it.isNotBlank() }) conTexto++
            }
        }

        println("Lecturas con texto: $conTexto de $revisadas")
        assertTrue("Demasiadas lecturas sin texto", conTexto >= revisadas * 0.95)
    }

    private fun leerVersiculos(libro: Int, cap1: Int, v1: Int, cap2: Int, v2: Int): List<String> {
        val json = JSONObject(File(assets, "bible-cat/$libro.json").readText(Charsets.UTF_8))
        val capitulos = json.getJSONArray("chapters")
        val salida = mutableListOf<String>()
        val primero = cap1.coerceIn(1, capitulos.length())
        val ultimo = cap2.coerceIn(primero, capitulos.length())
        for (c in primero..ultimo) {
            val versiculos = capitulos.getJSONArray(c - 1)
            val desde = if (c == primero) v1 else 1
            val hasta = if (c == ultimo) v2 else versiculos.length()
            for (v in desde.coerceAtLeast(1)..hasta.coerceAtMost(versiculos.length())) {
                salida.add(versiculos.getString(v - 1))
            }
        }
        return salida
    }
}
