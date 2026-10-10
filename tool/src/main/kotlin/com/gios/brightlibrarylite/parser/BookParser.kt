package com.gios.brightlibrarylite.parser

data class RawChapter(
    val title: String,
    val charOffset: Int,
)

data class ParsedBook(
    val title: String,
    val author: String,
    val format: String,
    val text: String,
    val chapters: List<RawChapter> = emptyList(),
    /**
     * The book's own cover art, exactly as it was stored in the file. Null is a common case —
     * plenty of EPUBs declare no cover — and the importer falls back to an Open Library lookup
     * and then to a typographic cover, so nothing downstream treats this as required.
     *
     * A `ByteArray` in a data class means `equals`/`hashCode` compare by identity. That is fine
     * here (a [ParsedBook] is consumed once, at import, and never compared) but it is why this
     * field must not be relied on for equality.
     */
    val coverImage: ByteArray? = null,
)

object BookParser {
    /**
     * Parse a book already in memory.
     *
     * In the Lite tool every book arrives as a file in the Tool Manager's Books folder, so this is
     * the only entry point: the importer reads the file and hands the bytes over.
     *
     * [name] is only used for its extension and as the fallback title.
     */
    fun parseBytes(bytes: ByteArray, name: String): ParsedBook {
        val ext = name.substringAfterLast('.', "").lowercase()
        val fallbackTitle = name.substringBeforeLast('.')
        return when (ext) {
            "epub" -> EpubParser.parseBytes(bytes, fallbackTitle = fallbackTitle)
            "mobi", "azw", "azw3" -> MobiParser.parseBytes(bytes, fallbackTitle = fallbackTitle)
            else -> throw IllegalArgumentException("Unsupported format: .$ext (use .epub or .mobi)")
        }
    }
}
