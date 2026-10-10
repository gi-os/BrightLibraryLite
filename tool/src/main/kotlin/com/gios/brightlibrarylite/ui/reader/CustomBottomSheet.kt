package com.gios.brightlibrarylite.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.gios.brightlibrarylite.ui.light.LightBarItem
import com.gios.brightlibrarylite.ui.light.LightBottomBar
import com.gios.brightlibrarylite.ui.light.LightRule
import com.gios.brightlibrarylite.ui.light.LightThemeTokens
import com.gios.brightlibrarylite.ui.light.designVerticalPxToDp
import com.gios.brightlibrarylite.ui.light.gridUnitsAsDp
import com.gios.brightlibrarylite.ui.light.lightClickable
import com.gios.brightlibrarylite.ui.theme.LocalIsLightPhone
import kotlinx.coroutines.launch

// Material3 ModalBottomSheet attaches Modifier.anchoredDraggable directly to
// its Surface. When a LazyColumn inside is at its top edge and the user swipes
// down, the LazyColumn (already at the top, can't scroll up) refuses the
// gesture and the sheet's surface-level draggable claims it — dismissing the
// sheet from a scroll attempt. A nested-scroll blocker only intercepts the
// nested-scroll path, not that direct surface draggable. This sheet drops the
// surface draggable entirely: only the explicit drag-handle dismisses by drag,
// so the LazyColumn body owns its own scroll without contention.
//
// The container is now drawn rather than borrowed from Material: a flat
// background fill with a rule along its top edge, square-cornered, because
// LightOS has no elevation, no shadow and no rounded chrome to imitate. The
// rule is also what keeps the sheet from reading as loose text floating over
// the reader, which is the job the border used to do.
//
// [actions] is optional so that callers written against the older two-argument
// form still compile; where it is supplied it becomes a LightBottomBar under
// the content.
@Composable
fun CustomBottomSheet(
    onDismiss: () -> Unit,
    actions: List<LightBarItem?> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val colors = LightThemeTokens.colors

    // Drawn over the reader in the same window rather than in a Dialog: a Light SDK tool receives
    // keys through its screen, and a Dialog window would never see the wheel.
    run {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .lightClickable(onClick = onDismiss),
        ) {
            // Half the screen is a sane cap at 890dp tall. The LPIII is ~472dp,
            // where half leaves room for about four chapter rows once the drag
            // handle is subtracted, so the sheet is allowed to go taller there.
            val maxSheetHeight =
                if (LocalIsLightPhone.current) maxHeight * 0.78f else maxHeight / 2
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .background(colors.background)
                    // Swallows taps that land on the sheet rather than the scrim. Deliberately
                    // not lightClickable: a tap on nothing should not buzz.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                LightRule()
                BottomSheetDragHandle(
                    onDrag = { dy ->
                        scope.launch {
                            offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f))
                        }
                    },
                    onDragStopped = { velocity ->
                        if (offsetY.value > DISMISS_DISTANCE_PX || velocity > DISMISS_VELOCITY) {
                            onDismiss()
                        } else {
                            offsetY.animateTo(0f, initialVelocity = velocity)
                        }
                    },
                )
                content()
                if (actions.isNotEmpty()) {
                    LightRule()
                    LightBottomBar(items = actions)
                }
            }
        }
    }
}

@Composable
private fun BottomSheetDragHandle(
    onDrag: (Float) -> Unit,
    onDragStopped: suspend (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { dy -> onDrag(dy) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> onDragStopped(velocity) },
            )
            .padding(vertical = 12f.designVerticalPxToDp()),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(2f.gridUnitsAsDp())
                .height(3f.designVerticalPxToDp())
                .background(LightThemeTokens.colors.contentFaint),
        )
    }
}

private const val DISMISS_DISTANCE_PX = 200f
private const val DISMISS_VELOCITY = 1000f
