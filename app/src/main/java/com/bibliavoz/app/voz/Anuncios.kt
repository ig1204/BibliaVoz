package com.bibliavoz.app.voz

import com.bibliavoz.app.voz.NumerosEnLetras.enLetras

/**
 * Lo que dice la voz IA antes de un capítulo o de una lectura de la misa.
 *
 * Los libros con número se nombran como se dicen en voz alta («Primero de
 * Reyes», «Segunda de Corintios»), y las lecturas de la misa con la fórmula con
 * que se proclaman («Lectura del libro del profeta Isaías»).
 */
object Anuncios {

    private val CON_NUMERO = Regex("^([1-3])\\s+(.+)$")

    /** Libros que se nombran en masculino («el primer libro de…»); los demás son cartas. */
    private val LIBROS_NUMERADOS = mapOf(
        "Samuel" to "de Samuel",
        "Reyes" to "de los Reyes",
        "Crónicas" to "de las Crónicas",
        "Macabeos" to "de los Macabeos",
    )

    private val PROFETAS = setOf(
        "Isaías", "Jeremías", "Baruc", "Ezequiel", "Daniel", "Oseas", "Joel", "Amós", "Abdías",
        "Jonás", "Miqueas", "Nahúm", "Habacuc", "Sofonías", "Ageo", "Hageo", "Zacarías", "Malaquías",
    )

    private val EVANGELIOS = setOf("Mateo", "Marcos", "Lucas", "Juan")

    private val CARTAS_DE_PABLO = mapOf(
        "Romanos" to "a los Romanos",
        "Corintios" to "a los Corintios",
        "Gálatas" to "a los Gálatas",
        "Efesios" to "a los Efesios",
        "Filipenses" to "a los Filipenses",
        "Colosenses" to "a los Colosenses",
        "Tesalonicenses" to "a los Tesalonicenses",
        "Timoteo" to "a Timoteo",
        "Tito" to "a Tito",
        "Filemón" to "a Filemón",
    )

    private val OTROS_LIBROS = mapOf(
        "Génesis" to "del libro del Génesis",
        "Éxodo" to "del libro del Éxodo",
        "Levítico" to "del libro del Levítico",
        "Números" to "del libro de los Números",
        "Deuteronomio" to "del libro del Deuteronomio",
        "Josué" to "del libro de Josué",
        "Jueces" to "del libro de los Jueces",
        "Rut" to "del libro de Rut",
        "Esdras" to "del libro de Esdras",
        "Nehemías" to "del libro de Nehemías",
        "Tobías" to "del libro de Tobías",
        "Judit" to "del libro de Judit",
        "Ester" to "del libro de Ester",
        "Job" to "del libro de Job",
        "Proverbios" to "del libro de los Proverbios",
        "Eclesiastés" to "del libro del Eclesiastés",
        "Cantar de los Cantares" to "del libro del Cantar de los Cantares",
        "Cantares" to "del libro de los Cantares",
        "Sabiduría" to "del libro de la Sabiduría",
        "Eclesiástico" to "del libro del Eclesiástico",
        "Lamentaciones" to "del libro de las Lamentaciones",
        "Hechos" to "del libro de los Hechos de los Apóstoles",
        "Hebreos" to "de la carta a los Hebreos",
        "Santiago" to "de la carta del apóstol Santiago",
        "Judas" to "de la carta del apóstol san Judas",
        "Apocalipsis" to "del libro del Apocalipsis del apóstol san Juan",
    )

    private val ORDINAL_MASCULINO = arrayOf("", "Primero", "Segundo", "Tercero")
    private val ORDINAL_FEMENINO = arrayOf("", "Primera", "Segunda", "Tercera")
    private val ORDINAL_LIBRO = arrayOf("", "primer", "segundo", "tercer")
    private val ORDINAL_CARTA = arrayOf("", "primera", "segunda", "tercera")

    /** «1 Reyes» → «Primero de Reyes», «2 Corintios» → «Segunda de Corintios». */
    fun nombreHablado(libro: String): String {
        val nombre = libro.trim()
        val m = CON_NUMERO.find(nombre) ?: return nombre
        val n = m.groupValues[1].toInt()
        val resto = m.groupValues[2]
        val ordinal = if (resto in LIBROS_NUMERADOS) ORDINAL_MASCULINO[n] else ORDINAL_FEMENINO[n]
        return "$ordinal de $resto"
    }

    /** «Génesis, capítulo uno.» Los salmos se nombran como se dicen: «Salmo veintitrés.» */
    fun capitulo(libro: String, capitulo: Int): String =
        if (libro.trim() == "Salmos") {
            "Salmo ${enLetras(capitulo)}."
        } else {
            "${nombreHablado(libro)}, capítulo ${enLetras(capitulo)}."
        }

    /**
     * Encabezado de una lectura de la misa: «Evangelio. Lectura del santo
     * Evangelio según san Marcos.» Si no se conoce el libro, solo el título.
     */
    fun lectura(titulo: String, libro: String, capitulo: Int): String {
        val t = titulo.trim().trimEnd('.')
        val nombre = libro.trim()
        if (nombre.isEmpty()) return "$t."
        if (nombre == "Salmos") return if (capitulo > 0) "$t. Salmo ${enLetras(capitulo)}." else "$t."
        val formula = formula(nombre) ?: return "$t. ${nombreHablado(nombre)}."
        return "$t. Lectura $formula."
    }

    /** «del libro del profeta Isaías», «de la primera carta del apóstol san Pedro»… */
    fun formula(libro: String): String? {
        val m = CON_NUMERO.find(libro.trim())
        val n = m?.groupValues?.get(1)?.toInt() ?: 0
        val base = m?.groupValues?.get(2) ?: libro.trim()

        if (n > 0) {
            LIBROS_NUMERADOS[base]?.let { return "del ${ORDINAL_LIBRO[n]} libro $it" }
            CARTAS_DE_PABLO[base]?.let {
                return "de la ${ORDINAL_CARTA[n]} carta del apóstol san Pablo $it"
            }
            return when (base) {
                "Pedro" -> "de la ${ORDINAL_CARTA[n]} carta del apóstol san Pedro"
                "Juan" -> "de la ${ORDINAL_CARTA[n]} carta del apóstol san Juan"
                else -> null
            }
        }
        return when {
            base in EVANGELIOS -> "del santo Evangelio según san $base"
            base in PROFETAS -> "del libro del profeta $base"
            base in CARTAS_DE_PABLO -> "de la carta del apóstol san Pablo ${CARTAS_DE_PABLO[base]}"
            else -> OTROS_LIBROS[base]
        }
    }
}
