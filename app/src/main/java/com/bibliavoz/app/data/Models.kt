package com.bibliavoz.app.data

/** Metadatos de un libro, tal como vienen de assets/bible/index.json. */
data class BookInfo(
    val number: Int,
    val name: String,
    val abbr: String,
    val testament: String,
    val chapterCount: Int,
) {
    val isOldTestament: Boolean get() = testament == "AT"
}

/** Un capítulo ya cargado, con sus versículos en orden. */
data class Chapter(
    val bookNumber: Int,
    val bookName: String,
    val chapterNumber: Int,
    val verses: List<String>,
) {
    val verseCount: Int get() = verses.size
    val reference: String get() = "$bookName $chapterNumber"

    fun verseOrNull(index: Int): String? = verses.getOrNull(index)
}

/**
 * Punto exacto de lectura. [verse] es un índice base 0 dentro del capítulo,
 * así que el versículo mostrado al usuario es `verse + 1`.
 */
data class Position(
    val book: Int = 1,
    val chapter: Int = 1,
    val verse: Int = 0,
) {
    fun reference(bookName: String): String = "$bookName $chapter:${verse + 1}"

    /** Igualdad ignorando el versículo: sirve para saber si cambió de capítulo. */
    fun sameChapter(other: Position): Boolean =
        book == other.book && chapter == other.chapter
}
