package com.gios.brightlibrarylite.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Keeps the shelf in step with the Tool Manager's Books folder.
 *
 * This replaces the full app's ADD button. A Light SDK tool cannot open the Android file picker or
 * read a `content://` Uri, so books reach the phone the Light way instead: the Tool Manager in a
 * browser on the computer shows a "Books" folder for this tool, files dropped there land in
 * `files/shared/Books`, and [sync] imports whatever is new. It runs when the shelf is opened and,
 * if the tool is already running, when the Tool Manager reports a change.
 *
 * The folder is the source of truth for which books exist:
 *
 * - a supported file that no book was made from is imported;
 * - a file replaced by a different one under the same name (a different size) is imported again,
 *   keeping the reading position where it still fits;
 * - a book whose file was deleted from the folder is taken off the shelf;
 * - a file that failed to import is remembered by name and size, so a broken 200 MB archive is not
 *   re-read on every open. Replacing it with a different file tries again.
 *
 * Deleting a book on the phone deletes its file from the folder too (see
 * [BookRepository.deleteBook]), so the two never disagree for long.
 */
object Library {

    /** What the folder accepts. CBR is RAR, which nothing on the phone can open, so it is not here. */
    val SUPPORTED_EXTENSIONS = setOf("epub", "mobi", "azw", "azw3", "cbz", "zip")

    data class SyncState(
        val running: Boolean = false,
        /** What is happening right now, for the shelf to show while it waits. */
        val step: String? = null,
        /** What the last sweep has to say: failures and skipped files. Null when there is nothing. */
        val message: String? = null,
    )

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val failedSerializer = MapSerializer(String.serializer(), Long.serializer())

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * Sweep the Books folder. Safe to call from anywhere and as often as wanted: concurrent calls
     * queue behind one another, and a sweep with nothing new to read costs a directory listing.
     */
    suspend fun sync() {
        if (!AppFiles.isReady) return
        mutex.withLock {
            withContext(Dispatchers.IO) { sweep() }
        }
    }

    private fun sweep() {
        BookRepository.load()
        SettingsRepository.load()
        val folder = AppFiles.booksFolder
        val files = listBookFiles(folder)
        val failed = loadFailed().toMutableMap()
        val problems = ArrayList<String>()

        // Books whose file is gone. Removed first, so a file that was replaced under a new name is
        // not briefly on the shelf twice.
        val present = files.associateBy { relativeName(folder, it) }
        for (book in BookRepository.books.toList()) {
            val source = book.sourceFile ?: continue
            if (source !in present) BookRepository.deleteBook(book, removeSource = false)
        }

        val pending = files.filter { file ->
            val name = relativeName(folder, file)
            val size = file.length()
            val known = BookRepository.books.firstOrNull { it.sourceFile == name }
            when {
                known != null && known.sourceSize == size -> false
                failed[name] == size -> false
                else -> true
            }
        }

        val unsupported = files.filter { it.extension.lowercase() !in SUPPORTED_EXTENSIONS }
        if (unsupported.isNotEmpty()) {
            problems += if (unsupported.size == 1) {
                "${unsupported.first().name} is not a format this reads (EPUB, MOBI or CBZ)."
            } else {
                "${unsupported.size} files are not a format this reads (EPUB, MOBI or CBZ)."
            }
        }

        val toImport = pending.filter { it.extension.lowercase() in SUPPORTED_EXTENSIONS }
        if (toImport.isEmpty()) {
            _state.value = SyncState(running = false, step = null, message = problems.firstOrNull())
            return
        }

        _state.value = SyncState(running = true, step = "Importing…", message = null)
        toImport.forEachIndexed { index, file ->
            val name = relativeName(folder, file)
            val size = file.length()
            val counter = if (toImport.size > 1) " (${index + 1} of ${toImport.size})" else ""
            _state.value = _state.value.copy(step = "Importing ${file.name}$counter…")

            // A file replaced under the same name: drop the old book, keep the place.
            val previous = BookRepository.books.firstOrNull { it.sourceFile == name }
            if (previous != null) BookRepository.deleteBook(previous, removeSource = false)

            val result = runCatching {
                Importer.importFile(
                    file = file,
                    displayName = file.name,
                    sourceFile = name,
                    sourceSize = size,
                ) { done, total ->
                    if (total > 0) {
                        _state.value = _state.value.copy(
                            step = "Converting page $done of $total$counter…",
                        )
                    }
                }
            }.getOrElse { Importer.Result.Failed(it.message ?: "Could not read ${file.name}.") }

            when (result) {
                is Importer.Result.Added -> {
                    failed.remove(name)
                    if (previous != null && previous.kind == result.book.kind && previous.currentWordIndex > 0) {
                        val keep = previous.currentWordIndex.coerceAtMost((result.book.totalWords - 1).coerceAtLeast(0))
                        BookRepository.updateProgress(result.book.id, keep)
                    }
                }
                is Importer.Result.Failed -> {
                    failed[name] = size
                    problems += "${file.name}: ${result.message}"
                }
            }
        }
        saveFailed(failed.filterKeys { it in present })

        val message = when (problems.size) {
            0 -> null
            1 -> problems.first()
            else -> problems.first() + " (and ${problems.size - 1} more)"
        }
        _state.value = SyncState(running = false, step = null, message = message)
    }

    /** Every regular, visible file under [folder], subfolders included. */
    internal fun listBookFiles(folder: File): List<File> =
        folder.walkTopDown()
            .onEnter { dir -> dir == folder || !dir.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".tmp") }
            .sortedBy { it.lastModified() }
            .toList()

    /** A file's name relative to the Books folder, with forward slashes. */
    internal fun relativeName(folder: File, file: File): String =
        file.relativeTo(folder).invariantSeparatorsPath

    private fun failedFile(): File = File(AppFiles.filesDir, "import-failures.json")

    private fun loadFailed(): Map<String, Long> = runCatching {
        val f = failedFile()
        if (!f.exists()) emptyMap() else json.decodeFromString(failedSerializer, f.readText())
    }.getOrDefault(emptyMap())

    private fun saveFailed(map: Map<String, Long>) {
        runCatching { AppFiles.writeAtomically(failedFile(), json.encodeToString(failedSerializer, map)) }
    }
}
