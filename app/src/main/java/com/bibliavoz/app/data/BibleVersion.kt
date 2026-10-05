package com.bibliavoz.app.data

/**
 * La Biblia empaquetada: la Santa Biblia Libre (SBL), con los 73 libros del
 * canon católico. Es una sola, para todo —los capítulos y las lecturas de la
 * misa—, porque el leccionario toma muchas primeras lecturas de los libros
 * deuterocanónicos (Tobías, Judit, Sabiduría, Eclesiástico, Baruc, 1-2 Macabeos).
 *
 * De dominio público (eBible.org, «spabll»). Para México se adapta: el nombre
 * divino «Yahvé» se lee «el Señor». Ver LEEME y la pantalla de ajustes.
 */
enum class BibleVersion(
    val id: String,
    val assetDir: String,
    val displayName: String,
    val shortName: String,
    val bookCount: Int,
) {
    CATOLICA(
        id = "sbl",
        assetDir = "bible-cat",
        displayName = "Santa Biblia Libre",
        shortName = "SBL",
        bookCount = 73,
    );

    companion object {
        fun fromId(id: String?): BibleVersion =
            entries.firstOrNull { it.id == id } ?: CATOLICA
    }
}
