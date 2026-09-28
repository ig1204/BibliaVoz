package com.bibliavoz.app.voz

/**
 * Números escritos con palabras, en español.
 *
 * La voz IA dice con más naturalidad «capítulo ciento diecinueve» que
 * «capítulo 119», y la Santa Biblia Libre escribe las cifras de los censos con
 * coma de millares («2,172»), que en español se leería como un decimal.
 */
object NumerosEnLetras {

    private val HASTA_VEINTINUEVE = arrayOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
        "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés",
        "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve",
    )

    private val DECENAS = arrayOf(
        "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa",
    )

    private val CENTENAS = arrayOf(
        "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
        "seiscientos", "setecientos", "ochocientos", "novecientos",
    )

    /**
     * Una cifra con separador de millares («2,172», «144.000») o un número
     * suelto. Los separadores solo cuentan si les siguen exactamente tres
     * dígitos: «3, 16» son dos números, no uno.
     */
    private val CIFRA = Regex("(?<![\\d.,])\\d{1,3}(?:[.,]\\d{3})+(?!\\d)|(?<!\\d)\\d+(?!\\d)")

    fun enLetras(n: Int): String = enLetras(n.toLong())

    fun enLetras(n: Long): String {
        require(n >= 0) { "Solo números naturales: $n" }
        return when {
            n < 30 -> HASTA_VEINTINUEVE[n.toInt()]
            n < 100 -> {
                val decena = DECENAS[(n / 10).toInt()]
                val unidad = (n % 10).toInt()
                if (unidad == 0) decena else "$decena y ${HASTA_VEINTINUEVE[unidad]}"
            }
            n == 100L -> "cien"
            n < 1_000 -> {
                val resto = n % 100
                val centena = CENTENAS[(n / 100).toInt()]
                if (resto == 0L) centena else "$centena ${enLetras(resto)}"
            }
            n < 1_000_000 -> {
                val miles = n / 1_000
                val resto = n % 1_000
                val prefijo = if (miles == 1L) "mil" else "${apocopar(enLetras(miles))} mil"
                if (resto == 0L) prefijo else "$prefijo ${enLetras(resto)}"
            }
            n < 1_000_000_000_000 -> {
                val millones = n / 1_000_000
                val resto = n % 1_000_000
                val prefijo = if (millones == 1L) "un millón" else "${apocopar(enLetras(millones))} millones"
                if (resto == 0L) prefijo else "$prefijo ${enLetras(resto)}"
            }
            else -> n.toString()
        }
    }

    /** Delante de «mil» y de «millones», «uno» pierde la o: «veintiún mil», «ciento un mil». */
    private fun apocopar(s: String): String = when {
        s.endsWith("veintiuno") -> s.removeSuffix("veintiuno") + "veintiún"
        s.endsWith("uno") -> s.removeSuffix("uno") + "un"
        else -> s
    }

    /** Sustituye todas las cifras de [texto] por palabras. */
    fun reemplazarCifras(texto: String): String =
        CIFRA.replace(texto) { m ->
            m.value.filter { it.isDigit() }.toLongOrNull()?.let { enLetras(it) } ?: m.value
        }
}
