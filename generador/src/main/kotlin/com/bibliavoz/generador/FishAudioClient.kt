package com.bibliavoz.generador

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Por qué no se pudo generar el audio de un tramo.
 *
 * [fatal] quiere decir que reintentar no sirve: hay que parar y que el
 * usuario lo arregle (la clave, el saldo). Los demás fallos se reintentan.
 */
sealed class FalloVozIa(val mensaje: String, val fatal: Boolean) : Exception(mensaje) {

    class ClaveInvalida : FalloVozIa(
        "Fish Audio no aceptó la clave. Revisa el archivo de la clave (sin espacios ni saltos de línea de más).",
        fatal = true,
    )

    class SinSaldo : FalloVozIa(
        "La cuenta de Fish Audio no tiene saldo para este modelo. El gratuito es s2.1-pro-free.",
        fatal = true,
    )

    class PeticionRechazada(codigo: Int, detalle: String) : FalloVozIa(
        "Fish Audio rechazó la petición ($codigo${if (detalle.isBlank()) "" else ": $detalle"}).",
        fatal = false,
    )

    class Saturado(val codigo: Int) : FalloVozIa("Fish Audio está saturado ($codigo).", fatal = false)

    class SinConexion(detalle: String = "") : FalloVozIa(
        "Sin conexión con Fish Audio${if (detalle.isBlank()) "" else " ($detalle)"}.",
        fatal = false,
    )

    class AudioInvalido : FalloVozIa("El audio que llegó de Fish Audio está vacío o dañado.", fatal = false)
}

/**
 * Cliente mínimo de la API de texto a voz de Fish Audio (`POST /v1/tts`).
 *
 * Sin librerías: HttpURLConnection y org.json bastan para una petición JSON
 * que devuelve el audio.
 */
class FishAudioClient(private val base: String = "https://api.fish.audio") {

    /**
     * Genera [texto] con la voz [vozId] y lo escribe en [destino]. Bloquea.
     *
     * @param formato `mp3` (64 kbps) u `opus`. Ojo: en Opus Fish Audio ignora el
     *   bitrate pedido y entrega unos 270 kbps, cuatro veces más que el MP3.
     * @throws FalloVozIa si no se pudo; en ese caso [destino] no queda a medias.
     */
    fun sintetizar(clave: String, modelo: String, vozId: String, texto: String, destino: File, formato: String = "mp3") {
        val conexion = URL("$base/v1/tts").openConnection() as HttpURLConnection
        try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = 20_000
            // Fish Audio no manda nada hasta tener el primer trozo de voz.
            conexion.readTimeout = 120_000
            conexion.doOutput = true
            conexion.setRequestProperty("Authorization", "Bearer $clave")
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexion.setRequestProperty("model", modelo)

            val cuerpo = cuerpo(texto, vozId, formato).toByteArray(Charsets.UTF_8)
            conexion.setFixedLengthStreamingMode(cuerpo.size)
            conexion.outputStream.use { it.write(cuerpo) }

            val codigo = conexion.responseCode
            if (codigo !in 200..299) throw fallo(codigo, leerError(conexion))

            conexion.inputStream.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            // Un audio de voz de verdad nunca pesa tan poco: es un error disfrazado.
            if (destino.length() < 1_024) throw FalloVozIa.AudioInvalido()
        } catch (e: FalloVozIa) {
            destino.delete()
            throw e
        } catch (e: IOException) {
            destino.delete()
            throw FalloVozIa.SinConexion(e.javaClass.simpleName)
        } finally {
            conexion.disconnect()
        }
    }

    fun cuerpo(texto: String, vozId: String, formato: String = "mp3"): String {
        val json = JSONObject()
            .put("text", texto)
            .put("reference_id", vozId)
            .put("latency", "normal")
        if (formato == "opus") {
            json.put("format", "opus").put("opus_bitrate", 32_000)
        } else {
            // 64 kbps sobran para una voz y dejan la hora de audio en ~29 MB.
            json.put("format", "mp3").put("mp3_bitrate", 64)
        }
        return json.toString()
    }

    private fun leerError(conexion: HttpURLConnection): String {
        val cuerpo = runCatching {
            conexion.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull().orEmpty()
        val mensaje = runCatching { JSONObject(cuerpo).optString("message") }.getOrNull()
        return (mensaje ?: cuerpo).take(160).trim()
    }

    private fun fallo(codigo: Int, detalle: String): FalloVozIa = when (codigo) {
        401, 403 -> FalloVozIa.ClaveInvalida()
        402 -> FalloVozIa.SinSaldo()
        429, in 500..599 -> FalloVozIa.Saturado(codigo)
        else -> FalloVozIa.PeticionRechazada(codigo, detalle)
    }
}
