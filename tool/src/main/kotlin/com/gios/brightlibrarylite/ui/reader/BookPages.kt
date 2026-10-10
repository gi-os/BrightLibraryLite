package com.gios.brightlibrarylite.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.gios.brightlibrarylite.data.BookRepository
import com.gios.brightlibrarylite.data.Fonts
import com.gios.brightlibrarylite.data.LoadedText
import com.gios.brightlibrarylite.data.SettingsRepository
import com.gios.brightlibrarylite.ui.light.LightText
import com.gios.brightlibrarylite.ui.light.LightTextVariant
import com.gios.brightlibrarylite.ui.light.lightInset
import com.gios.brightlibrarylite.ui.theme.toLightPhoneGrey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A text book's home screen: its pages.
 *
 * The full app drew the page view as a Dialog on top of the word reader and toggled between the
 * two. Here each is its own SDK screen — this one opens when a book is tapped, FASTREAD opens the
 * word reader on top of it, and the back gesture from the word reader returns here at the word it
 * reached — so the loading and colour handling the full app's reader did before showing the pages
 * is done here instead. The page view itself ([EreaderScreen]) is the full app's, unchanged.
 */
@Composable
fun BookPages(
    bookId: String,
    onFastRead: (wordIndex: Int) -> Unit,
    onExit: () -> Unit,
) {
    val settings by SettingsRepository.state
    // Read on every composition rather than remembered: coming back from the word reader, the
    // saved position has moved, and that is where the pages must open.
    val book = BookRepository.getBook(bookId)
    if (book == null) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    var loaded by remember(bookId) { mutableStateOf<LoadedText?>(null) }
    LaunchedEffect(bookId) {
        loaded = withContext(Dispatchers.IO) { BookRepository.loadWordsAndBreaks(book) }
    }

    val text = loaded
    if (text == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LightText("Opening…", LightTextVariant.Copy, lighten = true)
        }
        return
    }
    if (text.words.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = lightInset()), contentAlignment = Alignment.Center) {
            LightText(
                text = "No readable text in this book.",
                variant = LightTextVariant.Detail,
                align = TextAlign.Center,
            )
        }
        return
    }

    val titleColor = Color(settings.titleColorArgb).toLightPhoneGrey()
    val titleWordFlags = remember(book.chapters, text.words.size) {
        computeTitleWordFlags(book.chapters, text.words.size)
    }
    val start = book.currentWordIndex.coerceIn(0, text.words.size - 1)

    EreaderScreen(
        bookId = bookId,
        title = book.title,
        words = text.words,
        paragraphBreakAfter = text.paragraphBreakAfter,
        isItalicWord = text.italic,
        isTitleWord = titleWordFlags,
        titleColor = titleColor,
        titleStyle = settings.titleStyle,
        fontFamily = Fonts.familyFor(settings.fontFamily),
        fontKey = settings.fontFamily,
        fontSizeSp = settings.readerFontSizeSp,
        initialWordIndex = start,
        volumeKeysTurnPages = settings.volumeKeysTurnPages,
        // Persisted on every settled page, because leaving the phone with the home key never runs
        // an exit path.
        onProgress = { newIndex ->
            BookRepository.updateProgress(bookId, newIndex.coerceIn(0, text.words.size - 1))
        },
        onFastRead = { newIndex ->
            BookRepository.updateProgress(bookId, newIndex.coerceIn(0, text.words.size - 1))
            onFastRead(newIndex)
        },
        onExit = { newIndex ->
            BookRepository.updateProgress(bookId, newIndex.coerceIn(0, text.words.size - 1))
            onExit()
        },
    )
}
