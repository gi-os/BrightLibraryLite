package com.gios.brightlibrarylite.data

import kotlinx.serialization.Serializable

/**
 * What kind of thing a book is, and therefore which reader opens it.
 *
 * [Comic] covers CBZ and the image EPUBs a scanned manga volume ships as. The distinction is not
 * cosmetic: a comic has no words at all, so every text-shaped thing in this app — pagination, RSVP,
 * bionic reading, font size — is meaningless for one, and a comic opened in the text reader is a
 * hundred blank pages.
 */
@Serializable
enum class BookKind { Text, Comic }

@Serializable
data class Chapter(
    val title: String,
    val startWordIndex: Int,
)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val format: String,
    val textFileName: String,
    /**
     * Words in the book — or, for a [BookKind.Comic], **pages**.
     *
     * Reused rather than given a parallel `pageCount` on purpose: progress, the shelf's hairline
     * and the resume position are all expressed in terms of these two fields, and a second pair would mean every one of those grew a branch. A comic is a book
     * whose unit of progress happens to be a page.
     */
    val totalWords: Int,
    val currentWordIndex: Int = 0,
    val addedAtMs: Long = System.currentTimeMillis(),
    val chapters: List<Chapter> = emptyList(),
    val bookmarks: List<Int> = emptyList(),
    /**
     * File name of the stored cover inside `filesDir/covers`, or null when the book has none and
     * the shelf should draw a typographic cover for it. See [Covers].
     */
    val coverFileName: String? = null,
    /**
     * When the cover was last written. The file name is derived from the book id and so never
     * changes, which means a replacement cover would leave the [Book] equal to its old self —
     * no recomposition, and a decoded bitmap keyed on the name would stay stale. This is what
     * makes a re-fetch visible.
     */
    val coverUpdatedAtMs: Long = 0L,
    /**
     * When a cover was last looked for. The full app used this to back off its Open Library
     * lookups; the Lite tool makes no lookups and only keeps the field so the stored shelf format
     * matches the full app's.
     */
    val coverSearchedAtMs: Long = 0L,
    /**
     * The file this book was imported from, by name, inside the Tool Manager's Books folder
     * (`files/shared/Books`). Null only for a book whose file has since been removed.
     *
     * The Lite tool has no file picker: books arrive by being dropped into that folder from a
     * computer, and the shelf is kept in step with what is there. The name plus [sourceSize] is how a
     * file that has already been read is told apart from a new one.
     */
    val sourceFile: String? = null,
    val sourceSize: Long = 0L,
    val kind: BookKind = BookKind.Text,
    /**
     * Reading direction, for a [BookKind.Comic].
     *
     * Right-to-left by default, because the overwhelming majority of comics on this phone are manga
     * and a Western-ordered scan is the exception. Per book rather than a global setting, since a
     * shelf routinely holds both — long-press a book to flip it.
     */
    val rightToLeft: Boolean = true,
)
