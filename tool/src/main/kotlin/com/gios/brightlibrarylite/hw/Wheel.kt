package com.gios.brightlibrarylite.hw

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * Wheel notches on their way from the key handler to whatever is on screen.
 *
 * In the full app the Activity's `dispatchKeyEvent` fed this. A Light SDK tool never sees the
 * Activity: key events arrive at the current screen's view model instead (see [KeyedViewModel]),
 * which sends them here. Only one screen is composed at a time, so one bus for the whole tool is
 * enough.
 *
 * One notch per event, positive for up. No replay, deliberately: a notch that arrives while nothing
 * is listening is gone. Buffered generously because the sensor emits bursts faster than a frame.
 */
object WheelBus {
    private val _notches = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val notches: SharedFlow<Int> = _notches.asSharedFlow()

    fun send(notches: Int) {
        _notches.tryEmit(notches)
    }
}

/**
 * Distance per notch. About six notches to a screenful on the LPIII panel — enough that a
 * flick of the wheel moves you somewhere, short enough that you can land on a paragraph.
 */
private val NOTCH = 64.dp

/**
 * `1` means turning the wheel up moves you *down* the list — the wheel drags the page the way a
 * finger flick does. This is the convention the full app used through light-common.
 */
private const val DIRECTION = 1

/**
 * Fraction of the remaining distance applied per frame. Every notch adds to a debt and each frame
 * pays off a share of it, so one notch glides and a fast spin becomes one continuous sweep.
 */
private const val SMOOTHING = 0.28f

/**
 * The first notch after a pause buys nothing on its own: the wheel sits under a thumb and catches
 * stray brushes. A second notch inside [IDLE_MS] releases both.
 */
private const val ARM_NOTCHES = 2
private const val IDLE_MS = 1_500L

/** Point the wheel at a Compose scroller. Works for `ScrollState`, `LazyListState` and `LazyGridState`. */
@Composable
fun WheelScroll(state: ScrollableState, active: Boolean = true) {
    val step = with(LocalDensity.current) { NOTCH.toPx() }
    val debt = remember { Debt() }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }

    ArmedNotches(active) { notches ->
        debt.px += notches * step * DIRECTION
        wake.trySend(Unit)
    }

    LaunchedEffect(state, wake) {
        while (true) {
            wake.receive()
            state.scroll {
                while (abs(debt.px) > 0.5f) {
                    withFrameNanos { }
                    val wanted = (debt.px * SMOOTHING).let { if (abs(it) < 1f) debt.px else it }
                    debt.px -= wanted
                    val consumed = scrollBy(wanted)
                    if (abs(consumed) < abs(wanted) - 0.5f) debt.px = 0f
                }
            }
        }
    }
}

/**
 * The wheel as a stepper: one call per notch, positive for up, with no smoothing and no stray-brush
 * guard. The readers count notches themselves (several to a page turn), which is their own guard.
 */
@Composable
fun WheelSteps(onStep: (Int) -> Unit) {
    val handler by rememberUpdatedState(onStep)
    LaunchedEffect(Unit) {
        WheelBus.notches.collect { handler(it) }
    }
}

@Composable
private fun ArmedNotches(active: Boolean, onNotch: (Int) -> Unit) {
    val handler by rememberUpdatedState(onNotch)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var armed = false
        var held = 0
        var count = 0
        var last = 0L
        WheelBus.notches.collect { notches ->
            val now = System.nanoTime() / 1_000_000
            if (now - last > IDLE_MS) {
                armed = false
                held = 0
                count = 0
            }
            last = now
            if (armed) {
                handler(notches)
                return@collect
            }
            held += notches
            count++
            if (count >= ARM_NOTCHES) {
                armed = true
                if (held != 0) handler(held) else handler(notches.sign)
                held = 0
            }
        }
    }
}

/** Distance still owed to the scroller. Not Compose state: nothing in composition reads it. */
private class Debt {
    @Volatile
    var px: Float = 0f
}
