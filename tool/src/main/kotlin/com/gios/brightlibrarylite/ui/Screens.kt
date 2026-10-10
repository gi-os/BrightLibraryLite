package com.gios.brightlibrarylite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.gios.brightlibrarylite.data.AppFiles
import com.gios.brightlibrarylite.data.Book
import com.gios.brightlibrarylite.data.BookKind
import com.gios.brightlibrarylite.data.BookRepository
import com.gios.brightlibrarylite.data.Library
import com.gios.brightlibrarylite.data.SettingsRepository
import com.gios.brightlibrarylite.hw.KeyedViewModel
import com.gios.brightlibrarylite.hw.WheelScroll
import com.gios.brightlibrarylite.ui.comic.ComicReader
import com.gios.brightlibrarylite.ui.home.BookActions
import com.gios.brightlibrarylite.ui.home.HomeScreen
import com.gios.brightlibrarylite.ui.home.SeriesShelf
import com.gios.brightlibrarylite.ui.light.LightBarItem
import com.gios.brightlibrarylite.ui.light.LightBottomBar
import com.gios.brightlibrarylite.ui.light.LightIcons
import com.gios.brightlibrarylite.ui.light.LightRule
import com.gios.brightlibrarylite.ui.light.LightText
import com.gios.brightlibrarylite.ui.light.LightTextVariant
import com.gios.brightlibrarylite.ui.light.LightThemeTokens
import com.gios.brightlibrarylite.ui.light.LightTopBar
import com.gios.brightlibrarylite.ui.light.designVerticalPxToDp
import com.gios.brightlibrarylite.ui.light.gridUnitsAsDp
import com.gios.brightlibrarylite.ui.light.lightClickable
import com.gios.brightlibrarylite.ui.light.lightInset
import com.gios.brightlibrarylite.ui.reader.BookPages
import com.gios.brightlibrarylite.ui.reader.ReaderScreen
import com.gios.brightlibrarylite.ui.settings.SettingsScreen
import com.gios.brightlibrarylite.ui.theme.FastReadTheme
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import kotlinx.coroutines.launch

/**
 * Every screen in the tool.
 *
 * The full app was one Activity with a Compose NavHost. A Light SDK tool is a stack of
 * `LightScreen`s instead, and the system back gesture pops the current one. That decided where the
 * seams are: anything the back gesture should close (a book's actions, the word reader on top of
 * the pages) is its own screen, and the reader's sheets are drawn inside the reader.
 */
abstract class BooksScreen<R>(sealedActivity: SealedLightActivity) :
    LightScreen<R, KeyedViewModel<R>>(sealedActivity) {

    init {
        // The SDK gives screens the tool's files directory; everything on disk hangs off it.
        AppFiles.init(lightContext.filesDir)
        BookRepository.load()
        SettingsRepository.load()
    }

    @Suppress("UNCHECKED_CAST")
    override val viewModelClass: Class<KeyedViewModel<R>> = KeyedViewModel::class.java as Class<KeyedViewModel<R>>

    override fun createViewModel(): KeyedViewModel<R> = KeyedViewModel()

    override fun onAppPause() {
        PauseHooks.run()
    }

    override fun willHide() {
        PauseHooks.run()
    }

    @Composable
    final override fun Content() {
        FastReadTheme { Screen() }
    }

    @Composable
    abstract fun Screen()
}

/** The shelf. Sweeps the Books folder every time it is shown. */
@InitialScreen
class ShelfScreen(sealedActivity: SealedLightActivity) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        LaunchedEffect(Unit) { Library.sync() }
        HomeScreen(
            onOpenBook = { openBook(this, it) },
            onOpenSettings = { navigateTo(::SettingsPage) },
            onAddBooks = { navigateTo(::AddBooksScreen) },
            onOpenSeries = { key -> navigateTo({ SeriesScreen(it, key) }) },
            onBookActions = { book -> navigateTo({ BookActionsScreen(it, book.id) }) },
        )
    }
}

internal fun openBook(from: BooksScreen<*>, book: Book) {
    if (book.kind == BookKind.Comic) {
        from.navigateTo({ ComicScreen(it, book.id) })
    } else {
        from.navigateTo({ BookScreen(it, book.id) })
    }
}

class SeriesScreen(
    sealedActivity: SealedLightActivity,
    private val seriesKey: String,
) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        SeriesShelf(
            seriesKey = seriesKey,
            onOpenBook = { openBook(this, it) },
            onBookActions = { book -> navigateTo({ BookActionsScreen(it, book.id) }) },
            onBack = { goBack() },
        )
    }
}

/** A text book, opened to its pages. FASTREAD puts the word reader on top. */
class BookScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: String,
) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        BookPages(
            bookId = bookId,
            onFastRead = { index -> navigateTo({ RsvpScreen(it, bookId, index) }) },
            onExit = { goBack() },
        )
    }
}

/** The RSVP word reader. Returns the word it reached, which the pages then open at. */
class RsvpScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: String,
    private val startIndex: Int,
) : BooksScreen<Int>(sealedActivity) {

    @Composable
    override fun Screen() {
        ReaderScreen(
            bookId = bookId,
            startIndex = startIndex,
            onBack = { index ->
                BookRepository.updateProgress(bookId, index)
                goBack(index)
            },
        )
    }
}

class ComicScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: String,
) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        ComicReader(bookId = bookId, onBack = { goBack() })
    }
}

class SettingsPage(sealedActivity: SealedLightActivity) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        SettingsScreen(onBack = { goBack() })
    }
}

/** A book's long-press actions. */
class BookActionsScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: String,
) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        val book = BookRepository.books.firstOrNull { it.id == bookId }
        if (book == null) {
            LaunchedEffect(Unit) { goBack() }
            return
        }
        BookActions(
            book = book,
            onRename = { navigateTo({ RenameScreen(it, bookId) }) { goBack() } },
            onFlipDirection = {
                BookRepository.setReadingDirection(book.id, !book.rightToLeft)
                goBack()
            },
            onDelete = {
                BookRepository.deleteBook(book)
                goBack()
            },
            onDismiss = { goBack() },
        )
    }
}

/**
 * Fix a book's title and author, on the LP3 keyboard.
 *
 * The full app had one page with two text fields and the system keyboard. The SDK's way is a field
 * that opens a full-screen editor, so each field is edited on its own; the values wait here, on the
 * screen object, while the editor is up.
 */
class RenameScreen(
    sealedActivity: SealedLightActivity,
    private val bookId: String,
) : BooksScreen<Boolean>(sealedActivity) {

    private val original = BookRepository.getBook(bookId)
    private var title by mutableStateOf(original?.title.orEmpty())
    private var author by mutableStateOf(original?.author.orEmpty())

    @Composable
    override fun Screen() {
        val colors = LightThemeTokens.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            LightTopBar(
                title = "Name",
                left = LightBarItem.Icon(LightIcons.Back, onClick = { goBack(false) }),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = lightInset()),
            ) {
                LightTextField(
                    label = "Title",
                    value = title,
                    placeholder = "Title",
                    onClick = {
                        navigateTo({ TextEditorScreen(it, "Title", title) }) { typed ->
                            if (typed.isNotBlank()) title = typed.trim()
                        }
                    },
                )
                LightTextField(
                    label = "Author",
                    value = author,
                    placeholder = "Author",
                    onClick = {
                        navigateTo({ TextEditorScreen(it, "Author", author) }) { typed ->
                            author = typed.trim()
                        }
                    },
                )
                Spacer(Modifier.height(0.7f.gridUnitsAsDp()))
                LightText(
                    text = "Volumes stack into a series by their titles: \"Name, Vol. 3\", \"Name #3\" " +
                        "or \"Name v03\".",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
            LightRule()
            LightBottomBar(
                items = listOf(
                    LightBarItem.Text(text = "CANCEL", lighten = true, onClick = { goBack(false) }),
                    LightBarItem.Text(
                        text = "SAVE",
                        onClick = {
                            BookRepository.rename(bookId, title, author)
                            goBack(true)
                        },
                    ),
                ),
            )
        }
    }
}

/** Full-screen text entry on the LP3 keyboard. Returns the text; backing out returns nothing. */
class TextEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
) : BooksScreen<String>(sealedActivity) {

    private var done = false

    private fun finish(value: String?) {
        if (done) return
        done = true
        goBack(value)
    }

    @Composable
    override fun Screen() {
        val keyboardOptions = rememberKeyboardOptions()
        val state = rememberTextFieldState(initial)
        LightTextInputEditor(
            title = title,
            state = state,
            onSubmit = { finish(it.toString()) },
            onBack = { finish(null) },
            keyboardOptionsFlow = keyboardOptions,
            submitLabel = "DONE",
            singleLine = true,
            initialCaps = true,
        )
    }
}

/** What ADD does in the Lite tool: explain the Tool Manager's Books folder, and check it now. */
class AddBooksScreen(sealedActivity: SealedLightActivity) : BooksScreen<Unit>(sealedActivity) {

    @Composable
    override fun Screen() {
        val colors = LightThemeTokens.colors
        val sync by Library.state.collectAsState()
        val scope = rememberCoroutineScope()
        val scroll = rememberScrollState()
        WheelScroll(scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding(),
        ) {
            LightTopBar(
                title = "Add books",
                left = LightBarItem.Icon(LightIcons.Back, onClick = { goBack() }),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = lightInset()),
                verticalArrangement = Arrangement.Top,
            ) {
                Step("1", "On a computer on the same Wi-Fi as this phone, open the Tool Manager.")
                Step("2", "Choose BrightLibrary Lite, then the Books folder.")
                Step("3", "Upload EPUB, MOBI, AZW3 or CBZ files.")
                Step("4", "Come back here. New books are imported when the shelf opens, or straight away if it is already open.")
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
                LightText(
                    text = "Comics (CBZ, or an EPUB of page scans) are converted to greyscale pages " +
                        "for this screen when they are imported, which takes a minute for a long volume.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
                Spacer(Modifier.height(1f.gridUnitsAsDp()))
                val line = when {
                    sync.running -> sync.step ?: "Importing…"
                    sync.message != null -> sync.message
                    else -> null
                }
                if (line != null) {
                    LightText(text = line, variant = LightTextVariant.Detail)
                }
            }
            LightRule()
            LightBottomBar(
                items = listOf(
                    LightBarItem.Text(
                        text = if (sync.running) "IMPORTING" else "CHECK FOLDER",
                        lighten = sync.running,
                        onClick = { if (!sync.running) scope.launch { Library.sync() } },
                    ),
                ),
            )
        }
    }

    @Composable
    private fun Step(number: String, text: String) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6f.designVerticalPxToDp())) {
            LightText(text = "$number.  $text", variant = LightTextVariant.Copy)
        }
    }
}
