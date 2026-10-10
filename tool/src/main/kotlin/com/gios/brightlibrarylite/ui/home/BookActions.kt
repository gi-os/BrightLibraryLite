package com.gios.brightlibrarylite.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import com.gios.brightlibrarylite.data.Book
import com.gios.brightlibrarylite.data.BookKind
import com.gios.brightlibrarylite.ui.light.LightRule
import com.gios.brightlibrarylite.ui.light.LightText
import com.gios.brightlibrarylite.ui.light.LightTextVariant
import com.gios.brightlibrarylite.ui.light.LightThemeTokens
import com.gios.brightlibrarylite.ui.light.designVerticalPxToDp
import com.gios.brightlibrarylite.ui.light.gridUnitsAsDp
import com.gios.brightlibrarylite.ui.light.lightClickable
import com.gios.brightlibrarylite.ui.light.lightInset

/**
 * What you can do to a book, on a long press.
 *
 * A full-screen page rather than an AlertDialog. LightOS has no floating dialogs — a decision
 * takes over the screen and then gives it back. In the Lite tool it is its own screen, so the
 * back gesture closes it. FIND COVER is gone with the Open Library lookup it ran.
 */
@Composable
fun BookActions(
    book: Book,
    onRename: () -> Unit,
    onFlipDirection: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    var confirmingDelete by remember { mutableStateOf(false) }

    run {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .padding(horizontal = lightInset()),
            verticalArrangement = Arrangement.Center,
        ) {
            LightText(
                text = book.title,
                variant = LightTextVariant.Subheading,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                LightText(book.author, LightTextVariant.Detail, lighten = true, maxLines = 1)
            }
            Spacer(Modifier.height(1f.gridUnitsAsDp()))
            LightRule()

            if (confirmingDelete) {
                // Two taps to lose a book, and the second one says what it does rather than
                // asking again in a smaller box.
                LightText(
                    text = "This also deletes the file from the Books folder.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 8f.designVerticalPxToDp()),
                )
                ActionRow(text = "DELETE — TAP TO CONFIRM", onClick = onDelete)
                ActionRow(text = "KEEP IT", onClick = { confirmingDelete = false })
            } else if (book.kind == BookKind.Comic) {
                // A comic has no catalogue to search and no metadata worth correcting — its first
                // page is its cover and its file name is its title. What it does have is a reading
                // direction, and that is the one thing about it worth being able to change.
                ActionRow(
                    text = if (book.rightToLeft) "READ LEFT TO RIGHT" else "READ RIGHT TO LEFT",
                    onClick = onFlipDirection,
                )
                ActionRow(text = "FIX NAME", onClick = onRename)
                ActionRow(text = "DELETE", onClick = { confirmingDelete = true })
                ActionRow(text = "CANCEL", lighten = true, onClick = onDismiss)
            } else {
                // A wrong title is also what files a volume under the wrong series stack.
                ActionRow(text = "FIX NAME", onClick = onRename)
                ActionRow(text = "DELETE", onClick = { confirmingDelete = true })
                ActionRow(text = "CANCEL", lighten = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    lighten: Boolean = false,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().lightClickable(onClick = onClick)) {
        LightText(
            text = text,
            variant = LightTextVariant.Button,
            lighten = lighten,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 14f.designVerticalPxToDp()),
        )
        LightRule()
    }
}
