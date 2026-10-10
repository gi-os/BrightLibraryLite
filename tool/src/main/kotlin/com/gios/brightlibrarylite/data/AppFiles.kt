package com.gios.brightlibrarylite.data

import com.thelightphone.toolmanager.LightFileProvider
import java.io.File

/**
 * Where everything lives on disk.
 *
 * The full app reached all of this through an Android `Context`. A Light SDK tool is not handed one,
 * only its private `filesDir` (through `SealedLightContext`), so every store here is a plain file
 * under that directory and the repositories are opened with it once, by the first screen.
 *
 * - `files/shared/Books` is the folder the Tool Manager shows on the computer. Books are dropped in
 *   there and read from there; see [Library].
 * - `files/books`, `files/covers` and `files/comics` hold what the importer made of them: the
 *   extracted text, a downscaled cover and the greyscale comic pages.
 * - `files/library.json` and `files/settings.json` are the shelf and the settings.
 */
object AppFiles {
    /** Name of the Tool Manager folder, relative to the shared directory. */
    const val BOOKS_FOLDER = "Books"

    /** The Tool Manager serves everything below this directory of `filesDir`. */
    private val SHARED_DIR = LightFileProvider.SHARED_DIR

    @Volatile
    private var root: File? = null

    val isReady: Boolean get() = root != null

    fun init(filesDir: File) {
        if (root == null) {
            synchronized(this) {
                if (root == null) root = filesDir
            }
        }
    }

    val filesDir: File
        get() = checkNotNull(root) { "AppFiles.init was never called" }

    /** The folder the Tool Manager's file browser shows, created on first use. */
    val booksFolder: File
        get() = File(File(filesDir, SHARED_DIR), BOOKS_FOLDER).apply { mkdirs() }

    fun dir(name: String): File = File(filesDir, name).apply { mkdirs() }

    /**
     * Write [text] to [file] without ever leaving a half-written file behind: the bytes go to a
     * sibling first and are renamed over the target, so a process killed mid-write leaves the
     * previous version intact.
     */
    fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
