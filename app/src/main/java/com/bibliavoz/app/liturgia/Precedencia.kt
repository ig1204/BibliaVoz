package com.bibliavoz.app.liturgia

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Grado de una fiesta de fecha fija, tal como lo guarda el script en
 * `fijasInfo` del leccionario (campo «g»), y su lugar en la tabla de
 * precedencia del calendario romano: cuanto menor el rango, más manda.
 */
enum class Grado(val codigo: String, val nombre: String, val rango: Int) {
    SOLEMNIDAD("S", "Solemnidad", 3),
    FIESTA_DEL_SENOR("FS", "Fiesta", 5),
    FIESTA("F", "Fiesta", 7),
    MEMORIA("M", "Memoria", 10);

    companion object {
        fun de(codigo: String): Grado? = values().firstOrNull { it.codigo == codigo }
    }
}

/**
 * Una fiesta del leccionario: su clave («MM-DD», o la de una memoria móvil como
 * [CalendarioLiturgico.MADRE_DE_LA_IGLESIA]), su nombre y su grado.
 */
class Celebracion(val clave: String, val nombre: String, val grado: Grado)

/**
 * Lo que se celebra un día: siempre su clave del tiempo litúrgico y, si ese día
 * manda una fiesta de fecha fija (quizá trasladada desde otro), [fija].
 */
class DiaLiturgico(val fecha: LocalDate, val claveTemporal: String, val fija: Celebracion?) {

    /** Cabecera de la pantalla: el nombre de la fiesta si la hay; si no, el del tiempo litúrgico. */
    val descripcion: String
        get() = when {
            fija == null -> CalendarioLiturgico.descripcion(fecha)
            // La de los difuntos tiene rango de solemnidad, pero no lo es.
            fija.nombre.startsWith("Conmemoración") -> fija.nombre
            else -> "${fija.nombre} · ${fija.grado.nombre}"
        }
}

/**
 * Qué lecturas manda cada día cuando coinciden el tiempo litúrgico y una fiesta
 * de fecha fija: la tabla de precedencia del calendario romano, simplificada a
 * lo que distingue el leccionario de la app.
 *
 * - El Triduo, Navidad, Epifanía, el Miércoles de Ceniza, la Semana Santa, la
 *   octava de Pascua y los domingos de Adviento, Cuaresma y Pascua ganan a todo.
 * - Los domingos del Tiempo Ordinario y de Navidad ceden ante las solemnidades y
 *   las fiestas del Señor, no ante las de los santos.
 * - Las fiestas de los santos ganan a cualquier feria, también a las de
 *   Cuaresma y de la octava de Navidad (la Cátedra de San Pedro, San Esteban).
 * - Las memorias no se imponen en Cuaresma, del 17 al 24 de diciembre ni en la
 *   octava de Navidad; cuando se celebran, solo cambian las lecturas propias.
 * - Una solemnidad impedida se traslada al día libre siguiente (la Inmaculada en
 *   domingo de Adviento, al lunes); San José en Semana Santa se adelanta al
 *   sábado anterior a Ramos, y la Anunciación en Semana Santa o en la octava de
 *   Pascua pasa al lunes siguiente al II domingo de Pascua.
 *
 * La usan la app ([Leccionario]) y el generador de audio de la PC: si eligieran
 * distinto, el teléfono pediría un audio que nunca se grabó.
 */
object Precedencia {

    /** Nuestra Señora de Guadalupe (12 dic), patrona de México. */
    const val GUADALUPE = "12-12"

    /** Lee `fijasInfo` del leccionario: «MM-DD» → nombre y grado. */
    fun celebraciones(raiz: JSONObject): Map<String, Celebracion> {
        val info = raiz.optJSONObject("fijasInfo") ?: return emptyMap()
        val out = HashMap<String, Celebracion>()
        for (clave in info.keys()) {
            val o = info.optJSONObject(clave) ?: continue
            val grado = Grado.de(o.optString("g")) ?: continue
            out[clave] = Celebracion(clave, o.optString("n", clave), grado)
        }
        return out
    }

    /** Qué se celebra en [fecha]. */
    fun dia(fecha: LocalDate, celebraciones: Map<String, Celebracion>): DiaLiturgico {
        val claveTemporal = CalendarioLiturgico.claveTemporal(fecha)
        val claveFija = CalendarioLiturgico.claveFija(fecha)

        // Una solemnidad que en su fecha estaba impedida y se trasladó a este día.
        val trasladada = celebraciones.values.firstOrNull {
            it.grado == Grado.SOLEMNIDAD && it.clave != claveFija &&
                fechaCelebrada(it, fecha.year, celebraciones) == fecha
        }
        if (trasladada != null) return DiaLiturgico(fecha, claveTemporal, trasladada)

        val propia = celebraciones[claveFija]
        val fija = when {
            propia == null -> null
            // La solemnidad impedida no se omite: se celebra el día al que pasa.
            propia.grado == Grado.SOLEMNIDAD -> propia.takeIf { fechaCelebrada(it, fecha.year, celebraciones) == fecha }
            propia.grado.rango < rangoDelTiempo(claveTemporal, fecha) -> propia
            else -> null
        }

        // Las memorias móviles (Madre de la Iglesia, Corazón de María) caen
        // siempre en una feria del Tiempo Ordinario y ceden ante cualquier fiesta.
        // Si coinciden con otra memoria, la de la Madre de la Iglesia prevalece y
        // la del Corazón de María deja la del día.
        val movil = CalendarioLiturgico.memoriaMovil(fecha)?.let { celebraciones[it] }
            ?.takeIf { it.grado.rango < rangoDelTiempo(claveTemporal, fecha) }
        val celebrada = when {
            movil == null -> fija
            fija == null -> movil
            // Una fiesta móvil (Jesucristo Sumo Sacerdote) gana a una memoria fija.
            movil.grado.rango < fija.grado.rango -> movil
            fija.grado == Grado.MEMORIA && movil.clave == CalendarioLiturgico.MADRE_DE_LA_IGLESIA -> movil
            else -> fija
        }
        return DiaLiturgico(fecha, claveTemporal, celebrada)
    }

    /**
     * Clave de las lecturas de la fiesta en la tabla `fijas`: «MM-DD», o
     * «MM-DD/A» (B, C) si sus lecturas cambian con el ciclo dominical (la
     * Transfiguración).
     */
    fun claveLecturasFija(fijas: JSONObject, dia: DiaLiturgico): String? {
        val fija = dia.fija ?: return null
        val porCiclo = "${fija.clave}/${ciclo(dia.fecha)}"
        return if (fijas.has(porCiclo)) porCiclo else fija.clave
    }

    /**
     * Clave de las lecturas del día en la tabla `temporales`. Unas pocas ferias
     * cambian un pasaje según el ciclo dominical, porque el domingo anterior ya
     * lo proclamó (el lunes IV de Pascua del ciclo A lee Juan 10, 11-18): esas
     * van aparte como «CLAVE/CICLO» y se prefieren cuando existen.
     */
    fun claveLecturasDelTiempo(temporales: JSONObject, dia: DiaLiturgico): String {
        val porCiclo = "${dia.claveTemporal}/${ciclo(dia.fecha)}"
        return if (temporales.has(porCiclo)) porCiclo else dia.claveTemporal
    }

    private fun ciclo(fecha: LocalDate): String =
        CalendarioLiturgico.cicloDominical(CalendarioLiturgico.anioLiturgico(fecha))

    /**
     * Las lecturas que se leen: las de la fiesta si manda una; las de una
     * memoria solo sustituyen a las de la feria con su mismo título (su lectura
     * propia), y el resto sigue siendo el de la feria de ese año.
     *
     * Reglas de México: entre semana, las fiestas (del Señor o de los santos) y
     * el Bautismo del Señor en lunes se leen con 1ª lectura, salmo y Evangelio,
     * sin 2ª lectura. Y Guadalupe, cuando cae en domingo de Adviento, toma la 2ª
     * lectura de ese domingo (decreto de la CEM), no la de Gálatas.
     */
    fun <T> combinar(dia: DiaLiturgico, delTiempo: List<T>, deLaFiesta: List<T>, titulo: (T) -> String): List<T> {
        val esDomingo = dia.fecha.dayOfWeek == DayOfWeek.SUNDAY
        val esSegunda: (T) -> Boolean = { titulo(it).startsWith("Segunda lectura") }

        val grado = dia.fija?.grado
        if (grado == null || deLaFiesta.isEmpty()) {
            // El Bautismo del Señor, cuando cae en lunes, se lee sin 2ª lectura.
            return if (!esDomingo && dia.claveTemporal.startsWith("BAUTISMO")) {
                delTiempo.filterNot(esSegunda)
            } else {
                delTiempo
            }
        }

        // Entre semana, las fiestas del Señor y de los santos pierden la 2ª lectura.
        val fiesta = if (!esDomingo && (grado == Grado.FIESTA || grado == Grado.FIESTA_DEL_SENOR)) {
            deLaFiesta.filterNot(esSegunda)
        } else {
            deLaFiesta
        }

        if (grado != Grado.MEMORIA || delTiempo.isEmpty()) {
            // Guadalupe en domingo de Adviento: la 2ª lectura es la de ese domingo.
            if (dia.fija?.clave == GUADALUPE && esDomingo) {
                val segundaDelTiempo = delTiempo.firstOrNull(esSegunda)
                if (segundaDelTiempo != null) {
                    // Si Guadalupe trae su 2ª lectura (lo normal: Gálatas), se sustituye;
                    // si no la trajera, se inserta antes del Evangelio en vez de perderla.
                    if (fiesta.any(esSegunda)) {
                        return fiesta.map { if (esSegunda(it)) segundaDelTiempo else it }
                    }
                    val out = fiesta.toMutableList()
                    val i = out.indexOfFirst { titulo(it).startsWith("Evangelio") }
                    out.add(if (i < 0) out.size else i, segundaDelTiempo)
                    return out
                }
            }
            return fiesta
        }

        // Memoria: solo sustituye las lecturas de la feria con su mismo título.
        val propias = LinkedHashMap<String, T>()
        for (l in fiesta) propias.getOrPut(titulo(l)) { l }
        val out = ArrayList<T>(delTiempo.size + propias.size)
        for (l in delTiempo) out.add(propias.remove(titulo(l)) ?: l)
        // Una lectura propia que la feria no trae va delante del evangelio.
        if (propias.isNotEmpty()) {
            val i = out.indexOfFirst { titulo(it).startsWith("Evangelio") }
            out.addAll(if (i < 0) out.size else i, propias.values)
        }
        return out
    }

    /**
     * Lugar del día en la tabla de precedencia según su clave del tiempo
     * litúrgico: 1 el Triduo … 13 una feria común. Se compara con [Grado.rango].
     */
    fun rangoDelTiempo(clave: String, fecha: LocalDate): Int {
        val domingo = fecha.dayOfWeek == DayOfWeek.SUNDAY
        return when {
            clave.startsWith("TRI-") -> 1
            clave == "NAV-12-25" || clave == "EPIFANIA" || clave == "CENIZA-3" -> 2
            clave.startsWith("SANTA-") || clave.startsWith("PAS-1-") -> 2
            domingo && (clave.startsWith("ADV-") || clave.startsWith("CUA-") || clave.startsWith("PAS-")) -> 2
            clave == "ENE01" || clave.startsWith("TRINIDAD") || clave.startsWith("CORPUS") ||
                clave.startsWith("SAGCORAZON") || clave.startsWith("ORD-34-0-") -> 3
            clave.startsWith("BAUTISMO") || clave.startsWith("SAGFAM") -> 5
            domingo -> 6
            clave.startsWith("ADVDIC-") || clave.startsWith("NAV-") || clave.startsWith("CUA-") ||
                clave.startsWith("CENIZA-") -> 9
            else -> 13
        }
    }

    /**
     * Día en que se celebra [c] en [anio], o `null` si no llega a celebrarse.
     * Solo las solemnidades se trasladan; una fiesta o memoria impedida se omite.
     */
    fun fechaCelebrada(c: Celebracion, anio: Int, celebraciones: Map<String, Celebracion>): LocalDate? {
        val natural = fechaDe(c.clave, anio) ?: return null
        if (c.grado != Grado.SOLEMNIDAD || !impedida(natural, c, celebraciones)) return natural
        val pascua = CalendarioLiturgico.pascua(anio)
        val ramos = pascua.minusDays(7)
        // San José en Semana Santa se adelanta al sábado anterior al Domingo de Ramos.
        if (c.clave == "03-19" && !natural.isBefore(ramos) && natural.isBefore(pascua)) return ramos.minusDays(1)
        // La Anunciación en Semana Santa o en la octava de Pascua pasa al lunes
        // siguiente al II domingo de Pascua.
        if (c.clave == "03-25" && !natural.isBefore(ramos) && !natural.isAfter(pascua.plusDays(7))) return pascua.plusDays(8)
        var dia = natural.plusDays(1)
        repeat(14) {
            if (!impedida(dia, c, celebraciones)) return dia
            dia = dia.plusDays(1)
        }
        return null
    }

    /** Una solemnidad no puede celebrarse un día de rango igual o mayor, ni sobre otra solemnidad. */
    private fun impedida(fecha: LocalDate, c: Celebracion, celebraciones: Map<String, Celebracion>): Boolean {
        // Nuestra Señora de Guadalupe (12 dic), patrona de México y de América, se
        // celebra siempre en su fecha, incluso cuando el 12 cae en domingo de Adviento
        // (2027, 2032): nunca se traslada al lunes. Es la norma del propio mexicano.
        if (c.clave == GUADALUPE) return false
        if (rangoDelTiempo(CalendarioLiturgico.claveTemporal(fecha), fecha) <= Grado.SOLEMNIDAD.rango) return true
        val otra = celebraciones[CalendarioLiturgico.claveFija(fecha)]
        return otra != null && otra !== c && otra.grado == Grado.SOLEMNIDAD
    }

    private fun fechaDe(clave: String, anio: Int): LocalDate? {
        val mes = clave.substring(0, 2).toIntOrNull() ?: return null
        val dia = clave.substring(3, 5).toIntOrNull() ?: return null
        return runCatching { LocalDate.of(anio, mes, dia) }.getOrNull()
    }
}
