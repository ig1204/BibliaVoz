package com.bibliavoz.app.data

import android.content.Context
import com.bibliavoz.app.voz.Tramos
import org.json.JSONObject

/**
 * Acceso al texto bíblico empaquetado en `assets/bible/`.
 *
 * Formato: `index.json` con la lista de libros, y `<n>.json` por libro con
 * `{"n":1,"name":"Génesis","abbr":"Gn","chapters":[["v1","v2",...], ...]}`.
 *
 * Los libros se parsean bajo demanda y se guardan en una caché LRU pequeña,
 * porque cargar la Biblia entera en memoria no hace falta para leer un capítulo.
 */
class BibleRepository private constructor(
    context: Context,
    val version: BibleVersion,
) {

    private val appContext: Context = context.applicationContext
    private val dir: String = version.assetDir

    private class ParsedBook(
        val name: String,
        val abbr: String,
        val chapters: List<List<String>>,
    )

    /** Caché LRU de como mucho 4 libros parseados. */
    private val bookCache = object : LinkedHashMap<Int, ParsedBook>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ParsedBook>): Boolean =
            size > 4
    }

    /** Los 66 libros en orden canónico. Se lee una sola vez. */
    val books: List<BookInfo> by lazy { loadIndex() }

    val translationName: String by lazy {
        runCatching { JSONObject(readAsset("$dir/index.json")).getString("translation") }
            .getOrDefault(version.displayName)
    }

    /** Número de libros que trae de verdad esta versión. */
    val bookCount: Int get() = books.size

    private fun readAsset(path: String): String =
        appContext.assets.open(path).use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }

    private fun loadIndex(): List<BookInfo> {
        val array = JSONObject(readAsset("$dir/index.json")).getJSONArray("books")
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    BookInfo(
                        number = o.getInt("n"),
                        name = o.getString("name"),
                        abbr = o.getString("abbr"),
                        testament = o.getString("t"),
                        chapterCount = o.getInt("chapters"),
                    )
                )
            }
        }
    }

    @Synchronized
    private fun parsedBook(bookNumber: Int): ParsedBook {
        val n = bookNumber.coerceIn(1, version.bookCount)
        bookCache[n]?.let { return it }

        val root = JSONObject(readAsset("$dir/$n.json"))
        val chaptersArray = root.getJSONArray("chapters")
        val chapters = buildList(chaptersArray.length()) {
            for (c in 0 until chaptersArray.length()) {
                val versesArray = chaptersArray.getJSONArray(c)
                add(
                    buildList(versesArray.length()) {
                        for (v in 0 until versesArray.length()) add(versesArray.getString(v))
                    }
                )
            }
        }
        val parsed = ParsedBook(root.getString("name"), root.getString("abbr"), chapters)
        bookCache[n] = parsed
        return parsed
    }

    fun book(bookNumber: Int): BookInfo =
        books.firstOrNull { it.number == bookNumber } ?: books.first()

    fun chapterCount(bookNumber: Int): Int = book(bookNumber).chapterCount

    /** Carga un capítulo, recortando los índices fuera de rango en vez de fallar. */
    fun chapter(bookNumber: Int, chapterNumber: Int): Chapter {
        val parsed = parsedBook(bookNumber)
        val index = (chapterNumber - 1).coerceIn(0, parsed.chapters.lastIndex)
        return Chapter(
            bookNumber = bookNumber.coerceIn(1, version.bookCount),
            bookName = parsed.name,
            chapterNumber = index + 1,
            verses = parsed.chapters[index],
        )
    }

    /**
     * Normalización barata: solo aritmética, no abre ningún asset, así que es
     * segura de llamar en el hilo principal. El recorte fino del capítulo y del
     * versículo lo hace [chapter] o quien termine cargando el capítulo.
     */
    fun normalizeCheap(position: Position): Position = Position(
        book = position.book.coerceIn(1, version.bookCount),
        chapter = position.chapter.coerceAtLeast(1),
        verse = position.verse.coerceAtLeast(0),
    )

    /** Ajusta una posición cualquiera a algo que exista de verdad. Hace entrada/salida. */
    fun normalize(position: Position): Position {
        val book = position.book.coerceIn(1, version.bookCount)
        val chapter = position.chapter.coerceIn(1, chapterCount(book))
        val verses = chapter(book, chapter).verseCount
        val verse = position.verse.coerceIn(0, (verses - 1).coerceAtLeast(0))
        return Position(book, chapter, verse)
    }

    /** Capítulo siguiente, saltando al libro siguiente. `null` al final de Apocalipsis. */
    fun nextChapter(book: Int, chapter: Int): Pair<Int, Int>? = when {
        chapter < chapterCount(book) -> book to (chapter + 1)
        book < version.bookCount -> (book + 1) to 1
        else -> null
    }

    /** Capítulo anterior, retrocediendo al libro previo. `null` antes de Génesis 1. */
    fun previousChapter(book: Int, chapter: Int): Pair<Int, Int>? = when {
        chapter > 1 -> book to (chapter - 1)
        book > 1 -> (book - 1) to chapterCount(book - 1)
        else -> null
    }

    /**
     * Texto de un tramo de versículos, para las lecturas de la misa. Los huecos
     * de numeración se guardaron vacíos para no descuadrar los índices; aquí
     * simplemente no se leen. La misma función la usa el generador de audio.
     */
    fun verses(bookNumber: Int, fromChapter: Int, fromVerse: Int, toChapter: Int, toVerse: Int): List<String> =
        Tramos.versos(parsedBook(bookNumber).chapters, fromChapter, fromVerse, toChapter, toVerse)

    companion object {
        private val instances = HashMap<BibleVersion, BibleRepository>()

        @Synchronized
        fun get(context: Context, version: BibleVersion = BibleVersion.RV1909): BibleRepository =
            instances.getOrPut(version) { BibleRepository(context, version) }
    }
}
