package com.gios.brightlibrarylite.data

import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The settings, as one JSON document in `files/settings.json`.
 *
 * The full app kept the same JSON in SharedPreferences, which needs a `Context`. The document and the
 * [Settings] class are unchanged; only where it is written moved.
 */
object SettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    val state = mutableStateOf(Settings())

    @Volatile
    private var loaded = false

    private fun file(): File = File(AppFiles.filesDir, "settings.json")

    /** Read the settings from disk once. Safe to call from every screen. */
    fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val raw = runCatching { file().takeIf { it.exists() }?.readText() }.getOrNull()
            state.value = raw?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() }
                ?: Settings()
            loaded = true
        }
    }

    fun update(transform: (Settings) -> Settings) {
        synchronized(lock) {
            val newSettings = transform(state.value)
            state.value = newSettings
            runCatching {
                AppFiles.writeAtomically(file(), json.encodeToString(Settings.serializer(), newSettings))
            }
        }
    }
}
