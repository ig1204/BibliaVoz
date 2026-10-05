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

    @Test
    fun `con el Bautismo en lunes las semanas no se atrasan`() {
        // 2029: Epifanía el domingo 7 de enero, Bautismo el lunes 8. El domingo
        // 14 ya es el 2º del Tiempo Ordinario, como en el script que hizo la tabla.
        assertEquals(LocalDate.of(2029, 1, 8), CalendarioLiturgico.bautismo(2029))
        assertEquals("ORD-1-2-I", CalendarioLiturgico.claveTemporal(LocalDate.of(2029, 1, 9)))
        assertEquals("ORD-2-0-A", CalendarioLiturgico.claveTemporal(LocalDate.of(2029, 1, 14)))
        assertEquals("ORD-2-1-I", CalendarioLiturgico.claveTemporal(LocalDate.of(2029, 1, 15)))
        assertEquals("ORD-6-2-I", CalendarioLiturgico.claveTemporal(LocalDate.of(2029, 2, 13)))
        // Con el Bautismo en domingo no cambia nada.
        assertEquals("ORD-2-0-B", CalendarioLiturgico.claveTemporal(LocalDate.of(2027, 1, 17)))
        // El Bautismo en lunes lleva el ciclo dominical: su evangelio es del ciclo A/B/C.
        assertEquals("BAUTISMO-A", CalendarioLiturgico.claveTemporal(LocalDate.of(2029, 1, 8)))
    }

    @Test
    fun `las ferias del 17 al 24 son solo de diciembre`() {
        // El Adviento de 2026 empieza el 29 de noviembre: el 30 es una feria normal.
        assertEquals("ADV-1-1-I", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 11, 30)))
        assertEquals("ADV-1-1-II", CalendarioLiturgico.claveTemporal(LocalDate.of(2027, 11, 29)))
        assertEquals("ADVDIC-17", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 12, 17)))
        assertEquals("Lunes de la semana 1 de Adviento · Ciclo I", CalendarioLiturgico.descripcion(LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun `Navidad tiene su clave aunque caiga en domingo`() {
        assertEquals("NAV-12-25", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 12, 25)))
        // 2033: Navidad en domingo; la Sagrada Familia pasa al viernes 30.
        assertEquals("NAV-12-25", CalendarioLiturgico.claveTemporal(LocalDate.of(2033, 12, 25)))
        assertEquals("SAGFAM-C", CalendarioLiturgico.claveTemporal(LocalDate.of(2033, 12, 30)))
        assertEquals("SAGFAM-B", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 12, 27)))
        assertEquals("La Natividad del Señor · Ciclo B", CalendarioLiturgico.descripcion(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun `el Corpus mexicano es el jueves despues de la Trinidad`() {
        // En México el Corpus se celebra el jueves después de la Trinidad
        // (pascua+60) y el domingo siguiente vuelve a ser ordinario, no como en
        // EE. UU. (donde Corpus cae el domingo, pascua+63).
        // 2026: Corpus el jueves 4 de junio; el domingo 7 es el X domingo (A).
        assertEquals("CORPUS-A", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 6, 4)))
        assertEquals("ORD-10-0-A", CalendarioLiturgico.claveTemporal(LocalDate.of(2026, 6, 7)))
        // 2027: Corpus el jueves 27 de mayo; el domingo 30 es el IX domingo (B).
        assertEquals("CORPUS-B", CalendarioLiturgico.claveTemporal(LocalDate.of(2027, 5, 27)))
        assertEquals("ORD-9-0-B", CalendarioLiturgico.claveTemporal(LocalDate.of(2027, 5, 30)))
    }

    @Test
    fun `Jesucristo Sumo y Eterno Sacerdote cae el jueves despues de Pentecostes`() {
        // Fiesta propia de México: jueves después de Pentecostés (pascua+53).
        assertEquals(CalendarioLiturgico.SUMO_SACERDOTE, CalendarioLiturgico.memoriaMovil(LocalDate.of(2027, 5, 20)))
        assertEquals(CalendarioLiturgico.MADRE_DE_LA_IGLESIA, CalendarioLiturgico.memoriaMovil(LocalDate.of(2027, 5, 17)))
    }

    // ------------------------------------------------------------ cobertura

    /**
     * La prueba decisiva: los datos de origen terminan el 31 de octubre de 2027.
     * Si el diseño de claves litúrgicas funciona, los años siguientes tienen que
     * encontrar sus lecturas igualmente, sin ningún dato nuevo. Antes bastaba un
     * 95 %; ahora no se admite ni un día sin lecturas (el script tampoco lo deja
     * pasar), y se cuenta como la app: con la precedencia de las fiestas fijas.
     */
    @Test
    fun `hay lecturas para los anos posteriores a los datos de origen`() {
        val fijas = leccionario.getJSONObject("fijas")
        val temporales = leccionario.getJSONObject("temporales")
        val celebraciones = Precedencia.celebraciones(leccionario)

        var total = 0
        var encontradas = 0
        val faltan = mutableListOf<String>()

        var fecha = LocalDate.of(2026, 9, 29)
        val fin = LocalDate.of(2032, 12, 31)
        while (fecha <= fin) {
            total++
            val dia = Precedencia.dia(fecha, celebraciones)
            val porFecha = Precedencia.claveLecturasFija(fijas, dia)?.let { fijas.optJSONArray(it) }
            val porTiempo = temporales.optJSONArray(Precedencia.claveLecturasDelTiempo(temporales, dia))
            // Una memoria solo trae sus lecturas propias: el resto es de la feria.
            val completo = if (dia.fija?.grado == Grado.MEMORIA) porTiempo != null else (porFecha ?: porTiempo) != null
            if (completo) {
                encontradas++
            } else if (faltan.size < 25) {
                faltan.add("$fecha -> ${dia.claveTemporal} ${dia.fija?.clave ?: ""}")
            }
            fecha = fecha.plusDays(1)
        }

        println("Cobertura 2026-2032: $encontradas de $total días")
        if (faltan.isNotEmpty()) println("Sin lecturas, primeros casos: " + faltan.joinToString("\n  ", "\n  "))

        assertEquals("Días sin lecturas:\n" + faltan.joinToString("\n"), total, encontradas)
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
        val fijas = leccionario.getJSONObject("fijas")
        var citas = 0
        var malas = 0
        val ejemplos = mutableListOf<String>()

        for ((clave, array) in temporales.keys().asSequence().map { it to temporales.getJSONArray(it) } +
            fijas.keys().asSequence().map { it to fijas.getJSONArray(it) }) {
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
