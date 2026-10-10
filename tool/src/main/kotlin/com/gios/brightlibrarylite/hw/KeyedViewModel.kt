package com.gios.brightlibrarylite.hw

import android.view.KeyEvent
import com.thelightphone.sdk.LightViewModel

/**
 * The view model every screen in this tool uses, for one reason: it is where a Light SDK tool
 * receives hardware keys. The full app caught them in its Activity's `dispatchKeyEvent`; here the
 * SDK hands them to the current screen's view model, and this passes the wheel to [WheelBus] and,
 * when a reader has claimed them, the volume keys to [VolumeBus].
 *
 * A wheel notch is always consumed, both halves of it, so LightOS does not also act on it. A volume
 * key is consumed only while claimed; otherwise it falls through to the system and changes the
 * volume as usual.
 */
open class KeyedViewModel<R> : LightViewModel<R>() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (LightKeys.of(keyCode, event)) {
            LightKey.WheelUp -> {
                WheelBus.send(1)
                return true
            }
            LightKey.WheelDown -> {
                WheelBus.send(-1)
                return true
            }
            null -> Unit
        }
        if (VolumeBus.claimed) {
            val step = volumePageStep(keyCode)
            if (step != null) {
                if (event.repeatCount == 0) VolumeBus.send(step)
                return true
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (LightKeys.of(keyCode, event) != null) return true
        return VolumeBus.claimed && volumePageStep(keyCode) != null
    }
}
