package io.github.a1mohamad.toeflvocab.core.persistence

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.a1mohamad.toeflvocab.core.localization.Strings
import io.github.a1mohamad.toeflvocab.core.models.AppSettings
import kotlinx.serialization.json.Json

/**
 * User preferences, persisted to `SharedPreferences` as a single encoded blob.
 *
 * One blob rather than a key per field so adding a setting never needs a
 * migration — [AppSettings] decodes field-by-field with defaults. It also keeps
 * the stored shape identical to the iOS build's `UserDefaults` entry.
 */
@Stable
class SettingsStore(
    private val preferences: SharedPreferences?,
    private val storageKey: String = STORAGE_KEY,
) {

    var settings: AppSettings by mutableStateOf(load(preferences, storageKey))

    /**
     * Every write goes through here so persistence can never be forgotten at a
     * call site — the SwiftUI original got that from a `didSet` observer.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        if (next == settings) return
        settings = next
        persist()
    }

    /** Resolved copy of the string table for the currently selected language. */
    val strings: Strings get() = Strings(settings.language)

    fun resetToDefaults() {
        update { AppSettings() }
    }

    private fun persist() {
        val prefs = preferences ?: return
        val encoded = runCatching { json.encodeToString(AppSettings.serializer(), settings) }
            .getOrNull() ?: return
        prefs.edit().putString(storageKey, encoded).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "settings"
        const val STORAGE_KEY = "settings.v1"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
            explicitNulls = false
        }

        /** In-memory only. Used by tests and Compose previews. */
        fun inMemory(settings: AppSettings = AppSettings()): SettingsStore {
            val store = SettingsStore(preferences = null)
            store.settings = settings
            return store
        }

        private fun load(preferences: SharedPreferences?, storageKey: String): AppSettings {
            val raw = preferences?.getString(storageKey, null) ?: return AppSettings()
            return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
                .getOrDefault(AppSettings())
        }
    }
}
