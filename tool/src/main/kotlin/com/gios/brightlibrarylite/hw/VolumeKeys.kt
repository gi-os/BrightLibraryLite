package com.gios.brightlibrarylite.hw

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which way a volume key turns a page: +1 forward, -1 back, null for any other key.
 *
 * **Down is forward**, the way KOReader and every other e-reader with volume page turns does it, and
 * the way the thumb wants it: the lower key sits under the thumb at rest, and forward is the press
 * you make hundreds of times a book.
 */
fun volumePageStep(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> 1
    KeyEvent.KEYCODE_VOLUME_UP -> -1
    else -> null
}

/**
 * Volume presses on their way to whichever reader is up.
 *
 * Claimed rather than always live. Consuming a volume key means the system never sees it: no volume
 * slider, and no change in volume either. That is the price of turning a page with it, and it may
 * only be paid on the screens that asked for it - a claim is taken by the reader that is listening
 * and dropped when it leaves, so the shelf and settings still change the volume.
 */
object VolumeBus {
    private val claims = AtomicInteger(0)

    private val _presses = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val presses: SharedFlow<Int> = _presses

    /** Whether any screen currently wants volume keys taken away from the system. */
    val claimed: Boolean get() = claims.get() > 0

    fun claim() {
        claims.incrementAndGet()
    }

    fun release() {
        claims.decrementAndGet()
    }

    fun send(step: Int) {
        _presses.tryEmit(step)
    }
}

/**
 * One volume press, one page turn. [enabled] false does nothing at all - no claim - so with the
 * setting off the volume keys belong to the system exactly as before.
 *
 * The full app needed a second path for readers drawn in a Dialog window. The Lite tool draws
 * everything in the tool's one window, so the claim is the whole of it.
 */
@Composable
fun VolumePageTurns(enabled: Boolean, onTurn: (Int) -> Unit) {
    val handler by rememberUpdatedState(onTurn)
    if (!enabled) return
    DisposableEffect(Unit) {
        VolumeBus.claim()
        onDispose { VolumeBus.release() }
    }
    LaunchedEffect(Unit) { VolumeBus.presses.collect { handler(it) } }
}
