package com.gios.brightlibrarylite.ui

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Work to do when the tool leaves the screen.
 *
 * The full app's reader observed its Activity's ON_STOP through `LocalLifecycleOwner`, which a Light
 * SDK tool may not touch. The SDK tells a screen instead (`onAppPause`, `willHide`), and the screen
 * runs these: a reader registers "save my place" while it is composed.
 */
object PauseHooks {
    private val hooks = CopyOnWriteArrayList<() -> Unit>()

    fun add(hook: () -> Unit) {
        hooks.add(hook)
    }

    fun remove(hook: () -> Unit) {
        hooks.remove(hook)
    }

    fun run() {
        hooks.forEach { runCatching { it() } }
    }
}
