package com.bibliavoz.generador

import com.bibliavoz.app.voz.Director
import com.bibliavoz.app.voz.VocesIa
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Genera el audio de la voz IA de Biblia en Voz, una sola vez y en la PC.
 *
 * Hace lo que sería pegar cada tramo de texto en Fish Audio y descargar su
 * audio, pero sin intervención: unos nueve mil tramos solo de la Reina-Valera.
 * Deja en la carpeta de salida un archivo por tramo y un `manifest.json` que la
 * app lee para saber qué tiene. Se puede cortar y volver a lanzar: lo ya
 * generado no se repite.
 */
fun main(args: Array<String>) {
    System.setOut(PrintStream(FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    val op = Opciones.de(args)
    val assets = File(op.proyecto, "app/src/main/assets")
    val extension = if (op.formato == "opus") "opus" else "mp3"

    val biblia = Biblia.cargar(File(assets, "bible"))
    val catolica = Biblia.cargar(File(assets, "bible-cat"))
    val leccionario = Leccionario(File(assets, "liturgia/leccionario.json"))

    // El plan completo siempre: el manifiesto recoge todo lo que ya esté hecho,
    // aunque esta vez solo se genere una parte.
    val capitulos = biblia.libros.flatMap { libro -> libro.capitulos.indices.map { Plan.capitulo(libro, it + 1, extension) } }
    val lecturas = leccionario.todas().mapNotNull { Plan.lectura(it, catolica, extension) }
    val todo = capitulos + lecturas

    val pedido = seleccionar(op.solo, biblia, catolica, capitulos, lecturas, leccionario, extension)
    op.salida.mkdirs()
    val registro = Registro(File(op.salida, "generador.log"))

    if (op.contar) {
        contar("Todo", todo)
        contar("Pedido (${op.solo})", pedido)
        val faltan = pedido.flatMap { it.piezas }.count { !listo(op.salida, it) }
        println("Faltan por generar: $faltan tramos.")
        return
    }

    val manifiesto = Manifiesto(op.salida, todo, op.formato)
    if (op.limpiar) limpiar(op.salida, todo, registro)
    if (op.soloManifiesto) {
        manifiesto.escribir()
        println("Manifiesto escrito: ${manifiesto.resumen()}")
        return
    }

    val archivoClave = File(op.clave)
    val clave = if (archivoClave.isFile) archivoClave.readText(Charsets.UTF_8).trim().removePrefix("\uFEFF").trim() else ""
    if (clave.isEmpty()) {
        println("Falta la clave de Fish Audio: pégala en ${archivoClave.absolutePath} y guarda el archivo.")
        exitProcess(2)
    }

    val instalador = if (op.instalar) Instalador(File(op.proyecto, "instalar-audio.ps1"), registro) else null
    val ok = Generador(pedido, op, clave, registro, manifiesto, instalador).correr()
    instalador?.instalar()
    exitProcess(if (ok) 0 else 1)
}

/** Borra el audio que ya no corresponde a ningún tramo (de reglas o repartos anteriores). */
private fun limpiar(salida: File, todo: List<Unidad>, registro: Registro) {
    val vigentes = todo.flatMap { u -> u.piezas.map { it.archivo } }.toHashSet()
    val sobrantes = salida.listFiles { f -> (f.name.endsWith(".mp3") || f.name.endsWith(".opus")) && f.name !in vigentes }
        .orEmpty()
    val bytes = sobrantes.sumOf { it.length() }
    sobrantes.forEach { it.delete() }
    registro.escribir("Limpieza: ${sobrantes.size} archivos viejos borrados (${bytes / (1024 * 1024)} MB).")
}

/**
 * Copia al teléfono lo generado (instalar-audio.ps1), si hay uno conectado.
 * Si no lo hay, el script lo dice y no pasa nada: se copiará la próxima vez.
 */
class Instalador(private val script: File, private val registro: Registro) {
    @Synchronized
    fun instalar() {
        if (!script.isFile) return
        registro.escribir("Copiando al teléfono lo generado hasta ahora…")
        runCatching {
            val proceso = ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
                .redirectErrorStream(true)
                .start()
            val salida = proceso.inputStream.bufferedReader().readText().trim()
            proceso.waitFor()
            salida.lines().lastOrNull { it.isNotBlank() }?.let { registro.escribir("   $it") }
        }.onFailure { registro.escribir("   No se pudo copiar: ${it.message}") }
    }
}

private fun contar(titulo: String, unidades: List<Unidad>) {
    val piezas = unidades.flatMap { it.piezas }
    val letras = piezas.sumOf { it.texto.length.toLong() }
    // Unas 14 o 15 letras por segundo de voz narrada, medido con las muestras.
    val horas = letras / 14.5 / 3600
    println("$titulo: ${unidades.size} capítulos o lecturas, ${piezas.size} tramos, $letras letras, unas ${"%.0f".format(horas)} horas de audio.")
}

/**
 * Qué generar esta vez. Por defecto: las lecturas de la misa de las próximas
 * dos semanas, luego la Reina-Valera entera en orden y luego el resto de lecturas.
 */
private fun seleccionar(
    solo: String,
    biblia: Biblia,
    catolica: Biblia,
    capitulos: List<Unidad>,
    lecturas: List<Unidad>,
    leccionario: Leccionario,
    extension: String,
): List<Unidad> {
    val porClave = (capitulos + lecturas).associateBy { it.clave }
    val salida = LinkedHashMap<String, Unidad>()
    fun agregar(u: Unidad) { salida.putIfAbsent(u.clave, u) }

    val partes = solo.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
    for (parte in partes.ifEmpty { listOf("misa:14", "rv", "misa") }) {
        val p = plegar(parte)
        when {
            p == "rv" || p == "biblia" -> capitulos.forEach(::agregar)
            p == "misa" -> lecturas.forEach(::agregar)
            p.startsWith("misa:") -> {
                val dias = p.removePrefix("misa:").toIntOrNull() ?: 7
                val hoy = LocalDate.now()
                for (d in 0 until dias) {
                    for (lectura in leccionario.delDia(hoy.plusDays(d.toLong()))) {
                        (porClave[lectura.clave] ?: Plan.lectura(lectura, catolica, extension))?.let(::agregar)
                    }
                }
            }
            else -> {
                // «Juan 11» o «Juan» (el libro entero).
                val m = Regex("^(.*?)\\s+(\\d+)$").find(p)
                val nombre = m?.groupValues?.get(1) ?: p
                val numero = m?.groupValues?.get(2)?.toIntOrNull()
                val libro = biblia.libros.firstOrNull { plegar(it.nombre) == nombre }
                    ?: error("No conozco el libro «$parte». Escríbelo como en la app, por ejemplo «Juan 11» o «1 Samuel».")
                val numeros = if (numero != null) listOf(numero) else (1..libro.capitulos.size).toList()
                for (n in numeros) {
                    require(n in 1..libro.capitulos.size) { "${libro.nombre} no tiene capítulo $n." }
                    porClave["rv/${libro.numero}/$n"]?.let(::agregar)
                }
            }
        }
    }
    return salida.values.toList()
}

/** Minúsculas y sin acentos, para comparar nombres de libros. */
private fun plegar(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

private fun listo(salida: File, pieza: Pieza): Boolean {
    val f = File(salida, pieza.archivo)
    return f.isFile && f.length() >= 1_024
}

// ------------------------------------------------------------------ opciones

class Opciones(
    val proyecto: File,
    val salida: File,
    val clave: String,
    val solo: String,
    val hilos: Int,
    val formato: String,
    val contar: Boolean,
    val soloManifiesto: Boolean,
    val limpiar: Boolean,
    val instalar: Boolean,
) {
    companion object {
        fun de(args: Array<String>): Opciones {
            var proyecto = File(".").absoluteFile
            var salida = File("E:/PG/BibliaVoz-IA/audio")
            var clave = "E:/PG/BibliaVoz-IA/clave-fish.txt"
            var solo = ""
            var hilos = 3
            // Fish Audio ignora el bitrate que se le pide en Opus (lo entrega a ~270 kbps);
            // en MP3 sí respeta los 64 kbps: unos 3 GB para todo, en vez de 13.
            var formato = "mp3"
            var contar = false
            var soloManifiesto = false
            var limpiar = false
            var instalar = false
            var i = 0
            fun valor(): String = args.getOrNull(++i) ?: error("Falta el valor de ${args[i - 1]}")
            while (i < args.size) {
                when (args[i]) {
                    "--proyecto" -> proyecto = File(valor())
                    "--salida" -> salida = File(valor())
                    "--clave" -> clave = valor()
                    "--solo" -> solo = valor()
                    "--hilos" -> hilos = valor().toInt().coerceIn(1, 5)
                    "--formato" -> formato = valor().lowercase().also { require(it == "opus" || it == "mp3") { "Formato: opus o mp3" } }
                    "--contar" -> contar = true
                    "--manifiesto" -> soloManifiesto = true
                    "--limpiar" -> limpiar = true
                    "--instalar" -> instalar = true
                    else -> error("Opción desconocida: ${args[i]}")
                }
                i++
            }
            return Opciones(proyecto, salida, clave, solo, hilos, formato, contar, soloManifiesto, limpiar, instalar)
        }
    }
}

// ------------------------------------------------------------------ registro

class Registro(private val archivo: File) {
    private val hora = DateTimeFormatter.ofPattern("HH:mm:ss")

    @Synchronized
    fun escribir(texto: String) {
        val linea = "${LocalDateTime.now().format(hora)}  $texto"
        println(linea)
        runCatching { archivo.appendText(linea + System.lineSeparator(), Charsets.UTF_8) }
    }
}

// ------------------------------------------------------------------ manifiesto

/**
 * El `manifest.json` que lee la app. Solo incluye capítulos y lecturas con
 * TODAS sus piezas generadas: la app o tiene el capítulo entero, o no lo tiene.
 */
class Manifiesto(private val salida: File, private val unidades: List<Unidad>, private val formato: String) {

    private var capitulos = 0
    private var lecturas = 0
    private var bytes = 0L

    fun resumen(): String = "$capitulos capítulos y $lecturas lecturas completos (${bytes / (1024 * 1024)} MB)"

    @Synchronized
    fun escribir() {
        val caps = JSONObject()
        val lecs = JSONObject()
        var nCaps = 0
        var nLecs = 0
        var total = 0L
        for (u in unidades) {
            if (u.piezas.isEmpty() || !u.piezas.all { listo(salida, it) }) continue
            val arr = JSONArray()
            for (p in u.piezas) {
                val inicios = JSONArray()
                p.inicios.forEach { inicios.put(Math.round(it * 10_000.0) / 10_000.0) }
                arr.put(JSONArray().put(p.desde).put(p.hasta).put(p.archivo).put(inicios))
                total += File(salida, p.archivo).length()
            }
            if (u.grupo == Plan.GRUPO_CAPITULOS) {
                caps.put(u.clave, arr)
                nCaps++
            } else {
                lecs.put(u.clave, arr)
                nLecs++
            }
        }
        val raiz = JSONObject()
            .put("version", 1)
            .put("voz", VocesIa.VOZ_ID)
            .put("vozNombre", VocesIa.VOZ_NOMBRE)
            .put("modelo", VocesIa.MODELO)
            .put("modeloNombre", VocesIa.MODELO_NOMBRE)
            .put("director", Director.VERSION)
            .put("formato", formato)
            .put("generado", LocalDateTime.now().toString())
            .put("bytes", total)
            .put("capitulos", caps)
            .put("lecturas", lecs)
        val tmp = File(salida, "manifest.json.tmp")
        tmp.writeText(raiz.toString(), Charsets.UTF_8)
        Files.move(tmp.toPath(), File(salida, "manifest.json").toPath(), StandardCopyOption.REPLACE_EXISTING)
        capitulos = nCaps
        lecturas = nLecs
        bytes = total
    }
}

// ------------------------------------------------------------------ generador

class Generador(
    private val unidades: List<Unidad>,
    private val op: Opciones,
    private val clave: String,
    private val registro: Registro,
    private val manifiesto: Manifiesto,
    /** Si se da, cada hora se copia al teléfono lo que ya esté generado. */
    private val instalador: Instalador? = null,
) {
    private val cliente = FishAudioClient()
    private val fatal = AtomicReference<FalloVozIa?>(null)
    private val hechos = AtomicInteger(0)
    private val fallidos = ConcurrentLinkedQueue<String>()

    /** Devuelve `false` si hubo que parar por un error que no se arregla solo. */
    fun correr(): Boolean {
        val pendientes = unidades.flatMap { u -> u.piezas.filter { !listo(op.salida, it) }.map { u to it } }
        val total = pendientes.size
        registro.escribir(
            "Voz ${VocesIa.VOZ_NOMBRE}, modelo ${VocesIa.MODELO}, formato ${op.formato}, ${op.hilos} a la vez. " +
                "Faltan $total tramos de ${unidades.sumOf { it.piezas.size }}."
        )
        if (total == 0) {
            manifiesto.escribir()
            registro.escribir("Nada que generar. ${manifiesto.resumen()}.")
            return true
        }

        val cola = ConcurrentLinkedQueue(pendientes)
        val inicio = System.nanoTime()
        val trabajadores = List(op.hilos) { n ->
            thread(name = "voz-$n") {
                while (fatal.get() == null) {
                    val (unidad, pieza) = cola.poll() ?: break
                    if (listo(op.salida, pieza)) continue
                    if (generar(unidad, pieza)) informar(unidad, pieza, total, inicio)
                }
            }
        }
        // Cada minuto se reescribe el manifiesto: lo copiado a medio camino ya sirve.
        // Y cada hora, si se pidió, se copia al teléfono.
        val guardian = thread(isDaemon = true, name = "manifiesto") {
            try {
                var minutos = 0
                while (trabajadores.any { it.isAlive }) {
                    Thread.sleep(60_000)
                    runCatching { manifiesto.escribir() }
                    if (++minutos % 60 == 0) instalador?.instalar()
                }
            } catch (e: InterruptedException) {
                // Terminó el trabajo: el manifiesto final lo escribe correr().
            }
        }
        trabajadores.forEach { it.join() }
        guardian.interrupt()
        manifiesto.escribir()

        val error = fatal.get()
        if (error != null) {
            registro.escribir("PARADO: ${error.mensaje}")
            registro.escribir("Lo generado hasta aquí se conserva. ${manifiesto.resumen()}.")
            return false
        }
        if (fallidos.isNotEmpty()) {
            registro.escribir("Terminado con ${fallidos.size} tramos sin generar (vuelve a lanzarlo para reintentarlos):")
            fallidos.forEach { registro.escribir("   $it") }
        } else {
            registro.escribir("Terminado sin errores.")
        }
        registro.escribir(manifiesto.resumen() + ".")
        return true
    }

    private fun informar(unidad: Unidad, pieza: Pieza, total: Int, inicio: Long) {
        val n = hechos.incrementAndGet()
        val segundos = (System.nanoTime() - inicio) / 1e9
        val restante = if (n > 0) segundos / n * (total - n) else 0.0
        val parte = unidad.piezas.indexOf(pieza) + 1
        registro.escribir(
            "[$n/$total] ${unidad.nombre} (tramo $parte de ${unidad.piezas.size}) · faltan ~${duracion(restante)}"
        )
    }

    /** Genera una pieza con reintentos. `false` si no se pudo. */
    private fun generar(unidad: Unidad, pieza: Pieza): Boolean {
        val destino = File(op.salida, pieza.archivo)
        val temporal = File(op.salida, pieza.archivo + ".part")
        var intento = 0
        while (fatal.get() == null) {
            try {
                cliente.sintetizar(clave, VocesIa.MODELO, VocesIa.VOZ_ID, pieza.texto, temporal, op.formato)
                Files.move(temporal.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return true
            } catch (e: FalloVozIa) {
                temporal.delete()
                if (e.fatal) {
                    fatal.compareAndSet(null, e)
                    return false
                }
                intento++
                val rendirse = (e is FalloVozIa.PeticionRechazada && intento >= 2) ||
                    (e is FalloVozIa.AudioInvalido && intento >= 3)
                if (rendirse) {
                    fallidos.add("${unidad.nombre}, versículos ${pieza.desde + 1}-${pieza.hasta + 1}: ${e.mensaje}")
                    registro.escribir("Se salta ${unidad.nombre} (versículos ${pieza.desde + 1}-${pieza.hasta + 1}): ${e.mensaje}")
                    return false
                }
                esperar(unidad, e.mensaje, intento)
            } catch (e: IOException) {
                // Casi siempre el disco: E: es un SSD USB que a veces se desconecta.
                temporal.delete()
                intento++
                esperar(unidad, "No se pudo escribir en ${op.salida} (${e.javaClass.simpleName}).", intento)
            }
        }
        return false
    }

    private fun esperar(unidad: Unidad, motivo: String, intento: Int) {
        val segundos = minOf(600L, 15L shl minOf(intento, 6))
        registro.escribir("${unidad.nombre}: $motivo Reintento en ${duracion(segundos.toDouble())}.")
        Thread.sleep(segundos * 1_000)
    }

    private fun duracion(segundos: Double): String {
        val s = segundos.toLong()
        return when {
            s >= 3_600 -> "${s / 3_600} h ${(s % 3_600) / 60} min"
            s >= 60 -> "${s / 60} min"
            else -> "$s s"
        }
    }
}
