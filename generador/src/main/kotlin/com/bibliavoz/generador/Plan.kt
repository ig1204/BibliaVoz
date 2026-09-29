package com.bibliavoz.generador

import com.bibliavoz.app.voz.Anuncios
import com.bibliavoz.app.voz.Director
import com.bibliavoz.app.voz.Segmentador
import java.security.MessageDigest

/** Un archivo de audio: el guion de los versículos [desde]..[hasta] (base 0). */
class Pieza(
    val archivo: String,
    val texto: String,
    val desde: Int,
    val hasta: Int,
    val inicios: FloatArray,
)

/** Un capítulo o una lectura de la misa: la app solo lo usa si están todas sus piezas. */
class Unidad(
    /** `capitulos` o `lecturas`: el grupo del manifiesto. */
    val grupo: String,
    /** Cómo lo busca la app: `rv/<libro>/<capítulo>` o la clave de la lectura. */
    val clave: String,
    val nombre: String,
    val piezas: List<Pieza>,
)

/**
 * Arma los guiones igual que los armaba la app cuando pedía el audio en vivo:
 * mismo reparto en tramos, mismo director, mismas cabeceras.
 */
object Plan {

    const val GRUPO_CAPITULOS = "capitulos"
    const val GRUPO_LECTURAS = "lecturas"

    /**
     * Va en el nombre del archivo: si cambia el guion de un tramo (otras reglas
     * del director, otro reparto), cambia el nombre y se genera de nuevo, en
     * vez de reutilizar por error el audio viejo.
     */
    fun huella(texto: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(texto.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    fun capitulo(libro: Libro, numero: Int, extension: String): Unidad {
        val versos = libro.capitulos[numero - 1]
        val piezas = Segmentador.segmentar(versos).map { tramo ->
            val guion = Director.narrar(
                versos = versos.subList(tramo.desde, tramo.hasta + 1),
                anterior = versos.getOrNull(tramo.desde - 1),
                cabecera = if (tramo.desde == 0) Anuncios.capitulo(libro.nombre, numero) else null,
                conEmociones = true,
                libro = libro.nombre,
            )
            Pieza(
                "rv-${libro.numero}-$numero-${tramo.desde}-${huella(guion.texto)}.$extension",
                guion.texto, tramo.desde, tramo.hasta, guion.inicios,
            )
        }
        return Unidad(GRUPO_CAPITULOS, "rv/${libro.numero}/$numero", "${libro.nombre} $numero", piezas)
    }

    fun lectura(lectura: Lectura, catolica: Biblia, extension: String): Unidad? {
        val versiculos = lectura.versiculos(catolica)
        if (versiculos.isEmpty()) return null
        val nombreLibro = catolica.nombre(lectura.libro)
        val piezas = Segmentador.segmentar(versiculos).map { tramo ->
            val guion = Director.narrar(
                versos = versiculos.subList(tramo.desde, tramo.hasta + 1),
                anterior = versiculos.getOrNull(tramo.desde - 1),
                cabecera = if (tramo.desde == 0) Anuncios.lectura(lectura.titulo, nombreLibro, lectura.cita) else null,
                conEmociones = true,
                libro = nombreLibro,
            )
            Pieza(
                "misa-${lectura.clave}-${tramo.desde}-${huella(guion.texto)}.$extension",
                guion.texto, tramo.desde, tramo.hasta, guion.inicios,
            )
        }
        return Unidad(GRUPO_LECTURAS, lectura.clave, "${lectura.titulo}: ${lectura.cita}", piezas)
    }
}
