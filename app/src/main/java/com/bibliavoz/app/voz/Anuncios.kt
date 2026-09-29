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
     * Encabezado de una lectura de la misa, con el libro, el capítulo y los
     * versículos de su [cita] («Daniel 7, 9-14»), como se anuncia en misa:
     *
     * - «Primera lectura del libro del profeta Daniel, capítulo siete, versículos
     *   del nueve al catorce.»
     * - «Lectura del santo Evangelio según san Marcos, capítulo dieciséis,
     *   versículos del quince al veinte.»
     * - Un salmo se lee completo (ver el leccionario), así que solo se nombra:
     *   «Salmo responsorial. Salmo ochenta y cinco.»
     * - Un cántico que hace de salmo (el Magníficat, Isaías 12…) no se proclama
     *   como lectura: «Salmo responsorial, cántico de Isaías, capítulo doce, …».
     *
     * [libro] es el nombre del libro en la Biblia de la misa; si no se conoce,
     * solo el título. La cita va en la numeración del leccionario, la misma que
     * muestra la pantalla.
     */
    fun lectura(titulo: String, libro: String, cita: String): String {
        val t = titulo.trim().trimEnd('.')
        val nombre = libro.trim()
        if (nombre.isEmpty()) return "$t."
        val refs = Cita.de(cita)
        if (nombre == "Salmos") {
            val salmo = refs?.primerCapitulo ?: 0
            return if (salmo > 0) "$t. Salmo ${enLetras(salmo)}." else "$t."
        }
        val donde = refs?.hablada()?.let { ", $it" } ?: ""
        if (t.startsWith("Salmo responsorial")) return "$t, cántico de ${nombreHablado(nombre)}$donde."
        val formula = formula(nombre) ?: return "$t. ${nombreHablado(nombre)}$donde."
        return when {
            // «Primera lectura», «Segunda lectura»… van pegadas a la fórmula.
            t.endsWith("lectura") -> "$t $formula$donde."
            t == "Evangelio" -> "Lectura $formula$donde."
            // «Evangelio de la procesión», «Epístola».
            else -> "$t. Lectura $formula$donde."
        }
    }

    /**
     * Una cita del leccionario sin el libro: «7, 9-10. 13-14», «63, 16-17. 19;
     * 64, 2-7», «26, 14 - 27, 66» o, en los libros de un solo capítulo, «7-20».
     */
    class Cita private constructor(private val grupos: List<Grupo>) {

        /** Los versículos de un capítulo (o de un libro sin capítulos, con [capitulo] 0). */
        private class Grupo(val capitulo: Int, val partes: List<Parte>)

        /** «9», «9-14» o, si cruza de capítulo, «14 - 27, 66» ([hastaCapitulo] 27). */
        private class Parte(val desde: Int, val hasta: Int, val hastaCapitulo: Int = 0)

        val primerCapitulo: Int get() = grupos.firstOrNull()?.capitulo ?: 0

        /** «capítulo siete, versículos del nueve al diez y del trece al catorce» */
        fun hablada(): String = grupos.joinToString(", y ") { g ->
            val sola = g.partes.singleOrNull()
            val versiculos = when {
                sola != null && sola.hastaCapitulo > 0 ->
                    "versículo ${enLetras(sola.desde)}, al capítulo ${enLetras(sola.hastaCapitulo)}, versículo ${enLetras(sola.hasta)}"
                sola != null && sola.desde == sola.hasta -> "versículo ${enLetras(sola.desde)}"
                else -> "versículos " + lista(g.partes.map { p ->
                    when {
                        p.hastaCapitulo > 0 -> "del ${enLetras(p.desde)} al capítulo ${enLetras(p.hastaCapitulo)}, versículo ${enLetras(p.hasta)}"
                        p.desde == p.hasta -> enLetras(p.desde)
                        else -> "del ${enLetras(p.desde)} al ${enLetras(p.hasta)}"
                    }
                })
            }
            if (g.capitulo > 0) "capítulo ${enLetras(g.capitulo)}, $versiculos" else versiculos
        }

        private fun lista(xs: List<String>): String =
            if (xs.size <= 1) xs.joinToString("") else xs.dropLast(1).joinToString(", ") + " y " + xs.last()

        companion object {
            /** El libro y lo que sigue: el libro puede llevar número («1 Juan») o varias palabras. */
            private val PARTES = Regex("^(.+?)\\s+(\\d[\\d ,.;-]*)$")
            private val PARTE = Regex("^(\\d+)(?:-(\\d+))?(?: - (\\d+), (\\d+))?$")
            private val GRUPO = Regex("^(\\d+), (.+)$")

            /** `null` si la cita no tiene capítulo y versículos que se puedan leer (Ester C, 12…). */
            fun de(cita: String): Cita? {
                val m = PARTES.find(cita.trim()) ?: return null
                // «Ester C, 12…»: el capítulo es una letra; mejor no decir números sueltos.
                if (m.groupValues[1].endsWith(",")) return null
                val refs = m.groupValues[2].trim()
                val grupos = ArrayList<Grupo>()
                for (texto in refs.split("; ")) {
                    val g = GRUPO.find(texto)
                    // Sin coma: un libro de un solo capítulo («Filemón 7-20»).
                    val capitulo = g?.groupValues?.get(1)?.toInt() ?: 0
                    val resto = g?.groupValues?.get(2) ?: texto
                    if (capitulo == 0 && grupos.isNotEmpty()) return null
                    val partes = ArrayList<Parte>()
                    for (p in resto.split(". ")) {
                        val m = PARTE.find(p.trim()) ?: return null
                        val desde = m.groupValues[1].toInt()
                        partes += when {
                            m.groupValues[3].isNotEmpty() ->
                                Parte(desde, m.groupValues[4].toInt(), hastaCapitulo = m.groupValues[3].toInt())
                            m.groupValues[2].isNotEmpty() -> Parte(desde, m.groupValues[2].toInt())
                            else -> Parte(desde, desde)
                        }
                    }
                    grupos += Grupo(capitulo, partes)
                }
                return if (grupos.isEmpty()) null else Cita(grupos)
            }
        }
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
