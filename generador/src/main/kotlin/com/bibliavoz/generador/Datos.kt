package com.bibliavoz.generador

import com.bibliavoz.app.liturgia.Precedencia
import com.bibliavoz.app.voz.ClaveLectura
import com.bibliavoz.app.voz.Tramos
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class Libro(val numero: Int, val nombre: String, val capitulos: List<List<String>>)

/** Una Biblia tal como la empaqueta la app (`assets/bible` o `assets/bible-cat`). */
class Biblia(val libros: List<Libro>) {

    /** Como `BibleRepository.parsedBook`: un número fuera de rango se recorta. */
    fun texto(numero: Int): Libro = libros[numero.coerceIn(1, libros.size) - 1]

    /** Como `BibleRepository.book(n).name`: si el libro no existe, el primero. */
    fun nombre(numero: Int): String = (libros.firstOrNull { it.numero == numero } ?: libros.first()).nombre

    companion object {
        fun cargar(carpeta: File): Biblia {
            val index = JSONObject(File(carpeta, "index.json").readText(Charsets.UTF_8))
            val books = index.getJSONArray("books")
            val libros = (0 until books.length()).map { i ->
                val info = books.getJSONObject(i)
                val n = info.getInt("n")
                val raiz = JSONObject(File(carpeta, "$n.json").readText(Charsets.UTF_8))
                val chapters = raiz.getJSONArray("chapters")
                Libro(
                    numero = n,
                    nombre = info.getString("name"),
                    capitulos = (0 until chapters.length()).map { c ->
                        val versos = chapters.getJSONArray(c)
                        (0 until versos.length()).map { versos.getString(it) }
                    },
                )
            }
            return Biblia(libros)
        }
    }
}

/** Una lectura de la misa, leída del leccionario igual que en la app. */
class Lectura(val titulo: String, val cita: String, val libro: Int, val tramos: List<IntArray>) {
    val clave: String = ClaveLectura.de(titulo, libro, tramos)

    /** Sus versículos, resueltos con la misma función que usa la app ([Tramos]). */
    fun versiculos(catolica: Biblia): List<String> {
        val capitulos = catolica.texto(libro).capitulos
        val out = ArrayList<String>()
        for (t in tramos) {
            runCatching { out.addAll(Tramos.versos(capitulos, t[0], t[1], t[2], t[3])) }
        }
        return out
    }
}

/** El leccionario de la app (`assets/liturgia/leccionario.json`). */
class Leccionario(archivo: File) {

    private val raiz = JSONObject(archivo.readText(Charsets.UTF_8))
    private val fijas = raiz.optJSONObject("fijas") ?: JSONObject()
    private val temporales = raiz.optJSONObject("temporales") ?: JSONObject()
    private val celebraciones = Precedencia.celebraciones(raiz)

    /**
     * Las lecturas de un día, elegidas como en la app: la misma [Precedencia]
     * decide si manda la fiesta de fecha fija o el tiempo litúrgico.
     */
    fun delDia(fecha: LocalDate): List<Lectura> {
        val dia = Precedencia.dia(fecha, celebraciones)
        val delTiempo = temporales.optJSONArray(Precedencia.claveLecturasDelTiempo(temporales, dia))
            ?.let { parse(it) } ?: emptyList()
        val deLaFiesta = Precedencia.claveLecturasFija(fijas, dia)
            ?.let { fijas.optJSONArray(it) }
            ?.let { parse(it) } ?: emptyList()
        return Precedencia.combinar(delTiempo, deLaFiesta, dia.fija?.grado) { it.titulo }
    }

    /** Todas las lecturas distintas del leccionario, en un orden estable. */
    fun todas(): List<Lectura> {
        val vistas = LinkedHashMap<String, Lectura>()
        for (grupo in listOf(fijas, temporales)) {
            for (clave in grupo.keySet().sorted()) {
                val array = grupo.optJSONArray(clave) ?: continue
                for (lectura in parse(array)) vistas.putIfAbsent(lectura.clave, lectura)
            }
        }
        return vistas.values.toList()
    }

    /** Copia de `Leccionario.parse` de la app: mismas lecturas, mismos tramos. */
    private fun parse(array: JSONArray): List<Lectura> = buildList {
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val tramosArray = o.optJSONArray("r") ?: continue
            val tramos = buildList(tramosArray.length()) {
                for (j in 0 until tramosArray.length()) {
                    val t = tramosArray.optJSONArray(j) ?: continue
                    if (t.length() >= 4) add(intArrayOf(t.getInt(0), t.getInt(1), t.getInt(2), t.getInt(3)))
                }
            }
            if (tramos.isEmpty()) continue
            add(Lectura(o.optString("t"), o.optString("c"), o.optInt("b"), tramos))
        }
    }
}
