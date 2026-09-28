package com.bibliavoz.app.player

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * La voz IA ya grabada e instalada en el teléfono: suena sin internet.
 *
 * El audio lo genera una sola vez, en la PC, el programa `generador` (Fish
 * Audio, voz Hilary narrador) y se copia por cable a la carpeta
 * `Android/data/com.bibliavoz.app/files/voz-ia`. Un `manifest.json` dice qué
 * capítulos y qué lecturas de la misa tienen audio, en qué archivos, y en qué
 * punto de cada archivo empieza cada versículo.
 */
class AudioLocal(context: Context) {

    /** Un archivo de audio: los versículos [desde]..[hasta] (base 0) de un capítulo o de una lectura. */
    class Tramo(
        val desde: Int,
        val hasta: Int,
        val archivo: File,
        /** Fracción del archivo en que empieza cada versículo del tramo. */
        val inicios: FloatArray,
    ) {
        operator fun contains(verso: Int): Boolean = verso in desde..hasta
    }

    /** Lo que hay instalado, para mostrarlo en ajustes. */
    data class Resumen(
        val capitulos: Int = 0,
        val lecturas: Int = 0,
        val bytes: Long = 0L,
        val voz: String = "",
        val modelo: String = "",
    )

    private val appContext = context.applicationContext

    @Volatile private var capitulos: Map<String, List<Tramo>> = emptyMap()
    @Volatile private var lecturas: Map<String, List<Tramo>> = emptyMap()

    @Volatile
    var resumen: Resumen = Resumen()
        private set

    private var firmaLeida: Long = Long.MIN_VALUE

    /**
     * Carpeta del audio. Pedirla hace que Android la cree con los permisos de
     * la app, así la app puede leer lo que después se copie ahí por cable.
     */
    fun carpeta(): File? = appContext.getExternalFilesDir(CARPETA)

    /** Relee el manifiesto si cambió. Hace entrada/salida: llámalo fuera del hilo principal. */
    @Synchronized
    fun recargarSiCambio() {
        val dir = carpeta() ?: return
        val manifiesto = File(dir, MANIFIESTO)
        val firma = if (manifiesto.isFile) manifiesto.lastModified() xor manifiesto.length() else 0L
        if (firma == firmaLeida) return
        firmaLeida = firma
        if (!manifiesto.isFile) {
            vaciar()
            return
        }
        runCatching {
            val raiz = JSONObject(manifiesto.readText(Charsets.UTF_8))
            val caps = leerGrupo(raiz.optJSONObject("capitulos"), dir)
            val lecs = leerGrupo(raiz.optJSONObject("lecturas"), dir)
            capitulos = caps
            lecturas = lecs
            resumen = Resumen(
                capitulos = caps.size,
                lecturas = lecs.size,
                bytes = raiz.optLong("bytes"),
                voz = raiz.optString("vozNombre"),
                modelo = raiz.optString("modeloNombre"),
            )
        }.onFailure { vaciar() }
    }

    private fun vaciar() {
        capitulos = emptyMap()
        lecturas = emptyMap()
        resumen = Resumen()
    }

    private fun leerGrupo(obj: JSONObject?, dir: File): Map<String, List<Tramo>> {
        if (obj == null) return emptyMap()
        val out = HashMap<String, List<Tramo>>(obj.length() * 2)
        val claves = obj.keys()
        while (claves.hasNext()) {
            val clave = claves.next()
            val arr = obj.getJSONArray(clave)
            val tramos = ArrayList<Tramo>(arr.length())
            for (i in 0 until arr.length()) {
                val t = arr.getJSONArray(i)
                val ini = t.getJSONArray(3)
                tramos.add(
                    Tramo(
                        desde = t.getInt(0),
                        hasta = t.getInt(1),
                        archivo = File(dir, t.getString(2)),
                        inicios = FloatArray(ini.length()) { ini.getDouble(it).toFloat() },
                    )
                )
            }
            out[clave] = tramos
        }
        return out
    }

    /**
     * Tramos con voz IA de un capítulo de la Reina-Valera, o `null` si no los
     * hay. Solo vale si cubren justo los [versos] del capítulo: si no casaran,
     * el resaltado y el avance irían desfasados.
     */
    fun capitulo(libro: Int, capitulo: Int, versos: Int): List<Tramo>? =
        capitulos["rv/$libro/$capitulo"]?.takeIf { completos(it, versos) }

    /** Lo mismo para una lectura de la misa, por su clave ([com.bibliavoz.app.voz.ClaveLectura]). */
    fun lectura(clave: String, versos: Int): List<Tramo>? =
        if (clave.isEmpty()) null else lecturas[clave]?.takeIf { completos(it, versos) }

    private fun completos(tramos: List<Tramo>, versos: Int): Boolean {
        if (tramos.isEmpty() || tramos.first().desde != 0 || tramos.last().hasta != versos - 1) return false
        // El manifiesto pudo llegar al teléfono antes que algún archivo.
        return tramos.all { it.archivo.isFile }
    }

    companion object {
        const val CARPETA = "voz-ia"
        const val MANIFIESTO = "manifest.json"
    }
}
