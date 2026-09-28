package com.bibliavoz.app.voz

import com.bibliavoz.app.voz.NumerosEnLetras.enLetras

/**
 * El «director de escena» de la voz IA: convierte versículos en el guion que
 * se le manda a Fish Audio.
 *
 * Hace dos cosas:
 *
 * 1. **Limpia** lo que la voz leería mal: la ortografía de 1909 («á sus pies»,
 *    «fué»), las mayúsculas de inicio de capítulo («EN el principio»), las
 *    cifras con coma de millares y los corchetes del texto, que el modelo
 *    tomaría por etiquetas.
 * 2. Si está activado el estilo novela, **pone etiquetas de emoción** donde el
 *    propio texto las pide: «lloró», «clamó a gran voz», «una voz de los
 *    cielos», «dijo Dios»…
 *
 * Las etiquetas van en inglés porque así las aprendió el modelo S2: son las de
 * su biblioteca de emociones ([sad], [shouting], [whisper]…), más algunas
 * descripciones libres que el modelo también acepta ([solemn tone]).
 *
 * Es conservador a propósito: una etiqueta de más suena a teatro, y la voz ya
 * entona sola las preguntas y las exclamaciones.
 */
object Director {

    /** Súbelo al cambiar las reglas: forma parte de la clave de la caché de audio. */
    const val VERSION = 1

    /** Guion listo para la voz y el punto del audio en que empieza cada versículo. */
    class Guion(
        val texto: String,
        /** Fracción (0..1) del tramo en que empieza cada versículo, para resaltarlo en pantalla. */
        val inicios: FloatArray,
    )

    /**
     * Biblioteca de emociones. [nombre] es cómo aparece en la lista de Fish
     * Audio en español; [etiqueta], lo que de verdad entiende el modelo.
     */
    enum class Emocion(val nombre: String, val etiqueta: String) {
        ECO("eco", "[echo]"),
        LLANTO("triste", "[sad]"),
        GRITO("gritando", "[shouting]"),
        IRA("enojado", "[angry]"),
        MIEDO("con miedo", "[fearful]"),
        SUSURRO("susurro", "[whisper]"),
        BURLA("burlón", "[mocking tone]"),
        RISA("risita", "[chuckling]"),
        ASOMBRO("sorprendido", "[surprised]"),
        CONMOCION("conmocionado", "[shocked]"),
        ALEGRIA("deleite", "[delight]"),
        SUPLICA("suplicante", "[pleading tone]"),
        TERNURA("tierno", "[tender tone]"),
        SUSPIRO("suspiro", "[sigh]"),
        FIRMEZA("firme", "[firm tone]"),
        ORACION("reverente", "[reverent tone]"),
        SOLEMNE("solemne", "[solemn tone]"),
    }

    const val PAUSA = "[pause]"

    /** Todas las etiquetas que puede escribir el director; ninguna otra debe llegar al modelo. */
    val ETIQUETAS: Set<String> = Emocion.entries.map { it.etiqueta }.toSet() + PAUSA

    /** Lo que «pesa» una pausa en el reparto del tiempo del tramo, medido en letras. */
    private const val PESO_PAUSA = 12f

    /** Letras hacia atrás en que se buscan pistas antes de que alguien hable. */
    private const val VENTANA = 140

    // --------------------------------------------------------------- limpieza

    // `\b` y `\w` son solo ASCII en Java y Unicode en Android: con ellos la
    // misma regla daría resultados distintos en los tests y en el teléfono.
    // Por eso los límites de palabra se escriben a mano con \p{L}.
    private const val LETRA = "\\p{L}"

    private val ESPACIOS = Regex("\\s+")
    private val MAYUSCULAS = Regex("(?<!$LETRA)\\p{Lu}{2,}(?!$LETRA)")
    private val VOCAL_SUELTA = Regex("(?<!$LETRA)[áéóúÁÉÓÚ](?!$LETRA)")
    private val MONOSILABOS = Regex("(?<!$LETRA)(?:[Ff]u[éí]|[DdVv]i[óí]|[DdVv]í)(?!$LETRA)")

    /** Un versículo tal como debe leerlo la voz, todavía sin etiquetas. */
    fun limpiar(verso: String): String {
        if (verso.isBlank()) return ""
        var t = verso.replace('[', ' ').replace(']', ' ').replace('¶', ' ')
        t = MAYUSCULAS.replace(t) { it.value.first() + it.value.substring(1).lowercase() }
        t = VOCAL_SUELTA.replace(t) { sinTilde(it.value) }
        t = MONOSILABOS.replace(t) { sinTilde(it.value) }
        t = NumerosEnLetras.reemplazarCifras(t)
        return ESPACIOS.replace(t, " ").trim()
    }

    private fun sinTilde(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'
                    'Á' -> 'A'; 'É' -> 'E'; 'Í' -> 'I'; 'Ó' -> 'O'; 'Ú' -> 'U'
                    else -> c
                }
            )
        }
    }

    // --------------------------------------------------------------- pistas

    private fun rx(patron: String) = Regex(patron, RegexOption.IGNORE_CASE)

    /** La palabra entera. */
    private fun palabra(p: String) = "(?<!$LETRA)(?:$p)(?!$LETRA)"

    /** Cualquier palabra que empiece así. */
    private fun raiz(p: String) = "(?<!$LETRA)(?:$p)$LETRA*"

    private const val DIVINO = "(?:Jehová|Yahvé|Dios|el\\s+Señor)"

    private val LLANTO = rx(
        raiz("llor|lágrima|lament|endech|enlutad|plañ|solloz") + "|" + palabra("luto|amargamente")
    )

    private val GRITO = rx(
        "a\\s+gran(?:de)?\\s+voz|con\\s+gran(?:de)?\\s+voz|en\\s+alta\\s+voz|a\\s+voz\\s+en\\s+grito|a\\s+gritos" +
            "|" + palabra("dando|daban|dieron|dio|dar") + "\\s+voces" +
            "|con\\s+mucha\\s+fuerza|con\\s+voz\\s+fuerte" +
            "|" + palabra("alzó|alzaron|alzando") + "\\s+(?:su|la|sus)\\s+voz(?:es)?" +
            "|" + raiz("grit|vocifer|exclam") +
            // «Clamar a Dios» es súplica, no grito: se queda para ORACION.
            "|" + palabra("clam(?:ó|aron|aba|aban|ando|ad|en)") +
            "(?!\\s+(?:a|al)\\s+(?:Jehová|Yahvé|Dios|Señor|ti|él)(?!$LETRA))"
    )

    /**
     * Alguien SE ENOJA. Los sustantivos sueltos («la ira», «el furor») no
     * cuentan: aparecen igual en «lento para la ira» o «no me reprendas en tu
     * furor», que no se leen con enojo.
     */
    private val IRA = rx(
        palabra(
            "enoj(?:ó|aron|óse|áronse|aba|aban|ado|ados|ada|adas)|air(?:ó|aron|óse|áronse|ado|ados|ada|adas)" +
                "|indign(?:ó|aron|óse|ado|ados|ada|adas)|encoleriz(?:ó|aron|ado|ados|ada)" +
                "|enfurec(?:ió|ieron|ido|idos|ida)|iracund(?:o|a|os|as)|furios(?:o|a|os|as)"
        ) +
            "|(?:se\\s+encendió|encendióse)\\s+(?:el|su|la)\\s+(?:furor|ira|enojo)" +
            "|(?:con|llenos?\\s+de)\\s+(?:ira|furor|enojo|cólera|saña)(?!$LETRA)"
    )

    /** Alguien tiene miedo. «La tierra tembló» o «será por espanto» no son miedo de nadie. */
    private val MIEDO = rx(
        "tuv(?:o|ieron)\\s+(?:gran\\s+|mucho\\s+)?(?:miedo|temor)" +
            "|" + palabra(
                "temi(?:ó|eron|endo)|tembl(?:ando|aba|aban)|miedo|atemorizad(?:o|a|os|as)|despavorid(?:o|a|os|as)" +
                    "|espant(?:ó|aron|óse|áronse|ado|ados|ada|adas)|aterr(?:ó|aron|ado|ados|ada|adas)" +
                    "|aterrorizad(?:o|a|os|as)|horrorizad(?:o|a|os|as)"
            )
    )

    private val SUSURRO = rx(
        "en\\s+secreto|secretamente|en\\s+voz\\s+baja|al\\s+oído|a\\s+escondidas|" + raiz("susurr|cuchiche")
    )

    /** Alguien se burla. «El escarnecedor» de los Proverbios es un tipo de persona, no una burla. */
    private val BURLA = rx(
        palabra(
            "burl(?:aban|aron|ó|ándose|ábanse|arse|ando)|escarneci(?:eron|endo|ó|an|ían|a)" +
                "|mof(?:aban|aron|ó|ándose|arse)|ridiculiz(?:aban|aron|ó|ando)"
        ) +
            "|(?:hacían|hicieron|hacía)\\s+escarnio|se\\s+(?:rieron|reían|burlaban)\\s+de" +
            "|" + palabra("mene(?:ando|aron|aban)") + "\\s+(?:sus\\s+|la\\s+)?cabezas?"
    )

    private val RISA = rx(
        palabra("rió|rióse|se\\s+rió|rieron|riendo|reír|reírse|risa|risas|carcajadas?") + "|" + raiz("sonri")
    )

    private val ASOMBRO = rx(
        palabra(
            "maravill(?:ó|aron|aba|aban|ados|adas|ándose|óse|áronse)" +
                "|asombr(?:ó|aron|aba|aban|ados|adas|ándose)" +
                "|atónit(?:o|a|os|as)|pasmad(?:o|a|os|as)|pasm(?:ó|aron)" +
                "|admir(?:ó|aron|aban|ados|adas)|estupefact(?:o|a|os|as)"
        )
    )

    /** Asombro que llega a conmoción. */
    private val INTENSO = rx("en\\s+gran\\s+manera|sobremanera|en\\s+extremo|fuera\\s+de\\s+sí|muchísimo|grandemente")

    private val ALEGRIA = rx(raiz("goz|alegr|regocij|jubil|júbil") + "|" + palabra("contentos?|contentas?"))

    /** «No hay alegría», «cesó el gozo»: nombrar la alegría para decir que se acabó. */
    private val ALEGRIA_NEGADA = rx(
        palabra("no|sin|ni|cesó|cesará|cesar|quitaré|quitó|acabó|pereció|secó|apartó|faltó|faltará") +
            "\\s+(?:(?:hay|había|habrá|el|la|los|las|su|sus|tu|tus|mi|mis|de|toda|todo)\\s+){0,3}" +
            raiz("goz|alegr|regocij")
    )

    /** Compasión: «fue movido a misericordia», «se compadeció». */
    private val TERNURA = rx(
        "movid[oa]s?\\s+a\\s+(?:misericordia|compasión)|tuv(?:o|ieron)\\s+(?:compasión|misericordia)" +
            "|con\\s+ternura|" + palabra("compadeci(?:ó|eron|óse|do|dos|da)|se\\s+compadeció") +
            "|" + raiz("enterneci")
    )

    private val SUPLICA = rx(
        raiz("suplic|implor") + "|" + palabra("rog(?:ó|aba|aban|aron|ando)|rogándole|rogábale|rogóle|rogáronle")
    )

    private val SUSPIRO = rx(raiz("suspir|gemid") + "|" + palabra("gimió|gemía|gemían|gimiendo|gemir|gimieron"))

    private val FIRMEZA = rx(raiz("reprend|increp|amonest|conmin"))

    private val ORACION = rx(
        palabra("oró|oraba|oraban|orando|oraron|orad|oración|oraciones") +
            "|de\\s+rodillas|" + raiz("arrodill") +
            "|" + palabra("postr(?:ó|aron|ándose|óse|áronse|ado|ados|ada|adas)") +
            "|" + palabra("clam(?:ó|aron|aba|aban|ando)") + "\\s+(?:a|al)\\s+(?:Jehová|Yahvé|Dios|Señor)"
    )

    /**
     * Dios es quien habla: «dijo Dios», «Jehová dijo», «así dice Jehová»,
     * «palabra de Jehová a…». «Llamó Dios a la luz Día» no cuenta: es nombrar.
     */
    private val DIOS_HABLA = rx(
        palabra("dijo|díjole|dice|ha\\s+dicho|habló|hablóle|hablaba|respondió|respondióle|mandó|juró|ordenó") +
            "\\s+(?:le\\s+|les\\s+|me\\s+|nos\\s+)?$DIVINO(?!$LETRA)" +
            "|(?<!$LETRA)$DIVINO\\s+(?:Dios\\s+)?(?:de\\s+los\\s+ejércitos\\s+)?(?:le\\s+|les\\s+|me\\s+|nos\\s+|te\\s+)?" +
            "(?:dijo|habló|respondió|dice|ha\\s+dicho|mandó|juró|ordenó)(?!$LETRA)" +
            "|palabra\\s+de\\s+$DIVINO\\s+(?:a|que|vino|fue)(?!$LETRA)|oráculo\\s+de\\s+$DIVINO"
    )

    /** Voz que viene del cielo: se le pone eco. */
    private val VOZ_DEL_CIELO = rx(
        palabra("voz") + "\\s+(?:que\\s+(?:vino|salía|salió)\\s+)?(?:de|del|desde)\\s+" +
            "(?:los\\s+cielos|el\\s+cielo|cielo|la\\s+nube|el\\s+trono)"
    )

    /** Verbos con que alguien toma la palabra; en la Reina-Valera abren el discurso con dos puntos. */
    private val HABLA = rx(
        raiz(
            "dij|díj|dic|díc|decí|respond|pregunt|clam|grit|exclam|habl|contest|replic|añad|profetiz" +
                "|bendij|maldij|jur|mand|orden|cant|lament|suplic|rog|or[óa]|escrib|escrit|pregon|proclam"
        ) + "|" + palabra("oró|oraba|orando|oraron|orad")
    )

    /** Salmos de alabanza: «Alabad a Dios…», «Aleluya». */
    private val ALABANZA = rx("^\\s*[¡¿]?\\s*(?:alabad|aleluya|cantad|aclamad|regocijaos|alegraos|bendecid|load)(?!$LETRA)")

    private val AY_DE_MI = rx("(?<!$LETRA)ay\\s+de\\s+mí(?!$LETRA)")

    /** Pistas para cuando alguien habla, por orden de prioridad. */
    private val PISTAS_HABLA: List<Pair<Emocion, Regex>> = listOf(
        Emocion.ECO to VOZ_DEL_CIELO,
        Emocion.LLANTO to LLANTO,
        Emocion.GRITO to GRITO,
        Emocion.IRA to IRA,
        Emocion.MIEDO to MIEDO,
        Emocion.SUSURRO to SUSURRO,
        Emocion.BURLA to BURLA,
        Emocion.RISA to RISA,
        Emocion.ASOMBRO to ASOMBRO,
        Emocion.ALEGRIA to ALEGRIA,
        Emocion.SUPLICA to SUPLICA,
        Emocion.TERNURA to TERNURA,
        Emocion.SUSPIRO to SUSPIRO,
        Emocion.FIRMEZA to FIRMEZA,
        Emocion.ORACION to ORACION,
        Emocion.SOLEMNE to DIOS_HABLA,
    )

    /**
     * Pistas para la narración sin diálogo. Solo las que un narrador de verdad
     * dejaría notar en la voz: nunca grita, susurra ni pone eco al narrar.
     */
    private val PISTAS_NARRACION: List<Pair<Emocion, Regex>> = listOf(
        Emocion.LLANTO to LLANTO,
        Emocion.IRA to IRA,
        Emocion.MIEDO to MIEDO,
        Emocion.BURLA to BURLA,
        Emocion.RISA to RISA,
        Emocion.ASOMBRO to ASOMBRO,
        Emocion.ALEGRIA to ALEGRIA,
        Emocion.SUSPIRO to SUSPIRO,
        Emocion.TERNURA to TERNURA,
    )

    // --------------------------------------------------------------- etiquetas

    /** Emoción que pasa al versículo siguiente. */
    private class Arrastre(
        val emocion: Emocion,
        /**
         * `true` si alguien empezó a hablar al final del versículo anterior
         * («…diciendo:») y sus palabras son las de este: la etiqueta va al principio.
         */
        val alInicio: Boolean,
    )

    private class Etiquetado(val texto: String, val arrastre: Arrastre?)

    /** Punto donde alguien empieza a hablar, con el texto que lo precede. */
    private class Apertura(val posicion: Int, val ventana: String)

    private fun aperturas(t: String): List<Apertura> {
        val out = ArrayList<Apertura>()
        var inicioVentana = 0
        var enCita = false
        for (i in t.indices) {
            when (t[i]) {
                '“', '«' -> if (!enCita) {
                    out.add(Apertura(i, t.substring(maxOf(inicioVentana, i - VENTANA), i)))
                    enCita = true
                }
                '”', '»' -> if (enCita) {
                    enCita = false
                    inicioVentana = i + 1
                }
                // La Reina-Valera no usa comillas: el discurso lo abren los dos
                // puntos detrás de un verbo de habla. Si detrás vienen comillas,
                // la apertura la marcan ellas.
                ':' -> if (!enCita) {
                    val resto = t.substring(i + 1).trimStart()
                    if (!resto.startsWith('“') && !resto.startsWith('«')) {
                        val ventana = t.substring(maxOf(inicioVentana, i - VENTANA), i)
                        if (HABLA.containsMatchIn(ventana)) {
                            out.add(Apertura(i + 1, ventana))
                            inicioVentana = i + 1
                        }
                    }
                }
            }
        }
        return out
    }

    private fun emocionDeHabla(ventana: String): Emocion? {
        for ((emocion, pista) in PISTAS_HABLA) {
            if (!pista.containsMatchIn(ventana)) continue
            if (emocion == Emocion.ALEGRIA && ALEGRIA_NEGADA.containsMatchIn(ventana)) continue
            return if (emocion == Emocion.ASOMBRO && INTENSO.containsMatchIn(ventana)) Emocion.CONMOCION else emocion
        }
        return null
    }

    /** Emoción de un versículo sin diálogo, y dónde empieza la frase que la lleva. */
    private fun emocionNarrada(t: String): Pair<Int, Emocion>? {
        if (ALABANZA.containsMatchIn(t)) return 0 to Emocion.ALEGRIA
        AY_DE_MI.find(t)?.let { return inicioDeFrase(t, it.range.first) to Emocion.SUSPIRO }
        for ((emocion, pista) in PISTAS_NARRACION) {
            val m = pista.find(t) ?: continue
            if (emocion == Emocion.ALEGRIA && ALEGRIA_NEGADA.containsMatchIn(t)) continue
            val elegida = if (emocion == Emocion.ASOMBRO && INTENSO.containsMatchIn(t)) Emocion.CONMOCION else emocion
            return inicioDeFrase(t, m.range.first) to elegida
        }
        return null
    }

    private fun inicioDeFrase(t: String, desde: Int): Int {
        var i = desde - 1
        while (i >= 0) {
            if (t[i] == '.' || t[i] == ';' || t[i] == '!' || t[i] == '?') return i + 1
            i--
        }
        return 0
    }

    /** `true` si el versículo termina la frase; si no, la frase sigue en el siguiente. */
    fun cierraFrase(t: String): Boolean {
        val ultimo = t.trimEnd().lastOrNull() ?: return false
        return ultimo in ".!?”»’)"
    }

    private fun etiquetar(t: String, arrastre: Arrastre?): Etiquetado {
        val aperturas = aperturas(t)
        val inserciones = ArrayList<Pair<Int, Emocion>>()
        var siguiente: Arrastre? = null

        // Alguien empezó a hablar al final del versículo anterior («…diciendo:»):
        // sus palabras empiezan aquí.
        if (arrastre != null && arrastre.alInicio && (aperturas.isEmpty() || aperturas[0].posicion > 30)) {
            inserciones.add(0 to arrastre.emocion)
        }

        aperturas.forEachIndexed { i, apertura ->
            var emocion = emocionDeHabla(apertura.ventana)
            // La pista pudo quedar en el versículo anterior: «…se conmovió en espíritu, y se turbó, / y dijo:».
            if (emocion == null && i == 0 && arrastre != null && !arrastre.alInicio && apertura.posicion <= 40) {
                emocion = arrastre.emocion
            }
            if (emocion == null) {
                val inicio = t.substring(apertura.posicion).take(30)
                if (AY_DE_MI.containsMatchIn(inicio)) emocion = Emocion.SUSPIRO
            }
            if (emocion != null) {
                if (t.substring(apertura.posicion).isBlank()) {
                    siguiente = Arrastre(emocion, alInicio = true)
                } else {
                    inserciones.add(apertura.posicion to emocion)
                }
            }
        }

        if (aperturas.isEmpty() && inserciones.isEmpty()) {
            emocionNarrada(t)?.let { (posicion, emocion) ->
                inserciones.add(posicion to emocion)
                if (!cierraFrase(t)) siguiente = Arrastre(emocion, alInicio = false)
            }
        }

        // Como mucho dos por versículo, y nunca la misma dos veces seguidas.
        val elegidas = ArrayList<Pair<Int, Emocion>>()
        for (insercion in inserciones.sortedBy { it.first }) {
            if (elegidas.size >= 2) break
            if (elegidas.lastOrNull()?.second == insercion.second) continue
            elegidas.add(insercion)
        }
        return Etiquetado(insertar(t, elegidas), siguiente)
    }

    private fun insertar(t: String, inserciones: List<Pair<Int, Emocion>>): String {
        if (inserciones.isEmpty()) return t
        val sb = StringBuilder()
        var ultimo = 0
        for ((posicion, emocion) in inserciones) {
            val antes = t.substring(ultimo, posicion).trim()
            if (antes.isNotEmpty()) sb.append(antes).append(' ')
            sb.append(emocion.etiqueta).append(' ')
            ultimo = posicion
        }
        sb.append(t.substring(ultimo).trim())
        return sb.toString().trim()
    }

    // --------------------------------------------------------------- guion

    /**
     * Arma el guion de un tramo de versículos.
     *
     * @param anterior el versículo que precede al tramo, si lo hay: puede dejar
     *   una emoción pendiente («…y habló Jehová a Moisés, diciendo:»).
     * @param primerNumero si es mayor que cero, se anuncia «Versículo N» antes
     *   de cada uno, empezando por ese número.
     * @param libro nombre del libro: Lamentaciones se lee siempre con tristeza.
     */
    fun narrar(
        versos: List<String>,
        anterior: String? = null,
        cabecera: String? = null,
        conEmociones: Boolean = true,
        primerNumero: Int = 0,
        libro: String = "",
    ): Guion {
        val sb = StringBuilder()
        val inicios = FloatArray(versos.size)
        var peso = 0f

        if (!cabecera.isNullOrBlank()) {
            sb.append(cabecera.trim())
            peso += cabecera.trim().length
            if (conEmociones) {
                sb.append(' ').append(PAUSA)
                peso += PESO_PAUSA
            }
        }

        var arrastre: Arrastre? = if (conEmociones && !anterior.isNullOrBlank()) {
            etiquetar(limpiar(anterior), null).arrastre
        } else {
            null
        }
        var primeroConTexto = true

        versos.forEachIndexed { i, crudo ->
            inicios[i] = peso
            val limpio = limpiar(crudo)
            // Los huecos de numeración se guardaron vacíos: no se leen.
            if (limpio.isEmpty()) return@forEachIndexed

            var texto = limpio
            if (conEmociones) {
                val etiquetado = etiquetar(limpio, arrastre)
                texto = etiquetado.texto
                arrastre = etiquetado.arrastre
                if (primeroConTexto && libro.trim() == "Lamentaciones" && '[' !in texto) {
                    texto = "${Emocion.LLANTO.etiqueta} $texto"
                }
            }
            primeroConTexto = false

            val numero = if (primerNumero > 0) "Versículo ${enLetras(primerNumero + i)}. " else ""
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(numero).append(texto)
            peso += numero.length + limpio.length
        }

        val total = if (peso > 0f) peso else 1f
        return Guion(sb.toString(), FloatArray(versos.size) { inicios[it] / total })
    }
}
