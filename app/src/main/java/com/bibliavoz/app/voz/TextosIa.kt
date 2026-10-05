package com.bibliavoz.app.voz

/**
 * Identifica la VERSIÓN DEL TEXTO con que se grabó la voz IA (la Biblia y el
 * leccionario). Va en el `manifest.json` y lo comprueba la app al leerlo: si no
 * coincide, descarta ese audio entero.
 *
 * Las claves del audio (nombre de archivo, `rv/<libro>/<cap>`, [ClaveLectura])
 * NO incluyen el texto, así que un audio viejo podría quedar ligado a un texto
 * nuevo sin que nada avise: se oiría «Yahvé» o incluso otro libro mientras la
 * pantalla muestra el texto nuevo. Este sello lo impide.
 *
 * **Sube el número cada vez que cambie la Biblia (`assets/bible-cat`) o el
 * leccionario (`assets/liturgia/leccionario.json`):** así el audio de antes deja
 * de aceptarse y hay que regrabar.
 *
 * `mx-sbl-3`: Santa Biblia Libre (eBible spabll), con 1 Samuel 11:15 completado
 * desde la RV1909 (dominio público), adaptado; con «Yahvé»→«el Señor», sin
 * «Selah», vocativos de los salmos corregidos y sin los títulos de los salmos;
 * leccionario de la CEM (México) con el salmo responsorial leyendo sus versículos
 * citados (no el salmo entero).
 *
 * `mx-sbl-4`: lo mismo, con el Salmo 45,1 completo otra vez («Mi corazón rebosa
 * de un hermoso poema…»), que la tabla de títulos había quitado como si fuera
 * parte de la inscripción.
 */
object TextosIa {
    const val ID = "mx-sbl-4"
}
