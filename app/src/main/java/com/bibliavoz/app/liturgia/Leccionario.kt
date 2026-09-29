package com.bibliavoz.app.liturgia

import android.content.Context
import com.bibliavoz.app.data.BibleRepository
import com.bibliavoz.app.data.BibleVersion
import com.bibliavoz.app.voz.Anuncios
import com.bibliavoz.app.voz.ClaveLectura
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Una lectura de la misa, con su cita y el tramo de versículos que abarca. */
data class Lectura(
    val titulo: String,
    val cita: String,
    val libro: Int,
    val tramos: List<IntArray>,
)

/** Las lecturas de un día concreto. */
data class LecturasDelDia(
    val fecha: LocalDate,
    val descripcion: String,
    val lecturas: List<Lectura>,
)

/** Una lectura ya resuelta a texto, lista para mostrar o leer en voz alta. */
data class LecturaConTexto(
    val titulo: String,
    val cita: String,
    val versiculos: List<String>,
    /** Identifica la lectura para encontrar su audio de voz IA ([ClaveLectura]). */
    val clave: String = "",
    /** Lo que se dice antes de leerla ([Anuncios.lectura]), igual que la voz IA. */
    val anuncio: String = "$titulo. $cita.",
)

/**
 * Tabla de lecturas de la misa empaquetada en `assets/liturgia/leccionario.json`.
 *
 * No está indexada por fecha —eso caducaría— sino por CLAVE LITÚRGICA, que
 * [CalendarioLiturgico] sabe calcular para cualquier día. Las fiestas de fecha
 * fija (santoral) van en una tabla aparte, con su nombre y su grado, y
 * [Precedencia] decide cuándo le ganan al día.
 */
class Leccionario private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val raiz: JSONObject by lazy {
        JSONObject(
            appContext.assets.open("liturgia/leccionario.json").use {
                it.bufferedReader(Charsets.UTF_8).readText()
            }
        )
    }

    private val fijas: JSONObject by lazy { raiz.optJSONObject("fijas") ?: JSONObject() }
    private val temporales: JSONObject by lazy { raiz.optJSONObject("temporales") ?: JSONObject() }
    private val celebraciones: Map<String, Celebracion> by lazy { Precedencia.celebraciones(raiz) }

    /** Lecturas del día, o `null` si ese día no está cubierto por la tabla. */
    fun delDia(fecha: LocalDate): LecturasDelDia? {
        val dia = Precedencia.dia(fecha, celebraciones)
        val delTiempo = temporales.optJSONArray(Precedencia.claveLecturasDelTiempo(temporales, dia))
            ?.let { parse(it) } ?: emptyList()
        val deLaFiesta = Precedencia.claveLecturasFija(fijas, dia)
            ?.let { fijas.optJSONArray(it) }
            ?.let { parse(it) } ?: emptyList()

        val lecturas = Precedencia.combinar(delTiempo, deLaFiesta, dia.fija?.grado) { it.titulo }
        if (lecturas.isEmpty()) return null
        return LecturasDelDia(
            fecha = fecha,
            // Si la fiesta no tuviera lecturas se leería el día: la cabecera, también.
            descripcion = if (deLaFiesta.isEmpty()) CalendarioLiturgico.descripcion(fecha) else dia.descripcion,
            lecturas = lecturas,
        )
    }

    private fun parse(array: JSONArray): List<Lectura> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tramosArray = o.optJSONArray("r") ?: continue
            val tramos = buildList(tramosArray.length()) {
                for (j in 0 until tramosArray.length()) {
                    val t = tramosArray.optJSONArray(j) ?: continue
                    if (t.length() >= 4) {
                        add(intArrayOf(t.getInt(0), t.getInt(1), t.getInt(2), t.getInt(3)))
                    }
                }
            }
            if (tramos.isEmpty()) continue
            add(
                Lectura(
                    titulo = o.optString("t"),
                    cita = o.optString("c"),
                    libro = o.optInt("b"),
                    tramos = tramos,
                )
            )
        }
    }

    /**
     * Resuelve las citas a texto usando la versión de canon católico, que es la
     * única que tiene los libros deuterocanónicos que el leccionario utiliza.
     */
    fun conTexto(dia: LecturasDelDia): List<LecturaConTexto> {
        val repo = BibleRepository.get(appContext, BibleVersion.CATOLICA)
        return dia.lecturas.map { lectura ->
            val versiculos = ArrayList<String>()
            for (t in lectura.tramos) {
                runCatching {
                    versiculos.addAll(repo.verses(lectura.libro, t[0], t[1], t[2], t[3]))
                }
            }
            LecturaConTexto(
                titulo = lectura.titulo,
                cita = lectura.cita,
                versiculos = versiculos,
                clave = ClaveLectura.de(lectura.titulo, lectura.libro, lectura.tramos),
                anuncio = Anuncios.lectura(
                    lectura.titulo,
                    runCatching { repo.book(lectura.libro).name }.getOrDefault(""),
                    lectura.cita,
                ),
            )
        }
    }

    companion object {
        @Volatile
        private var instance: Leccionario? = null

        fun get(context: Context): Leccionario =
            instance ?: synchronized(this) {
                instance ?: Leccionario(context).also { instance = it }
            }
    }
}
