package com.gios.brightlibrarylite.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Cover art on disk.
 *
 * Covers come from the book file's own artwork (or, for a comic, its first page). When the file has
 * none the shelf draws a typographic cover instead (see `ui/home/CoverArt.kt`). The full app also
 * asked Open Library for missing art; the Lite tool makes no network requests, so that step is gone.
 *
 * Stored as JPEG at [MAX_EDGE_PX] on the long edge. A shelf holds a dozen covers at once on a
 * 411dp screen, so the full-size art a publisher ships — often 1600px — is a megabyte of heap
 * per book for no visible gain.
 */
object Covers {

    /** Comfortably over the ~120dp a shelf cell gets at 420dpi, with room for a 2x panel. */
    private const val MAX_EDGE_PX = 600
    private const val JPEG_QUALITY = 88

    fun dir(): File = AppFiles.dir("covers")

    fun fileFor(book: Book): File? =
        book.coverFileName?.let { File(dir(), it) }?.takeIf { it.exists() }

    fun fileName(bookId: String): String = "$bookId.jpg"

    /**
     * Decode, downscale and store [bytes] as [bookId]'s cover.
     *
     * Returns the file name to put on the [Book], or null if the bytes were not an image this
     * device can decode — which is the normal outcome for a malformed EPUB, not an error worth
     * failing an import over.
     */
    fun store(bookId: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val bitmap = runCatching { decodeScaled(bytes) }.getOrNull() ?: return null
        val target = File(dir(), fileName(bookId))
        return try {
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            target.name
        } catch (e: Exception) {
            target.delete()
            null
        }
    }

    fun delete(book: Book) {
        book.coverFileName?.let { File(dir(), it).delete() }
    }

    /** Load a stored cover for drawing. Null if it is missing or no longer decodable. */
    fun load(book: Book): ImageBitmap? {
        val file = fileFor(book) ?: return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    /** Cheap magic-number check, so an HTML error page never gets stored as a cover. */
    fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()
        val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte()
        val webp = bytes.size > 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()
        return jpeg || png || gif || webp
    }

    /**
     * Decode at a power-of-two sample size chosen from the real bounds, then finish the scale
     * exactly. Sampling alone leaves anything from 1x to 2x the target size; a shelf of covers
     * that are each a slightly different scale reads as sloppy.
     */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE_PX) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val edge = maxOf(decoded.width, decoded.height)
        if (edge <= MAX_EDGE_PX) return decoded
        val ratio = MAX_EDGE_PX.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != decoded) decoded.recycle()
        return scaled
    }
}
