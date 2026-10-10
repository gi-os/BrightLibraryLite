package com.gios.brightlibrarylite.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.gios.brightlibrarylite.comic.ComicPages
import com.gios.brightlibrarylite.parser.HtmlStripper
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

data class LoadedText(
    val words: List<String>,
    val paragraphBreakAfter: BooleanArray,
    val italic: BooleanArray,
)

/**
 * The shelf: every imported book, newest first, as one JSON list in `files/library.json`.
 *
 * Ported from the full app's repository with two changes. It is an `object` opened with the tool's
 * `filesDir` rather than a singleton built from a `Context`, and the Calibre and reading-session
 * bookkeeping is gone with the features that needed it.
 */
object BookRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    val books: SnapshotStateList<Book> = mutableStateListOf()

    @Volatile
    private var loaded = false

    private fun libraryFile(): File = File(AppFiles.filesDir, "library.json")

    private fun booksDir(): File = AppFiles.dir("books")

    fun textFile(book: Book): File = File(booksDir(), book.textFileName)

    /** Read the shelf from disk once. Safe to call from every screen and from the background job. */
    fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val raw = runCatching { libraryFile().takeIf { it.exists() }?.readText() }.getOrNull()
            val list = raw?.let {
                runCatching { json.decodeFromString(ListSerializer(Book.serializer()), it) }.getOrNull()
            }.orEmpty()
            books.clear()
            books.addAll(list)
            loaded = true
        }
    }

    private fun persist() {
        synchronized(lock) {
            val raw = json.encodeToString(ListSerializer(Book.serializer()), books.toList())
            runCatching { AppFiles.writeAtomically(libraryFile(), raw) }
        }
    }

    fun addBook(book: Book, fullText: String) {
        val file = File(booksDir(), book.textFileName)
        file.writeText(fullText)
        books.add(0, book)
        persist()
    }

    /**
     * Convert chapter char offsets (positions in `fullText`) into word indices.
     * A "word" here matches the splitter used elsewhere: a maximal run of non-whitespace.
     */
    fun chaptersFromCharOffsets(
        fullText: String,
        rawChapters: List<Pair<String, Int>>,
    ): List<Chapter> {
        if (rawChapters.isEmpty()) return emptyList()
        val sorted = rawChapters
            .map { (title, off) -> title to off.coerceIn(0, fullText.length) }
            .sortedBy { it.second }
        val result = ArrayList<Chapter>(sorted.size)
        var charIdx = 0
        var wordIdx = 0
        var inWord = false
        var ci = 0
        while (charIdx < fullText.length && ci < sorted.size) {
            while (ci < sorted.size && sorted[ci].second <= charIdx) {
                result.add(Chapter(sorted[ci].first, wordIdx))
                ci++
            }
            val ch = fullText[charIdx]
            if (ch == HtmlStripper.ITALIC_START || ch == HtmlStripper.ITALIC_END) {
                // Zero-width markers — neither whitespace nor part of a word.
            } else if (ch.isWhitespace()) {
                if (inWord) {
                    wordIdx++
                    inWord = false
                }
            } else {
                inWord = true
                if (ch == '—' || ch == '–') {
                    wordIdx++
                    inWord = false
                }
            }
            charIdx++
        }
        // Any chapter whose offset is at/past end of text — clamp to last word.
        val tail = wordIdx + (if (inWord) 1 else 0)
        while (ci < sorted.size) {
            result.add(Chapter(sorted[ci].first, tail))
            ci++
        }
        return result
    }

    /**
     * Put a comic on the shelf.
     *
     * Separate from [addBook] because there is no text to write: the pages were converted into
     * `filesDir/comics/<id>` before this is called, and that directory is the book.
     */
    fun addComic(book: Book) {
        books.add(0, book)
        persist()
    }

    /**
     * Flip a comic between right-to-left and left-to-right.
     *
     * Per book rather than a setting: a shelf holds manga and Western scans at once, and the right
     * answer is a property of the volume, not of the reader.
     */
    fun setReadingDirection(id: String, rightToLeft: Boolean) {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0 || books[idx].rightToLeft == rightToLeft) return
        books[idx] = books[idx].copy(rightToLeft = rightToLeft)
        persist()
    }

    /**
     * Take a book off the shelf and delete what the import made of it.
     *
     * [removeSource] also deletes the original file from the Tool Manager folder. That is what a
     * DELETE on the phone means: if the file stayed, the next sweep of the folder would import it
     * straight back. When the file is already gone (deleted from the computer) there is nothing to
     * remove and the flag does nothing.
     */
    fun deleteBook(book: Book, removeSource: Boolean = true) {
        textFile(book).delete()
        if (book.kind == BookKind.Comic) ComicPages.delete(book.id)
        Covers.delete(book)
        if (removeSource) book.sourceFile?.let { File(AppFiles.booksFolder, it).delete() }
        books.removeAll { it.id == book.id }
        persist()
    }

    /**
     * Attach cover art to a book that is already on the shelf.
     *
     * Separate from [addBook] because a comic's cover is its first converted page, which only
     * exists once the book itself has been written.
     */
    fun setCover(id: String, bytes: ByteArray): Boolean {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val stored = Covers.store(id, bytes) ?: return false
        books[idx] = books[idx].copy(
            coverFileName = stored,
            coverUpdatedAtMs = System.currentTimeMillis(),
            coverSearchedAtMs = System.currentTimeMillis(),
        )
        persist()
        return true
    }

    /**
     * Correct a book's title and author.
     *
     * Ebook metadata is frequently wrong or absent — a filename where a title should be, an author
     * field holding a publisher — and everything downstream of it is downstream of that: what the
     * shelf reads, and which series stack a volume lands in.
     */
    fun rename(id: String, title: String, author: String) {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0) return
        val cleanTitle = title.trim().ifBlank { books[idx].title }
        books[idx] = books[idx].copy(
            title = cleanTitle,
            author = author.trim(),
        )
        persist()
    }

    fun getBook(id: String): Book? = books.firstOrNull { it.id == id }

    fun updateProgress(id: String, wordIndex: Int) {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0) return
        val current = books[idx]
        if (current.currentWordIndex == wordIndex) return
        books[idx] = current.copy(currentWordIndex = wordIndex)
        persist()
    }

    /** Remember which file in the Books folder a book came from. */
    fun setSource(id: String, fileName: String?, size: Long) {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0) return
        books[idx] = books[idx].copy(sourceFile = fileName, sourceSize = size)
        persist()
    }

    fun toggleBookmark(id: String, wordIndex: Int): Boolean {
        val idx = books.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val current = books[idx]
        val newBookmarks = if (current.bookmarks.contains(wordIndex)) {
            current.bookmarks - wordIndex
        } else {
            (current.bookmarks + wordIndex).sorted()
        }
        books[idx] = current.copy(bookmarks = newBookmarks)
        persist()
        return newBookmarks.contains(wordIndex)
    }

    /**
     * The last book's words, kept so going from the page view to the word reader and back does not
     * read and split the whole text file each time. One entry: it is always the book being read.
     */
    @Volatile
    private var wordsCache: Pair<String, LoadedText>? = null

    fun loadWordsAndBreaks(book: Book): LoadedText {
        val key = "${book.id}|${book.textFileName}|${book.totalWords}"
        wordsCache?.let { (k, v) -> if (k == key) return v }
        return readWordsAndBreaks(book).also { wordsCache = key to it }
    }

    private fun readWordsAndBreaks(book: Book): LoadedText {
        val file = textFile(book)
        if (!file.exists()) return LoadedText(emptyList(), BooleanArray(0), BooleanArray(0))
        val text = file.readText()
        val words = ArrayList<String>()
        val breaks = ArrayList<Boolean>()
        val italics = ArrayList<Boolean>()
        val current = StringBuilder()
        var pendingNewline = false
        var italicOn = false
        var wordItalic = false

        fun flushWord() {
            if (current.isNotEmpty()) {
                words.add(current.toString())
                breaks.add(false)
                italics.add(wordItalic)
                current.clear()
                wordItalic = italicOn
            }
        }

        for (ch in text) {
            when {
                ch == HtmlStripper.ITALIC_START -> {
                    italicOn = true
                    wordItalic = true
                }
                ch == HtmlStripper.ITALIC_END -> {
                    italicOn = false
                }
                ch.isWhitespace() -> {
                    flushWord()
                    if (ch == '\n' || ch == '\r') pendingNewline = true
                }
                else -> {
                    if (pendingNewline && breaks.isNotEmpty()) {
                        breaks[breaks.size - 1] = true
                    }
                    pendingNewline = false
                    if (current.isEmpty()) wordItalic = italicOn
                    current.append(ch)
                    if (italicOn) wordItalic = true
                    // Em/en dashes terminate the current word (kept attached to it),
                    // so "reality—that" becomes "reality—" + "that".
                    if (ch == '—' || ch == '–') flushWord()
                }
            }
        }
        flushWord()
        return LoadedText(
            words,
            BooleanArray(breaks.size) { breaks[it] },
            BooleanArray(italics.size) { italics[it] },
        )
    }

        /** Word count that matches the splitter in [loadWordsAndBreaks]. */
        fun countWords(text: String): Int {
            var count = 0
            var hasContent = false
            for (ch in text) {
                if (ch == HtmlStripper.ITALIC_START || ch == HtmlStripper.ITALIC_END) {
                    // Zero-width italic markers — ignore.
                } else if (ch.isWhitespace()) {
                    if (hasContent) { count++; hasContent = false }
                } else {
                    hasContent = true
                    if (ch == '—' || ch == '–') { count++; hasContent = false }
                }
            }
            if (hasContent) count++
            return count
        }
}
