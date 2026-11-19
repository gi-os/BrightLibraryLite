package com.gios.brightlibrarylite.ui.light

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/**
 * A full-screen error: what happened, in text that is read, and one thing to do next.
 *
 * Copy size and not lightened, because the reader needs this to get out of the screen.
 */
@Composable
fun LightNotice(
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = lightInset()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(message, LightTextVariant.Copy, align = TextAlign.Center)
        LightText(
            text = actionText,
            variant = LightTextVariant.Button,
            underline = true,
            align = TextAlign.Center,
            modifier = Modifier
                .lightClickable(onClick = onAction)
                .padding(vertical = 14f.designVerticalPxToDp()),
        )
    }
}
