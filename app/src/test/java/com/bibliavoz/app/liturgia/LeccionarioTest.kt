package com.bibliavoz.app.liturgia

import com.bibliavoz.app.voz.Tramos
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pruebas del leccionario empaquetado tal como lo usa la app: qué se lee cada
 * día (con la precedencia de las fiestas fijas) y que lo que se lee existe, no
 * se repite y no se alarga.
 *
 * La app no se puede ejecutar en esta máquina: estas pruebas son la
 * comprobación de que cada día suena la misa que toca.
 */
class LeccionarioTest {

    private class L(val titulo: String, val cita: String, val libro: Int, val tramos: List<IntArray>)

    private class Dia(val eleccion: DiaLiturgico, val descripcion: String, val lecturas: List<L>)

    private val assets = File("src/main/assets")
    private val raiz: JSONObject by lazy { JSONObject(File(assets, "liturgia/leccionario.json").readText(Charsets.UTF_8)) }
    private val fijas: JSONObject by lazy { raiz.getJSONObject("fijas") }
    private val temporales: JSONObject by lazy { raiz.getJSONObject("temporales") }
    private val celebraciones: Map<String, Celebracion> by lazy { Precedencia.celebraciones(raiz) }
    private val libros = HashMap<Int, List<List<String>>>()

    private val desde = LocalDate.of(2026, 9, 29)
    private val hasta = LocalDate.of(2032, 12, 31)

    private fun libro(n: Int): List<List<String>> = libros.getOrPut(n) {
        val caps = JSONObject(File(assets, "bible-cat/$n.json").readText(Charsets.UTF_8)).getJSONArray("chapters")
        (0 until caps.length()).map { c ->
            val vs = caps.getJSONArray(c)
            (0 until vs.length()).map { vs.getString(it) }
        }
    }

    /** Copia de `Leccionario.parse`. */
    private fun parse(array: JSONArray): List<L> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val r = o.optJSONArray("r") ?: continue
            val tramos = (0 until r.length()).mapNotNull { j ->
                val t = r.optJSONArray(j) ?: return@mapNotNull null
                if (t.length() >= 4) intArrayOf(t.getInt(0), t.getInt(1), t.getInt(2), t.getInt(3)) else null
            }
            if (tramos.isEmpty()) continue
            add(L(o.optString("t"), o.optString("c"), o.optInt("b"), tramos))
        }
    }

    /** Lo mismo que `Leccionario.delDia`, sin Android. */
    private fun delDia(fecha: LocalDate): Dia {
        val dia = Precedencia.dia(fecha, celebraciones)
        val delTiempo = temporales.optJSONArray(Precedencia.claveLecturasDelTiempo(temporales, dia))?.let { parse(it) } ?: emptyList()
        val deLaFiesta = Precedencia.claveLecturasFija(fijas, dia)?.let { fijas.optJSONArray(it) }?.let { parse(it) } ?: emptyList()
        val lecturas = Precedencia.combinar(delTiempo, deLaFiesta, dia.fija?.grado) { it.titulo }
        val descripcion = if (deLaFiesta.isEmpty()) CalendarioLiturgico.descripcion(fecha) else dia.descripcion
        return Dia(dia, descripcion, lecturas)
    }

    /** Los versículos que suenan, con la misma función que la app. */
    private fun versiculos(l: L): List<String> =
        l.tramos.flatMap { Tramos.versos(libro(l.libro), it[0], it[1], it[2], it[3]) }

    /** Posiciones (capítulo, versículo) con texto que abarca una lectura, repetidas si se repiten. */
    private fun posiciones(l: L): List<Pair<Int, Int>> {
        val caps = libro(l.libro)
        val out = ArrayList<Pair<Int, Int>>()
        for (t in l.tramos) {
            for (c in t[0]..t[2]) {
                val vs = caps.getOrNull(c - 1) ?: continue
                val ini = if (c == t[0]) t[1] else 1
                val fin = if (c == t[2]) minOf(t[3], vs.size) else vs.size
                for (v in ini..fin) if (v >= 1 && vs[v - 1].isNotBlank()) out.add(c to v)
            }
        }
        return out
    }

    private fun todas(): List<Pair<String, L>> = buildList {
        for ((nombre, grupo) in listOf("fijas" to fijas, "temporales" to temporales)) {
            for (clave in grupo.keys()) for (l in parse(grupo.getJSONArray(clave))) add("$nombre/$clave" to l)
        }
    }

    private fun dias(): Sequence<LocalDate> = generateSequence(desde) { it.plusDays(1) }.takeWhile { !it.isAfter(hasta) }

    // ------------------------------------------------------------ todo el periodo

    @Test
    fun `cada dia hasta 2032 tiene su misa completa`() {
        val malos = mutableListOf<String>()
        for (fecha in dias()) {
            val dia = delDia(fecha)
            val titulos = dia.lecturas.map { it.titulo }
            val requeridas = mutableListOf("Primera lectura", "Salmo responsorial", "Evangelio")
            if (fecha.dayOfWeek == DayOfWeek.SUNDAY) requeridas.add("Segunda lectura")
            val faltan = requeridas.filter { it !in titulos }
            val vacias = dia.lecturas.filter { versiculos(it).isEmpty() }.map { it.titulo }
            if (faltan.isNotEmpty() || vacias.isNotEmpty()) {
                malos.add("$fecha ${dia.eleccion.claveTemporal} ${dia.eleccion.fija?.clave ?: ""}: faltan $faltan, vacías $vacias")
            }
        }
        assertTrue("Días sin su misa completa:\n" + malos.take(40).joinToString("\n"), malos.isEmpty())
    }

    @Test
    fun `ninguna lectura del leccionario queda sin versiculos`() {
        val vacias = todas().filter { (_, l) -> versiculos(l).isEmpty() }.map { (k, l) -> "$k ${l.titulo}: ${l.cita}" }
        assertTrue("Lecturas sin texto:\n" + vacias.joinToString("\n"), vacias.isEmpty())
    }

    @Test
    fun `ningun tramo repite versiculos`() {
        val repetidas = todas().filter { (_, l) -> posiciones(l).let { it.size != it.toSet().size } }
            .map { (k, l) -> "$k ${l.titulo}: ${l.cita} " + l.tramos.joinToString("") { it.contentToString() } }
        assertTrue("Lecturas con versículos repetidos:\n" + repetidas.joinToString("\n"), repetidas.isEmpty())
    }

    /**
     * El salmo responsorial se lee completo cuando tiene 30 versículos o menos
     * (decisión deliberada: el desfase de los títulos haría sonar un tramo
     * corrido). Si es más largo, solo lo citado: nunca el salmo entero.
     */
    @Test
    fun `ningun salmo responsorial pasa de su cita`() {
        val largos = mutableListOf<String>()
        for ((k, l) in todas()) {
            if (l.titulo != "Salmo responsorial") continue
            val n = posiciones(l).size
            val caps = libro(l.libro)
            val completos = l.libro == 23 && l.tramos.all { t ->
                t[0] == t[2] && t[1] == 1 && t[3] == caps[t[0] - 1].size && caps[t[0] - 1].size <= 30
            }
            if (completos) continue
            val citados = versiculosCitados(l.cita)
            if (n > 30 || n > citados) largos.add("$k: ${l.cita} lee $n versículos (la cita tiene $citados)")
        }
        assertTrue("Salmos que se alargan:\n" + largos.joinToString("\n"), largos.isEmpty())
    }

    /** Cuántos versículos nombra una cita «Salmo 89, 2-5. 27. 29» o «Daniel 3, 52-56». */
    private fun versiculosCitados(cita: String): Int {
        var total = 0
        for (parte in cita.substringAfter(", ").split(". ", "; ")) {
            val versos = parte.substringAfter(", ")
            val m = Regex("^(\\d+)(?:-(\\d+))?$").find(versos.trim()) ?: return Int.MAX_VALUE
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toIntOrNull() ?: a
            total += b - a + 1
        }
        return total
    }

    // ------------------------------------------------------------ fechas clave

    private fun Dia.tiene(libro: Int, vararg capitulos: Int) =
        lecturas.any { l -> l.libro == libro && l.tramos.any { it[0] in capitulos } }

    @Test
    fun `primer domingo de Adviento de 2026, ciclo B`() {
        val dia = delDia(LocalDate.of(2026, 11, 29))
        assertEquals("ADV-1-0-B", dia.eleccion.claveTemporal)
        assertNull("Ninguna fiesta fija le gana", dia.eleccion.fija)
        assertEquals("Domingo 1º de Adviento · Ciclo B", dia.descripcion)
        assertTrue("Isaías 63-64", dia.tiene(29, 63, 64))
        assertTrue("Marcos 13", dia.tiene(48, 13))
        assertEquals(4, dia.lecturas.size)
    }

    @Test
    fun `el domingo 18 de octubre de 2026 es el domingo 29, no San Lucas`() {
        val dia = delDia(LocalDate.of(2026, 10, 18))
        assertEquals("ORD-29-0-A", dia.eleccion.claveTemporal)
        assertNull(dia.eleccion.fija)
        assertTrue("Mateo 22", dia.tiene(47, 22))
        assertTrue("Isaías 45", dia.tiene(29, 45))
        // Entre semana San Lucas sí se celebra.
        assertEquals("10-18", delDia(LocalDate.of(2027, 10, 18)).eleccion.fija?.clave)
    }

    @Test
    fun `el segundo domingo de Adviento B lee el Salmo 85`() {
        val dia = delDia(LocalDate.of(2026, 12, 6))
        val salmo = dia.lecturas.first { it.titulo == "Salmo responsorial" }
        assertEquals(23, salmo.libro)
        assertTrue(salmo.tramos.all { it[0] == 85 && it[2] == 85 })
        assertEquals("Salmo 85, 9-14", salmo.cita)
    }

    @Test
    fun `Navidad tiene lecturas todos los anos`() {
        for (anio in 2026..2032) {
            val dia = delDia(LocalDate.of(anio, 12, 25))
            assertEquals("NAV-12-25", dia.eleccion.claveTemporal)
            assertEquals(4, dia.lecturas.size)
            assertTrue("Juan 1 en $anio", dia.tiene(50, 1))
        }
    }

    @Test
    fun `la cabecera nombra la fiesta que se lee`() {
        assertEquals("Santos Miguel, Gabriel y Rafael, arcángeles · Fiesta", delDia(LocalDate.of(2026, 9, 29)).descripcion)
        assertEquals("Todos los Santos · Solemnidad", delDia(LocalDate.of(2026, 11, 1)).descripcion)
        assertEquals("La Inmaculada Concepción de la Virgen María · Solemnidad", delDia(LocalDate.of(2026, 12, 8)).descripcion)
        assertEquals("Nuestra Señora de Guadalupe · Solemnidad", delDia(LocalDate.of(2026, 12, 12)).descripcion)
        assertEquals("Conmemoración de todos los fieles difuntos", delDia(LocalDate.of(2026, 11, 2)).descripcion)
        // Un día sin fiesta sigue con la del tiempo litúrgico.
        assertEquals("Miércoles de la semana 26 del Tiempo Ordinario · Ciclo II", delDia(LocalDate.of(2026, 9, 30)).descripcion)
    }

    @Test
    fun `las fiestas de los santos no tapan domingos ni la Semana Santa`() {
        // San Juan en domingo: la Sagrada Familia.
        assertTrue(delDia(LocalDate.of(2026, 12, 27)).eleccion.claveTemporal.startsWith("SAGFAM"))
        assertNull(delDia(LocalDate.of(2026, 12, 27)).eleccion.fija)
        // San Marcos en el V domingo de Pascua de 2027.
        assertNull(delDia(LocalDate.of(2027, 4, 25)).eleccion.fija)
        // San Bernabé (memoria) en la Trinidad de 2028.
        assertNull(delDia(LocalDate.of(2028, 6, 11)).eleccion.fija)
        // Todos los Santos sí gana al domingo.
        assertEquals("11-01", delDia(LocalDate.of(2026, 11, 1)).eleccion.fija?.clave)
        // Las fechas falsas de la tabla vieja ya no existen: el 9 de diciembre es feria de Adviento.
        assertNull(delDia(LocalDate.of(2026, 12, 9)).eleccion.fija)
        assertTrue("Isaías 40", delDia(LocalDate.of(2026, 12, 9)).tiene(29, 40))
    }

    @Test
    fun `las solemnidades impedidas se trasladan`() {
        // 2027: el 25 de marzo es Jueves Santo; la Anunciación pasa al lunes 5 de abril.
        val jueves = delDia(LocalDate.of(2027, 3, 25))
        assertEquals("TRI-JUE", jueves.eleccion.claveTemporal)
        assertNull(jueves.eleccion.fija)
        assertTrue("Éxodo 12", jueves.tiene(2, 12))
        assertEquals("03-25", delDia(LocalDate.of(2027, 4, 5)).eleccion.fija?.clave)
        // 2028: San José cae en el III domingo de Cuaresma y pasa al lunes 20.
        assertNull(delDia(LocalDate.of(2028, 3, 19)).eleccion.fija)
        assertEquals("CUA-3-0-C", delDia(LocalDate.of(2028, 3, 19)).eleccion.claveTemporal)
        assertEquals("03-19", delDia(LocalDate.of(2028, 3, 20)).eleccion.fija?.clave)
        // 2029: la Anunciación cae en Domingo de Ramos y pasa al lunes después del II domingo de Pascua.
        assertEquals("03-25", delDia(LocalDate.of(2029, 4, 9)).eleccion.fija?.clave)
        // 2030: la Inmaculada en domingo de Adviento pasa al lunes 9.
        assertNull(delDia(LocalDate.of(2030, 12, 8)).eleccion.fija)
        assertEquals("12-08", delDia(LocalDate.of(2030, 12, 9)).eleccion.fija?.clave)
        // Guadalupe en el III domingo de Adviento (2027) pasa al lunes 13.
        assertNull(delDia(LocalDate.of(2027, 12, 12)).eleccion.fija)
        assertEquals("12-12", delDia(LocalDate.of(2027, 12, 13)).eleccion.fija?.clave)
    }

    @Test
    fun `una memoria solo cambia sus lecturas propias`() {
        // Santos Timoteo y Tito, martes 26-1-2027: primera lectura y salmo propios,
        // evangelio del martes de la III semana (Marcos 3, 31-35).
        val dia = delDia(LocalDate.of(2027, 1, 26))
        assertEquals("01-26", dia.eleccion.fija?.clave)
        val primera = dia.lecturas.first { it.titulo == "Primera lectura" }
        assertEquals(62, primera.libro)
        val evangelio = dia.lecturas.first { it.titulo == "Evangelio" }
        assertEquals(48, evangelio.libro)
        assertEquals(3, evangelio.tramos[0][0])
        assertEquals(3, dia.lecturas.size)
        // En Cuaresma una memoria no se impone: no hay ninguna en la tabla, pero
        // la regla se comprueba con el rango.
        assertTrue(Grado.MEMORIA.rango > Precedencia.rangoDelTiempo("CUA-2-3-I", LocalDate.of(2027, 3, 3)))
    }

    @Test
    fun `las ferias que dependen del ciclo dominical no repiten el evangelio del domingo`() {
        fun evangelio(fecha: LocalDate) = delDia(fecha).lecturas.first { it.titulo == "Evangelio" }.tramos[0].toList()
        fun primera(fecha: LocalDate) = delDia(fecha).lecturas.first { it.titulo == "Primera lectura" }.tramos[0].toList()
        // Lunes IV de Pascua: ciclo A Juan 10, 11-18; B y C Juan 10, 1-10.
        assertEquals(listOf(10, 11, 10, 18), evangelio(LocalDate.of(2029, 4, 23)))
        assertEquals(listOf(10, 1, 10, 10), evangelio(LocalDate.of(2027, 4, 19)))
        // Lunes V de Cuaresma: ciclo C Juan 8, 12-20; A y B Juan 8, 1-11.
        assertEquals(listOf(8, 12, 8, 20), evangelio(LocalDate.of(2028, 4, 3)))
        assertEquals(listOf(8, 1, 8, 11), evangelio(LocalDate.of(2027, 3, 15)))
        // Lunes I de Adviento: ciclo A Isaías 4, 2-6; B y C Isaías 2, 1-5.
        assertEquals(listOf(4, 2, 4, 6), primera(LocalDate.of(2028, 12, 4)))
        assertEquals(listOf(2, 1, 2, 5), primera(LocalDate.of(2027, 11, 29)))
        // Lunes XVIII: ciclo A Mateo 14, 22-36; B y C Mateo 14, 13-21.
        assertEquals(listOf(14, 22, 14, 36), evangelio(LocalDate.of(2032, 8, 2)))
        assertEquals(listOf(14, 13, 14, 21), evangelio(LocalDate.of(2027, 8, 2)))
    }

    @Test
    fun `las memorias con evangelio propio lo leen y no contaminan la feria`() {
        // 2-10-2026, Ángeles Custodios: primera lectura de la feria (Job) y Mateo 18.
        val angeles = delDia(LocalDate.of(2026, 10, 2))
        assertEquals("10-02", angeles.eleccion.fija?.clave)
        assertTrue("Job", angeles.tiene(22, 38))
        assertTrue("Mateo 18", angeles.tiene(47, 18))
        // La misma feria (viernes XXVI del año II) en 2028 no lee Mateo 18.
        val feria = delDia(LocalDate.of(2028, 10, 6))
        assertEquals("ORD-26-5-II", feria.eleccion.claveTemporal)
        assertTrue("Lucas 10", feria.tiene(49, 10))
        // Santa Marta el jueves 29-7-2027.
        assertTrue("Juan 11", delDia(LocalDate.of(2027, 7, 29)).tiene(50, 11))
    }

    @Test
    fun `lecturas que antes faltaban o sonaban mal`() {
        // 20-12-2026: la doxología de Romanos (en esta Biblia, 14, 24-26).
        val segunda = delDia(LocalDate.of(2026, 12, 20)).lecturas.first { it.titulo == "Segunda lectura" }
        assertEquals(listOf(14, 24, 14, 26), segunda.tramos[0].toList())
        // 12-11-2026: Filemón.
        assertTrue(delDia(LocalDate.of(2026, 11, 12)).tiene(64, 1))
        // 23-12-2026: Malaquías 3, 1-4 y 4, 5-6 (el profeta Elías), sin repetir.
        val malaquias = delDia(LocalDate.of(2026, 12, 23)).lecturas.first { it.titulo == "Primera lectura" }
        assertEquals(listOf(listOf(3, 1, 3, 4), listOf(4, 5, 4, 6)), malaquias.tramos.map { it.toList() })
        // Guadalupe: Zacarías 2, 14-17 es 2, 10-13 en esta Biblia.
        val zacarias = delDia(LocalDate.of(2026, 12, 12)).lecturas.first { it.titulo == "Primera lectura" }
        assertEquals(listOf(2, 10, 2, 13), zacarias.tramos[0].toList())
        // Domingo de Ramos: la Pasión, no solo el evangelio de la procesión.
        val ramos = delDia(LocalDate.of(2027, 3, 21))
        assertTrue("Marcos 14-15", ramos.tiene(48, 14))
        // Transfiguración con el evangelio de su ciclo (2027 es ciclo B).
        assertTrue("Marcos 9", delDia(LocalDate.of(2027, 8, 6)).tiene(48, 9))
        // Pentecostés, Ascensión y la Vigilia pascual existen y tienen evangelio.
        assertTrue(delDia(LocalDate.of(2028, 6, 4)).lecturas.any { it.titulo == "Evangelio" })
        assertEquals("La Ascensión del Señor · Ciclo C", delDia(LocalDate.of(2028, 5, 28)).descripcion)
        assertTrue("Lucas 24 en la Vigilia de 2028", delDia(LocalDate.of(2028, 4, 15)).tiene(49, 24))
        // Primer lunes del Tiempo Ordinario de 2029, ya en la semana 2.
        assertEquals("ORD-2-1-I", delDia(LocalDate.of(2029, 1, 15)).eleccion.claveTemporal)
        assertNotNull(temporales.optJSONArray("ORD-2-1-I"))
    }

    @Test
    fun `memorias moviles y deuterocanonicos cotejados con el texto`() {
        // Lunes después de Pentecostés (17-5-2027): Santa María, Madre de la Iglesia.
        val madre = delDia(LocalDate.of(2027, 5, 17))
        assertEquals(CalendarioLiturgico.MADRE_DE_LA_IGLESIA, madre.eleccion.fija?.clave)
        assertEquals("Santa María, Madre de la Iglesia · Memoria", madre.descripcion)
        assertTrue("Génesis 3", madre.tiene(1, 3))
        assertTrue("Juan 19", madre.tiene(50, 19))
        // Sábado después del Sagrado Corazón (5-6-2027): la feria y su evangelio propio.
        val corazon = delDia(LocalDate.of(2027, 6, 5))
        assertEquals(CalendarioLiturgico.CORAZON_DE_MARIA, corazon.eleccion.fija?.clave)
        assertEquals("Inmaculado Corazón de la Virgen María · Memoria", corazon.descripcion)
        assertTrue("Lucas 2", corazon.tiene(49, 2))
        assertTrue("primera lectura de la feria", corazon.lecturas.any { it.titulo == "Primera lectura" })
        // 26-5-2027: Eclesiástico 36, 1. 4-5a. 10-17 en la numeración de esta Biblia.
        val eclo = delDia(LocalDate.of(2027, 5, 26)).lecturas.first { it.titulo == "Primera lectura" }
        assertEquals(listOf(listOf(36, 1, 36, 2), listOf(36, 5, 36, 6), listOf(36, 11, 36, 17)), eclo.tramos.map { it.toList() })
        // 3-6-2027: Tobías, que esta Biblia trae en su recensión corta.
        val tobias = delDia(LocalDate.of(2027, 6, 3)).lecturas.first { it.titulo == "Primera lectura" }
        assertEquals(
            listOf(listOf(6, 9, 6, 10), listOf(7, 1, 7, 1), listOf(7, 8, 7, 18), listOf(8, 4, 8, 8)),
            tobias.tramos.map { it.toList() },
        )
    }
}
