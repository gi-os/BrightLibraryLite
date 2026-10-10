package com.gios.brightlibrarylite.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.gios.brightlibrarylite.hw.WheelScroll
import com.gios.brightlibrarylite.data.Book
import com.gios.brightlibrarylite.data.BookRepository
import com.gios.brightlibrarylite.data.Shelf
import com.gios.brightlibrarylite.ui.light.LightBarItem
import com.gios.brightlibrarylite.ui.light.LightIcons
import com.gios.brightlibrarylite.ui.light.LightText
import com.gios.brightlibrarylite.ui.light.LightTextVariant
import com.gios.brightlibrarylite.ui.light.LightThemeTokens
import com.gios.brightlibrarylite.ui.light.LightTopBar
import com.gios.brightlibrarylite.ui.light.gridUnitsAsDp
import com.gios.brightlibrarylite.ui.light.lightInset

/**
 * One series, as a shelf of its own.
 *
 * The same grid, the same cells, the same gestures as the main shelf — because it is the same idea
 * one level down, and a series that looked like a list would be a second design for no reason. The
 * only difference is the order: volumes run 1, 2, 3 rather than newest-first, since inside a series
 * the reading order is the only order anybody wants.
 *
 * Keyed by [com.gios.brightlibrarylite.data.SeriesTitle.key] rather than by the display name, so the screen
 * survives a volume being renamed to a fuller spelling of the series while it is open.
 */
@Composable
fun SeriesShelf(
    seriesKey: String,
    onOpenBook: (Book) -> Unit,
    onBookActions: (Book) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val books = BookRepository.books

    val volumes = Shelf.volumesOf(books.toList(), seriesKey)
    val name = Shelf.nameOf(books.toList(), seriesKey)
    val gridState = rememberLazyGridState()
    WheelScroll(gridState)

    // Deleting the second-to-last volume dissolves the series; there is nothing left to show and
    // nothing to decide, so the screen takes itself away rather than sitting there empty.
    LaunchedEffect(volumes.size) {
        if (volumes.size < 2) onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        LightTopBar(
            title = name,
            left = LightBarItem.Icon(LightIcons.Back, onClick = onBack),
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (volumes.isEmpty()) {
                LightText(
                    text = "Nothing here any more.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = lightInset(),
                        end = lightInset(),
                        top = 0.5f.gridUnitsAsDp(),
                        bottom = 1f.gridUnitsAsDp(),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(1f.gridUnitsAsDp()),
                    verticalArrangement = Arrangement.spacedBy(1f.gridUnitsAsDp()),
                ) {
                    items(volumes, key = { it.id }) { book ->
                        ShelfBook(
                            book = book,
                            onClick = { onOpenBook(book) },
                            onLongClick = { onBookActions(book) },
                        )
                    }
                }
            }
        }
    }
}
