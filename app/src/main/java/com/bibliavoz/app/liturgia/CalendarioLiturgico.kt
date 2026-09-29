package com.bibliavoz.app.liturgia

import java.time.LocalDate

/**
 * Calendario litúrgico romano, calculado sin datos externos.
 *
 * Todo sale de dos fechas: la Pascua (que da Cuaresma, Triduo, Pascua y
 * Pentecostés) y el primer domingo de Adviento (que abre el año litúrgico).
 * Al ser cálculo puro, funciona para cualquier año, pasado o futuro, y no
 * caduca nunca.
 *
 * Las claves que devuelve [claveTemporal] y [claveFija] son las mismas que
 * generó el script `tools/construir-leccionario.ps1` al empaquetar la tabla de
 * lecturas. Si se toca una, hay que tocar la otra.
 */
object CalendarioLiturgico {

    /** Domingo = 0 … sábado = 6, como en el script de construcción. */
    private fun dow(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** Cómputo gregoriano de la Pascua (algoritmo de Meeus/Jones/Butcher). */
    fun pascua(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /** Primer domingo de Adviento: cuarto domingo antes de Navidad. */
    fun adviento1(year: Int): LocalDate {
        val navidad = LocalDate.of(year, 12, 25)
        val d = dow(navidad)
        val domingoPrevio = if (d == 0) navidad.minusDays(7) else navidad.minusDays(d.toLong())
        return domingoPrevio.minusDays(21)
    }

    /**
     * Epifanía trasladada al domingo entre el 2 y el 8 de enero, que es el uso
     * del calendario del que provienen las lecturas empaquetadas.
     */
    fun epifania(year: Int): LocalDate {
        val ene2 = LocalDate.of(year, 1, 2)
        val d = dow(ene2)
        return if (d == 0) ene2 else ene2.plusDays((7 - d).toLong())
    }

    /** Bautismo del Señor: domingo siguiente a Epifanía, o lunes si cae el 7 u 8. */
    fun bautismo(year: Int): LocalDate {
        val epi = epifania(year)
        return if (epi.dayOfMonth >= 7) epi.plusDays(1) else epi.plusDays(7)
    }

    /** Año litúrgico: el año civil en que termina. Empieza en Adviento. */
    fun anioLiturgico(date: LocalDate): Int =
        if (!date.isBefore(adviento1(date.year))) date.year + 1 else date.year

    /** Ciclo dominical A, B o C. */
    fun cicloDominical(anioLit: Int): String = when (anioLit % 3) {
        1 -> "A"
        2 -> "B"
        else -> "C"
    }

    /** Ciclo ferial I (años impares) o II (años pares). */
    fun cicloFerial(anioLit: Int): String = if (anioLit % 2 == 1) "I" else "II"

    fun claveFija(date: LocalDate): String =
        "%02d-%02d".format(date.monthValue, date.dayOfMonth)

    /** Clave de Santa María, Madre de la Iglesia en `fijas`/`fijasInfo` del leccionario. */
    const val MADRE_DE_LA_IGLESIA = "MADRE-IGLESIA"

    /** Clave del Inmaculado Corazón de la Virgen María. */
    const val CORAZON_DE_MARIA = "CORAZON-MARIA"

    /**
     * Memorias obligatorias que dependen de la Pascua y no tienen fecha fija:
     * Santa María, Madre de la Iglesia (lunes después de Pentecostés) y el
     * Inmaculado Corazón de María (sábado después del Sagrado Corazón). Devuelve
     * su clave en el leccionario, o `null` si ese día no toca ninguna.
     */
    fun memoriaMovil(date: LocalDate): String? {
        val p = pascua(date.year)
        return when (date) {
            p.plusDays(50) -> MADRE_DE_LA_IGLESIA
            p.plusDays(69) -> CORAZON_DE_MARIA
            else -> null
        }
    }

    fun claveTemporal(date: LocalDate): String {
        val y = date.year
        val d = dow(date)
        val anioLit = anioLiturgico(date)
        val ciclo = cicloDominical(anioLit)
        val sufijo = if (d == 0) "-$ciclo" else "-" + cicloFerial(anioLit)

        val pascua = pascua(y)
        val miercolesCeniza = pascua.minusDays(46)
        val cuaresma1 = pascua.minusDays(42)
        val ramos = pascua.minusDays(7)
        val juevesSanto = pascua.minusDays(3)
        val pentecostes = pascua.plusDays(49)
        val adviento = adviento1(y)
        val bautismo = bautismo(y)
        val navidad = LocalDate.of(y, 12, 25)

        // Del 17 al 24 de diciembre las ferias de Adviento tienen lecturas
        // propias por fecha, no por día de la semana. Un día de Adviento de
        // finales de noviembre es una feria normal.
        if (!date.isBefore(adviento) && date.isBefore(navidad)) {
            if (d != 0 && date.monthValue == 12 && date.dayOfMonth >= 17) return "ADVDIC-%02d".format(date.dayOfMonth)
            val semana = (java.time.temporal.ChronoUnit.DAYS.between(adviento, date) / 7 + 1).toInt()
            return "ADV-$semana-$d$sufijo"
        }

        if (date.monthValue == 1 && date.dayOfMonth == 1) return "ENE01"

        // El 25 tiene su misa aunque caiga en domingo; la Sagrada Familia es el
        // domingo de la octava o, si no lo hay (Navidad en domingo), el 30.
        if (!date.isBefore(navidad)) {
            if (date == navidad) return "NAV-12-25"
            if (d == 0 || (dow(navidad) == 0 && date.dayOfMonth == 30)) return "SAGFAM-$ciclo"
            return "NAV-%02d-%02d".format(date.monthValue, date.dayOfMonth)
        }

        if (date.isBefore(bautismo)) {
            val epi = epifania(y)
            if (date == epi) return "EPIFANIA"
            if (d == 0) return "SAGFAM-$ciclo"
            if (date.isBefore(epi)) return "ANTEPIF-%02d".format(date.dayOfMonth)
            return "TRASEPIF-$d"
        }
        // El Bautismo tiene evangelio de cada ciclo A/B/C aunque caiga en lunes.
        if (date == bautismo) return "BAUTISMO-$ciclo"

        if (!date.isBefore(juevesSanto) && date.isBefore(pascua)) {
            val nombres = arrayOf("JUE", "VIE", "SAB")
            val indice = java.time.temporal.ChronoUnit.DAYS.between(juevesSanto, date).toInt().coerceIn(0, 2)
            // La Vigilia pascual también tiene evangelio propio de cada ciclo.
            return if (indice == 2) "TRI-SAB-$ciclo" else "TRI-" + nombres[indice]
        }
        if (!date.isBefore(ramos) && date.isBefore(juevesSanto)) return "SANTA-$d$sufijo"

        if (!date.isBefore(miercolesCeniza) && date.isBefore(cuaresma1)) return "CENIZA-$d"
        if (!date.isBefore(cuaresma1) && date.isBefore(ramos)) {
            val semana = (java.time.temporal.ChronoUnit.DAYS.between(cuaresma1, date) / 7 + 1).toInt()
            return "CUA-$semana-$d$sufijo"
        }

        // PAS-7-0 es la Ascensión, que en México se celebra el VII domingo de
        // Pascua; PAS-8-0 es Pentecostés.
        if (!date.isBefore(pascua) && !date.isAfter(pentecostes)) {
            val semana = (java.time.temporal.ChronoUnit.DAYS.between(pascua, date) / 7 + 1).toInt()
            return "PAS-$semana-$d$sufijo"
        }

        // Solemnidades móviles que caen ya en Tiempo Ordinario y desplazan a la feria.
        if (date == pascua.plusDays(56)) return "TRINIDAD$sufijo"
        if (date == pascua.plusDays(63)) return "CORPUS$sufijo"
        // El Sagrado Corazón tiene ciclo propio A/B/C aunque caiga en viernes.
        if (date == pascua.plusDays(68)) return "SAGCORAZON-$ciclo"

        if (date.isAfter(bautismo) && date.isBefore(miercolesCeniza)) {
            val domingo = date.minusDays(d.toLong())
            // Redondeando, como [math]::Round del script: si el Bautismo cae en
            // lunes, el domingo siguiente (6 días después) ya es el 2º.
            val dias = java.time.temporal.ChronoUnit.DAYS.between(bautismo, domingo)
            val semana = ((dias + 3) / 7 + 1).toInt()
            return "ORD-$semana-$d$sufijo"
        }

        if (date.isAfter(pentecostes) && date.isBefore(adviento)) {
            // El segundo tramo se cuenta hacia atrás desde la semana 34.
            val ultimoDomingo = adviento.minusDays(7)
            val domingo = date.minusDays(d.toLong())
            val n = (java.time.temporal.ChronoUnit.DAYS.between(domingo, ultimoDomingo) / 7).toInt()
            return "ORD-${34 - n}-$d$sufijo"
        }

        return "OTRO-%02d-%02d".format(date.monthValue, date.dayOfMonth)
    }

    /** Nombre legible del día litúrgico, para la cabecera de la pantalla. */
    fun descripcion(date: LocalDate): String {
        val clave = claveTemporal(date)
        val anioLit = anioLiturgico(date)
        val d = dow(date)
        val dia = DIAS[d]

        val base = when {
            clave.startsWith("ADVDIC-") -> "Feria de Adviento, ${date.dayOfMonth} de diciembre"
            clave.startsWith("ADV-") -> tramo(clave, dia, "de Adviento")
            clave == "ENE01" -> "Santa María, Madre de Dios"
            clave.startsWith("SAGFAM") -> "La Sagrada Familia"
            clave == "EPIFANIA" -> "Epifanía del Señor"
            clave.startsWith("ANTEPIF-") -> "Feria antes de Epifanía"
            clave.startsWith("TRASEPIF-") -> "$dia después de Epifanía"
            clave == "NAV-12-25" -> "La Natividad del Señor"
            clave.startsWith("NAV-") -> "Octava de Navidad"
            clave.startsWith("BAUTISMO") -> "El Bautismo del Señor"
            clave == "TRI-JUE" -> "Jueves Santo"
            clave == "TRI-VIE" -> "Viernes Santo"
            clave.startsWith("TRI-SAB") -> "Sábado Santo, Vigilia pascual"
            clave.startsWith("SANTA-") -> if (d == 0) "Domingo de Ramos" else "$dia Santo"
            clave.startsWith("CENIZA-") -> if (d == 3) "Miércoles de Ceniza" else "$dia después de Ceniza"
            clave.startsWith("CUA-") -> tramo(clave, dia, "de Cuaresma")
            clave.startsWith("PAS-1-") -> if (d == 0) "Domingo de Pascua de la Resurrección" else "$dia de la Octava de Pascua"
            clave.startsWith("PAS-7-0") -> "La Ascensión del Señor"
            clave.startsWith("PAS-8-0") -> "Domingo de Pentecostés"
            clave.startsWith("PAS-") -> tramo(clave, dia, "de Pascua")
            clave.startsWith("TRINIDAD") -> "La Santísima Trinidad"
            clave.startsWith("CORPUS") -> "El Cuerpo y la Sangre de Cristo"
            clave.startsWith("SAGCORAZON") -> "El Sagrado Corazón de Jesús"
            clave.startsWith("ORD-34-0") -> "Jesucristo, Rey del Universo"
            clave.startsWith("ORD-") -> tramo(clave, dia, "del Tiempo Ordinario")
            else -> dia
        }

        // Los días cuyas lecturas van por el ciclo dominical aunque no sean
        // domingo (Vigilia pascual, Sagrado Corazón…) lo muestran también.
        // Navidad es solemnidad: aunque caiga entre semana, su año es el dominical.
        val porDomingo = d == 0 || clave == "NAV-12-25" ||
            clave.endsWith("-A") || clave.endsWith("-B") || clave.endsWith("-C")
        val ciclo = if (porDomingo) "Ciclo ${cicloDominical(anioLit)}" else "Ciclo ${cicloFerial(anioLit)}"
        return "$base · $ciclo"
    }

    private fun tramo(clave: String, dia: String, temporada: String): String {
        val semana = clave.split("-").getOrNull(1)?.toIntOrNull() ?: return "$dia $temporada"
        return if (dia == "Domingo") {
            "Domingo ${semana}º $temporada"
        } else {
            "$dia de la semana $semana $temporada"
        }
    }

    private val DIAS = arrayOf(
        "Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"
    )
}
