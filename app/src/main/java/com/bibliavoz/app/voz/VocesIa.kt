package com.bibliavoz.app.voz

/**
 * La voz IA con que está grabada la Biblia.
 *
 * La eligió el usuario escuchando cuatro narradores leyendo Juan 11 (fue la
 * muestra 4). El audio se genera una sola vez en la PC con estos valores; la
 * app no se conecta a ningún sitio para reproducirlo.
 */
object VocesIa {

    /** «hilary narrador», de la biblioteca pública de fish.audio. */
    const val VOZ_ID = "937314c424504d10912e5eef334993a7"

    const val VOZ_NOMBRE = "Hilary narrador"

    const val MODELO = "s2.1-pro-free"

    const val MODELO_NOMBRE = "Fish Audio S2.1 Pro"
}
