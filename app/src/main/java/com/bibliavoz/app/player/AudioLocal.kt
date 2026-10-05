package com.bibliavoz.app.player

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/**
 * La voz IA ya grabada: suena sin internet.
 *
 * El audio lo genera una sola vez, en la PC, el programa `generador` (Fish
 * Audio, voz Hilary narrador). Desde la 2.2 viaja DENTRO del APK, en
 * `assets/voz-ia` y sin comprimir, para que MediaPlayer lo lea directamente del
 * APK sin copiarlo a ningún sitio: cualquier teléfono lo tiene al instalar la app.
 *
 * Sigue valiendo la carpeta `Android/data/com.bibliavoz.app/files/voz-ia`, que se
 * llena por cable con instalar-audio.ps1, para estrenar audio nuevo sin
 * reinstalar. Si los dos sitios tienen el mismo capítulo, manda la generación más
 * reciente. En cada sitio, un `manifest.json` dice qué capítulos y qué lecturas
 * de la misa tienen audio, en qué archivos, y en qué punto de cada archivo
 * empieza cada versículo.
 */
class AudioLocal(context: Context) {

    /**
     * Dónde está el audio de un tramo: en la carpeta copiada por cable
     * ([archivo]) o, si es `null`, dentro del APK (`assets/voz-ia/<nombre>`).
     */
    data class Fuente(val nombre: String, val archivo: File?) {
        val enApk: Boolean get() = archivo == null
    }

    /** Un archivo de audio: los versículos [desde]..[hasta] (base 0) de un capítulo o de una lectura. */
    class Tramo(
        val desde: Int,
        val hasta: Int,
        val fuente: Fuente,
        /** Fracción del archivo en que empieza cada versículo del tramo. */
        val inicios: FloatArray,
    ) {
        operator fun contains(verso: Int): Boolean = verso in desde..hasta
    }

    /**
     * Lo que hay instalado, para mostrarlo en ajustes. [capitulos] y [lecturas]
     * cuentan solo los que tienen TODOS sus archivos; los "declarados" son los
     * que prometen los manifiestos. Si no coinciden, una copia por cable quedó a
     * medias y hay que volver a copiar.
     */
    data class Resumen(
        val capitulos: Int = 0,
        val lecturas: Int = 0,
        val bytes: Long = 0L,
        val voz: String = "",
        val modelo: String = "",
        val capitulosDeclarados: Int = 0,
        val lecturasDeclaradas: Int = 0,
        /** Archivos de audio que nombra un manifiesto y no están. */
        val archivosFaltantes: Int = 0,
        /** El audio viene dentro de la app (no hace falta copiar nada por cable). */
        val enApk: Boolean = false,
    ) {
        val faltanArchivos: Boolean get() = archivosFaltantes > 0
    }

    /** Lo que ofrece un sitio (el APK o la carpeta): sus tramos y qué archivos tiene de verdad. */
    private class Origen(
        val capitulos: Map<String, List<Tramo>> = emptyMap(),
        val lecturas: Map<String, List<Tramo>> = emptyMap(),
        /** Nombres de los archivos que de verdad hay. */
        val presentes: Set<String> = emptySet(),
        val bytes: Long = 0L,
        val voz: String = "",
        val modelo: String = "",
        /** Cuándo se generó ese audio (ISO 8601): sirve para saber cuál es más nuevo. */
        val generado: String = "",
    ) {
        fun completo(tramos: List<Tramo>): Boolean = tramos.all { it.fuente.nombre in presentes }

        /** Todos los archivos que nombra el manifiesto. */
        val referenciados: Set<String> by lazy {
            HashSet<String>().apply {
                for (tramos in capitulos.values) tramos.forEach { add(it.fuente.nombre) }
                for (tramos in lecturas.values) tramos.forEach { add(it.fuente.nombre) }
            }
        }

        val hayAudio: Boolean get() = capitulos.isNotEmpty() || lecturas.isNotEmpty()
    }

    private val appContext = context.applicationContext

    // Ya combinados: solo tramos con todos sus archivos presentes.
    @Volatile private var capitulos: Map<String, List<Tramo>> = emptyMap()
    @Volatile private var lecturas: Map<String, List<Tramo>> = emptyMap()

    @Volatile
    var resumen: Resumen = Resumen()
        private set

    private var apk: Origen? = null
    private var enCarpeta = Origen()

    private var firmaLeida: Long = Long.MIN_VALUE
    private var firmaCarpeta: Long = Long.MIN_VALUE
    private var listadoEn: Long = 0L

    @Volatile private var nomediaHecho = false

    /**
     * Carpeta del audio copiado por cable. Pedirla hace que Android la cree con
     * los permisos de la app, así la app puede leer lo que después se copie ahí.
     * Lleva un `.nomedia` para que las apps de música no muestren los MP3
     * como canciones (Android 7 a 10 también miran esta carpeta).
     */
    fun carpeta(): File? = appContext.getExternalFilesDir(CARPETA)?.also { dir ->
        if (!nomediaHecho) {
            nomediaHecho = true
            runCatching { File(dir, NOMEDIA).createNewFile() }
        }
    }

    /**
     * Lee el audio de la app (la primera vez) y relee el de la carpeta si cambió
     * el manifiesto o la carpeta (o si faltaban archivos: puede que la copia siga
     * en curso). Hace entrada/salida: llámalo fuera del hilo principal.
     */
    @Synchronized
    fun recargarSiCambio() {
        var cambio = false
        if (apk == null) {
            apk = leerApk()
            cambio = true
        }
        if (revisarCarpeta()) cambio = true
        if (cambio) combinar()
    }

    /** El audio que viaja dentro del APK. No cambia mientras la app esté instalada. */
    private fun leerApk(): Origen = runCatching {
        val assets = appContext.assets
        val nombres = assets.list(CARPETA)?.toHashSet() ?: emptySet()
        if (MANIFIESTO !in nombres) return@runCatching Origen()
        val texto = assets.open("$CARPETA/$MANIFIESTO").use { String(it.readBytes(), Charsets.UTF_8) }
        leerManifiesto(JSONObject(texto), carpeta = null, presentes = nombres)
    }.getOrElse { Origen() }

    /** Devuelve `true` si lo que hay en la carpeta cambió desde la última vez. */
    private fun revisarCarpeta(): Boolean {
        val dir = carpeta() ?: return false
        val manifiesto = File(dir, MANIFIESTO)
        val firma = if (manifiesto.isFile) manifiesto.lastModified() xor manifiesto.length() else 0L
        val firmaDir = dir.lastModified()
        val ahora = SystemClock.elapsedRealtime()
        val manifiestoCambio = firma != firmaLeida
        val faltaban = enCarpeta.referenciados.any { it !in enCarpeta.presentes }
        val revisarFaltantes = faltaban && ahora - listadoEn >= REVISAR_FALTANTES_MS
        if (!manifiestoCambio && firmaDir == firmaCarpeta && !revisarFaltantes) return false

        firmaLeida = firma
        firmaCarpeta = firmaDir
        listadoEn = ahora
        if (!manifiesto.isFile) {
            enCarpeta = Origen()
            return true
        }
        val presentes = dir.list()?.toHashSet() ?: emptySet()
        enCarpeta = runCatching {
            leerManifiesto(JSONObject(manifiesto.readText(Charsets.UTF_8)), carpeta = dir, presentes = presentes)
        }.getOrElse { Origen() }
        return true
    }

    private fun leerManifiesto(raiz: JSONObject, carpeta: File?, presentes: Set<String>): Origen {
        // Audio grabado con otra versión del texto (otra Biblia o leccionario) no
        // sirve: diría algo distinto de lo que muestra la pantalla. Se descarta
        // entero. Ver com.bibliavoz.app.voz.TextosIa.
        if (raiz.optString("textos") != com.bibliavoz.app.voz.TextosIa.ID) return Origen()
        val caps = leerGrupo(raiz.optJSONObject("capitulos"), carpeta)
        val lecs = leerGrupo(raiz.optJSONObject("lecturas"), carpeta)
        // Con todo presente vale el tamaño que declara el manifiesto; si falta
        // algo en la carpeta, se suma lo que hay (solo entonces se mira el
        // tamaño de cada archivo).
        val provisional = Origen(caps, lecs, presentes)
        val faltantes = provisional.referenciados.count { it !in presentes }
        val bytes = when {
            faltantes == 0 -> raiz.optLong("bytes")
            carpeta != null -> provisional.referenciados.sumOf { nombre ->
                if (nombre in presentes) File(carpeta, nombre).length() else 0L
            }
            else -> 0L
        }
        return Origen(
            capitulos = caps,
            lecturas = lecs,
            presentes = presentes,
            bytes = bytes,
            voz = raiz.optString("vozNombre"),
            modelo = raiz.optString("modeloNombre"),
            generado = raiz.optString("generado"),
        )
    }

    /**
     * Junta los dos sitios. Por cada capítulo o lectura se queda con el que tenga
     * todos sus archivos; si los dos los tienen, con el de la generación más
     * reciente (a igualdad, el de la app).
     */
    private fun combinar() {
        val deApk = apk ?: Origen()
        val deCarpeta = enCarpeta
        val carpetaMasNueva = deCarpeta.generado > deApk.generado

        fun elegir(a: Map<String, List<Tramo>>, c: Map<String, List<Tramo>>): Map<String, List<Tramo>> {
            val out = HashMap<String, List<Tramo>>((a.size + c.size) * 2)
            for (clave in a.keys + c.keys) {
                val enA = a[clave]?.takeIf { deApk.completo(it) }
                val enC = c[clave]?.takeIf { deCarpeta.completo(it) }
                val elegido = when {
                    enA != null && enC != null -> if (carpetaMasNueva) enC else enA
                    else -> enA ?: enC
                }
                if (elegido != null) out[clave] = elegido
            }
            return out
        }

        val caps = elegir(deApk.capitulos, deCarpeta.capitulos)
        val lecs = elegir(deApk.lecturas, deCarpeta.lecturas)
        capitulos = caps
        lecturas = lecs

        // Lo que falta solo cuenta si ningún sitio lo tiene entero.
        fun faltantes(grupoApk: Map<String, List<Tramo>>, grupoCarpeta: Map<String, List<Tramo>>, hechos: Map<String, List<Tramo>>): Int {
            var n = 0
            for (clave in grupoApk.keys + grupoCarpeta.keys) {
                if (clave in hechos) continue
                val (tramos, origen) = grupoCarpeta[clave]?.let { it to deCarpeta } ?: (grupoApk.getValue(clave) to deApk)
                n += tramos.count { it.fuente.nombre !in origen.presentes }
            }
            return n
        }

        resumen = Resumen(
            capitulos = caps.size,
            lecturas = lecs.size,
            bytes = deApk.bytes + deCarpeta.bytes,
            voz = deApk.voz.ifBlank { deCarpeta.voz },
            modelo = deApk.modelo.ifBlank { deCarpeta.modelo },
            capitulosDeclarados = (deApk.capitulos.keys + deCarpeta.capitulos.keys).size,
            lecturasDeclaradas = (deApk.lecturas.keys + deCarpeta.lecturas.keys).size,
            archivosFaltantes = faltantes(deApk.capitulos, deCarpeta.capitulos, caps) +
                faltantes(deApk.lecturas, deCarpeta.lecturas, lecs),
            enApk = deApk.hayAudio,
        )
    }

    private fun leerGrupo(obj: JSONObject?, carpeta: File?): Map<String, List<Tramo>> {
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
                val nombre = t.getString(2)
                tramos.add(
                    Tramo(
                        desde = t.getInt(0),
                        hasta = t.getInt(1),
                        fuente = Fuente(nombre, carpeta?.let { File(it, nombre) }),
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
        capitulos["rv/$libro/$capitulo"]?.takeIf { cubre(it, versos) }

    /** Lo mismo para una lectura de la misa, por su clave ([com.bibliavoz.app.voz.ClaveLectura]). */
    fun lectura(clave: String, versos: Int): List<Tramo>? =
        if (clave.isEmpty()) null else lecturas[clave]?.takeIf { cubre(it, versos) }

    /**
     * Que los archivos estén se comprobó al combinar, fuera del hilo principal:
     * aquí (que sí corre en el hilo principal) solo se mira que casen los versículos.
     */
    private fun cubre(tramos: List<Tramo>, versos: Int): Boolean =
        tramos.isNotEmpty() && tramos.first().desde == 0 && tramos.last().hasta == versos - 1

    companion object {
        const val CARPETA = "voz-ia"
        const val MANIFIESTO = "manifest.json"
        private const val NOMEDIA = ".nomedia"

        /** Si faltaban archivos, cada cuánto se vuelve a mirar la carpeta aunque no parezca cambiada. */
        private const val REVISAR_FALTANTES_MS = 10_000L
    }
}
