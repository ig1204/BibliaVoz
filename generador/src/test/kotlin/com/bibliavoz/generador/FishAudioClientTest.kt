package com.bibliavoz.generador

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * El cliente contra un servidor local que imita a api.fish.audio: comprueba lo
 * que se envía (cabeceras y JSON) y cómo se traduce cada error.
 *
 * El servidor es un ServerSocket a mano: así el test no depende de ninguna
 * librería de servidor.
 */
class FishAudioClientTest {

    private lateinit var servidor: ServerSocket
    @Volatile private var codigo: Int = 200
    @Volatile private var respuesta: ByteArray = ByteArray(4_096) { (it % 251).toByte() }
    @Volatile private var cabeceras: Map<String, String> = emptyMap()
    @Volatile private var cuerpo: String = ""

    @Before
    fun arrancar() {
        servidor = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!servidor.isClosed) {
                val socket = try {
                    servidor.accept()
                } catch (e: SocketException) {
                    break
                }
                socket.use { s ->
                    val entrada = s.getInputStream()
                    val leidas = HashMap<String, String>()
                    leerLinea(entrada) // POST /v1/tts HTTP/1.1
                    while (true) {
                        val linea = leerLinea(entrada)
                        if (linea.isEmpty()) break
                        val dos = linea.indexOf(':')
                        if (dos > 0) leidas[linea.substring(0, dos).trim().lowercase()] = linea.substring(dos + 1).trim()
                    }
                    val largo = leidas["content-length"]?.toIntOrNull() ?: 0
                    val datos = ByteArray(largo)
                    var n = 0
                    while (n < largo) {
                        val r = entrada.read(datos, n, largo - n)
                        if (r < 0) break
                        n += r
                    }
                    cabeceras = leidas
                    cuerpo = datos.toString(Charsets.UTF_8)

                    val salida = s.getOutputStream()
                    val cabecera = "HTTP/1.1 $codigo X\r\nContent-Length: ${respuesta.size}\r\nConnection: close\r\n\r\n"
                    salida.write(cabecera.toByteArray(Charsets.US_ASCII))
                    salida.write(respuesta)
                    salida.flush()
                }
            }
        }
    }

    private fun leerLinea(entrada: InputStream): String {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = entrada.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) buffer.write(b)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    @After
    fun parar() = servidor.close()

    private fun cliente() = FishAudioClient("http://127.0.0.1:${servidor.localPort}")

    private fun destino(): File = File.createTempFile("voz", ".mp3").apply { delete() }

    @Test
    fun `envia la peticion como la pide Fish Audio y guarda el mp3`() {
        val archivo = destino()
        cliente().sintetizar("clave-secreta", "s2.1-pro-free", "937314c424504d10912e5eef334993a7", "Y dijo: [sad] ¿Dónde?", archivo)

        assertEquals(respuesta.size.toLong(), archivo.length())
        assertEquals("Bearer clave-secreta", cabeceras["authorization"])
        assertEquals("s2.1-pro-free", cabeceras["model"])
        assertTrue(cabeceras["content-type"].orEmpty().startsWith("application/json"))

        val json = JSONObject(cuerpo)
        assertEquals("Y dijo: [sad] ¿Dónde?", json.getString("text"))
        assertEquals("937314c424504d10912e5eef334993a7", json.getString("reference_id"))
        assertEquals("mp3", json.getString("format"))
        assertEquals(64, json.getInt("mp3_bitrate"))
        archivo.delete()
    }

    @Test
    fun `en opus pide 32 kbps`() {
        val json = JSONObject(FishAudioClient().cuerpo("hola", "v", "opus"))
        assertEquals("opus", json.getString("format"))
        assertEquals(32_000, json.getInt("opus_bitrate"))
        assertFalse(json.has("mp3_bitrate"))
    }

    private inline fun <reified T : FalloVozIa> fallaCon(estado: Int, texto: String = "") {
        codigo = estado
        respuesta = texto.toByteArray()
        val archivo = destino()
        try {
            cliente().sintetizar("k", "s2.1-pro-free", "v", "hola", archivo)
            fail("Debió fallar con $estado")
        } catch (e: FalloVozIa) {
            assertTrue("Con $estado se esperaba ${T::class.simpleName} y llegó ${e::class.simpleName}", e is T)
        }
        assertFalse("No debe quedar un archivo a medias", archivo.exists())
    }

    @Test
    fun `cada error de la API se traduce a un aviso claro`() {
        fallaCon<FalloVozIa.ClaveInvalida>(401, """{"status":401,"message":"Invalid token"}""")
        fallaCon<FalloVozIa.SinSaldo>(402, """{"status":402,"message":"No credit"}""")
        fallaCon<FalloVozIa.Saturado>(429)
        fallaCon<FalloVozIa.Saturado>(503)
        fallaCon<FalloVozIa.PeticionRechazada>(400, """{"status":400,"message":"text too long"}""")
    }

    @Test
    fun `la clave rechazada y el saldo son fatales, lo demas pasajero`() {
        assertTrue(FalloVozIa.ClaveInvalida().fatal)
        assertTrue(FalloVozIa.SinSaldo().fatal)
        assertFalse(FalloVozIa.SinConexion().fatal)
        assertFalse(FalloVozIa.Saturado(503).fatal)
        assertTrue(FalloVozIa.PeticionRechazada(400, "text too long").mensaje.contains("text too long"))
    }

    @Test
    fun `una respuesta vacia no se toma por audio`() {
        fallaCon<FalloVozIa.AudioInvalido>(200, "")
    }

    @Test
    fun `sin servidor es falta de conexion`() {
        val libre = ServerSocket(0).use { it.localPort }
        val archivo = destino()
        try {
            FishAudioClient("http://127.0.0.1:$libre").sintetizar("k", "m", "v", "hola", archivo)
            fail("Debió fallar sin servidor")
        } catch (e: FalloVozIa) {
            assertTrue(e is FalloVozIa.SinConexion)
        }
        assertFalse(archivo.exists())
    }
}
