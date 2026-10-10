package com.gios.brightlibrarylite.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.gios.brightlibrarylite.data.Book
import com.gios.brightlibrarylite.data.BookRepository
import com.gios.brightlibrarylite.data.Library
import com.gios.brightlibrarylite.data.SeriesTitle
import com.gios.brightlibrarylite.data.Shelf
import com.gios.brightlibrarylite.data.ShelfEntry
import com.gios.brightlibrarylite.hw.WheelScroll
import com.gios.brightlibrarylite.ui.light.LightBarItem
import com.gios.brightlibrarylite.ui.light.LightBottomBar
import com.gios.brightlibrarylite.ui.light.LightIcons
import com.gios.brightlibrarylite.ui.light.LightRule
import com.gios.brightlibrarylite.ui.light.LightText
import com.gios.brightlibrarylite.ui.light.LightTextVariant
import com.gios.brightlibrarylite.ui.light.LightThemeTokens
import com.gios.brightlibrarylite.ui.light.designVerticalPxToDp
import com.gios.brightlibrarylite.ui.light.gridUnitsAsDp
import com.gios.brightlibrarylite.ui.light.lightClickable
import com.gios.brightlibrarylite.ui.light.lightInset

/**
 * The shelf.
 *
 * Two covers to a row, which on the LP3's 411dp width gives each book about 180dp — big enough
 * that real cover art is worth looking at. Everything else here is white on black in the SDK's own
 * grid and type scale.
 *
 * **"Books" is not a fixed top bar** — it is the grid's own first item, spanning both columns, so it
 * scrolls off with the rest of the shelf instead of sitting pinned above it.
 *
 * The full app's bottom bar had LIBRARY (Calibre), SETTINGS and ADD (the Android file picker). The
 * Lite tool has neither Calibre nor a file picker: ADD opens a page that explains the Tool Manager's
 * Books folder, which is how books get here now. While [Library] is importing, the shelf says so.
 */
@Composable
fun HomeScreen(
    onOpenBook: (Book) -> Unit,
    onOpenSettings: () -> Unit,
    onAddBooks: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onBookActions: (Book) -> Unit,
) {
    val books = BookRepository.books
    val colors = LightThemeTokens.colors
    val sync by Library.state.collectAsState()

    // Recomputed whenever the shelf changes, which is cheap: a parse of each title and a group-by,
    // over a list that is tens of items long, not thousands.
    val entries = remember(books.toList()) { Shelf.entries(books.toList()) }
    val importing = sync.running
    val importStep = sync.step
    val status = sync.message
    val gridState = rememberLazyGridState()
    WheelScroll(gridState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (books.isEmpty() && !importing) {
                // Nothing to scroll, so the title stays outside the grid rather than floating
                // above an otherwise-empty screen with no content to earn it.
                Column(Modifier.fillMaxSize()) {
                    ShelfTitle()
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        EmptyShelf(modifier = Modifier.align(Alignment.Center))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = lightInset(),
                        end = lightInset(),
                        top = 0.5f.gridUnitsAsDp(),
                        bottom = 1f.gridUnitsAsDp(),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(1f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.spacedBy(1f.gridUnitsAsDp()),
                ) {
                    // The title is the grid's own first row, spanning every column, so it scrolls
                    // away with the covers instead of sitting pinned above them in a fixed bar.
                    item(key = "title", span = { GridItemSpan(maxLineSpan) }) { ShelfTitle() }
                    items(
                        items = entries,
                        // A stack is keyed by its series, a book by its id. Without a stable key
                        // here, adding a volume would rebuild every cell on the shelf.
                        key = { entry ->
                            when (entry) {
                                is ShelfEntry.Single -> entry.book.id
                                is ShelfEntry.Stack -> "series:" + SeriesTitle.key(entry.series)
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is ShelfEntry.Single -> ShelfBook(
                                book = entry.book,
                                onClick = { onOpenBook(entry.book) },
                                onLongClick = { onBookActions(entry.book) },
                            )
                            is ShelfEntry.Stack -> ShelfStack(
                                stack = entry,
                                onClick = { onOpenSeries(SeriesTitle.key(entry.series)) },
                            )
                        }
                    }
                }
            }
            if (importing && books.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        importStep ?: "Importing…",
                        LightTextVariant.Copy,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = lightInset()),
                    )
                }
            }
        }

        // While importing with books already on the shelf, the shelf stays usable and the step is
        // shown here instead of covering it.
        (if (importing && books.isNotEmpty()) importStep else status)?.let { message ->
            Column(Modifier.lightClickable { if (!importing) Library.clearMessage() }) {
                LightRule()
                LightText(
                    text = message,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = lightInset(), vertical = 8f.designVerticalPxToDp()),
                )
            }
        }

        LightRule()
        LightBottomBar(
            // Icons, as in the full app. Add sits last, after Settings.
            items = listOf(
                LightBarItem.Icon(LightIcons.Settings, onClick = onOpenSettings),
                LightBarItem.Icon(icon = LightIcons.Add, onClick = onAddBooks),
            ),
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

/**
 * The shelf's own title, standing in for a fixed top bar.
 *
 * A grid item rather than a [LightTopBar] — this is what lets it scroll away with the covers.
 * Left-aligned and full width, matching the inset every cover row already sits inside.
 */
@Composable
private fun ShelfTitle(modifier: Modifier = Modifier) {
    LightText(
        text = "Books",
        variant = LightTextVariant.Heading,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = lightInset(),
                vertical = 0.5f.gridUnitsAsDp(),
            ),
    )
}

@Composable
private fun EmptyShelf(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = lightInset()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText("No books yet", LightTextVariant.Subheading, align = TextAlign.Center)
        Spacer(Modifier.height(10f.designVerticalPxToDp()))
        LightText(
            text = "Open the Tool Manager on your computer and drop EPUB, MOBI or CBZ files into " +
                "BrightLibrary Lite's Books folder. They appear here, cover and all.",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
        )
    }
}
