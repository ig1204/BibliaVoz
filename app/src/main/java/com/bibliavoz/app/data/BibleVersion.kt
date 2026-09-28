package com.bibliavoz.app.data

/**
 * Las dos versiones empaquetadas. Ambas son de dominio público.
 *
 * La católica hace falta porque el leccionario de la misa toma muchas primeras
 * lecturas de los libros deuterocanónicos (Tobías, Judit, Sabiduría, Eclesiástico,
 * Baruc, 1-2 Macabeos), que la Reina-Valera no incluye.
 */
enum class BibleVersion(
    val id: String,
    val assetDir: String,
    val displayName: String,
    val shortName: String,
    val bookCount: Int,
) {
    RV1909(
        id = "rv1909",
        assetDir = "bible",
        displayName = "Reina-Valera 1909",
        shortName = "RV1909",
        bookCount = 66,
    ),

    CATOLICA(
        id = "sbl",
        assetDir = "bible-cat",
        displayName = "Santa Biblia Libre",
        shortName = "Canon católico",
        bookCount = 73,
    );

    companion object {
        fun fromId(id: String?): BibleVersion =
            entries.firstOrNull { it.id == id } ?: RV1909
    }
}
