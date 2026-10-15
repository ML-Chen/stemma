package app.stemma.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Reading themes, matching the four themes of the Wikipedia app. */
enum class ThemeChoice { SYSTEM, LIGHT, SEPIA, DARK, BLACK }

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Text size for entries, in percent (WebView text zoom). */
    val textZoom: Int = 100,
    /** Language section that opens first on every entry, for example "English". */
    val preferredLanguage: String = "English",
    /** When true, every language section on a page starts expanded. */
    val expandAllLanguages: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val textZoom = intPreferencesKey("text_zoom")
        val preferredLanguage = stringPreferencesKey("preferred_language")
        val expandAll = booleanPreferencesKey("expand_all_languages")
    }

    val settings: Flow<Settings> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { p ->
        Settings(
            theme = p[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: ThemeChoice.SYSTEM,
            textZoom = p[Keys.textZoom] ?: 100,
            preferredLanguage = p[Keys.preferredLanguage] ?: "English",
            expandAllLanguages = p[Keys.expandAll] ?: false,
        )
    }

    suspend fun setTheme(theme: ThemeChoice) = safeEdit { it[Keys.theme] = theme.name }

    suspend fun setTextZoom(percent: Int) =
        safeEdit { it[Keys.textZoom] = percent.coerceIn(MIN_ZOOM, MAX_ZOOM) }

    suspend fun setPreferredLanguage(language: String) =
        safeEdit { it[Keys.preferredLanguage] = language }

    suspend fun setExpandAllLanguages(expand: Boolean) = safeEdit { it[Keys.expandAll] = expand }

    /** Saves a change; if the disk is full the change is simply not kept. */
    private suspend fun safeEdit(transform: (MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit { transform(it) }
        } catch (_: IOException) {
        }
    }

    companion object {
        const val MIN_ZOOM = 80
        const val MAX_ZOOM = 180

        /** Languages offered in Settings. Any language heading used on Wiktionary would work. */
        val LANGUAGES = listOf(
            "English", "Spanish", "French", "German", "Italian", "Portuguese", "Dutch", "Swedish",
            "Norwegian Bokmål", "Danish", "Finnish", "Polish", "Czech", "Russian", "Ukrainian", "Greek",
            "Ancient Greek", "Latin", "Turkish", "Arabic", "Hebrew", "Persian", "Hindi", "Indonesian",
            "Vietnamese", "Japanese", "Korean", "Chinese", "Esperanto",
        )
    }
}
