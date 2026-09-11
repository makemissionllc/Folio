package com.makemission.folio.data.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.makemission.folio.ui.reader.ReadingNavigationMode
import com.makemission.folio.ui.theme.FolioPalette
import com.makemission.folio.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.folioSettingsDataStore by preferencesDataStore(name = "folio_settings")

/**
 * Settings persistence via DataStore (survives app restarts, not just in-memory).
 * Single toggle for now: "Always show progress bar" — room to grow with clear sections.
 * Builds on Folio's on-device local storage philosophy (no network).
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_ALWAYS_SHOW_PROGRESS = booleanPreferencesKey("always_show_progress_bar")
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        private val KEY_AUTO_SCAN_ENABLED = booleanPreferencesKey("auto_scan_enabled")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_DARK_PALETTE = stringPreferencesKey("dark_palette")
        private val KEY_TIME_TINT_ENABLED = booleanPreferencesKey("time_tint_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_READING_NAV_MODE = stringPreferencesKey("reading_nav_mode")
        private val KEY_BOOKS_FOLDER_URI = stringPreferencesKey("books_folder_uri")

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    val alwaysShowProgressBar: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_ALWAYS_SHOW_PROGRESS] ?: false
        }

    suspend fun setAlwaysShowProgressBar(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_ALWAYS_SHOW_PROGRESS] = value
        }
    }

    val hasSeenOnboarding: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
        }

    suspend fun setHasSeenOnboarding(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = value
        }
    }

    val autoScanEnabled: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_AUTO_SCAN_ENABLED] ?: true
        }

    suspend fun setAutoScanEnabled(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_AUTO_SCAN_ENABLED] = value
        }
    }

    val hapticsEnabled: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_HAPTICS_ENABLED] ?: true
        }

    suspend fun setHapticsEnabled(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = value
        }
    }

    val darkPalette: Flow<FolioPalette> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioPalette.fromKey(prefs[KEY_DARK_PALETTE] ?: FolioPalette.SLATE.name)
        }

    suspend fun setDarkPalette(palette: FolioPalette) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_DARK_PALETTE] = palette.name
        }
    }

    val timeTintEnabled: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_TIME_TINT_ENABLED] ?: false
        }

    suspend fun setTimeTintEnabled(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_TIME_TINT_ENABLED] = value
        }
    }

    val themeMode: Flow<ThemeMode> =
        context.folioSettingsDataStore.data.map { prefs ->
            ThemeMode.fromKey(prefs[KEY_THEME_MODE] ?: ThemeMode.AUTO.name)
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    val readingNavigationMode: Flow<ReadingNavigationMode> =
        context.folioSettingsDataStore.data.map { prefs ->
            ReadingNavigationMode.fromKey(prefs[KEY_READING_NAV_MODE] ?: ReadingNavigationMode.CONTINUOUS.name)
        }

    suspend fun setReadingNavigationMode(mode: ReadingNavigationMode) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_READING_NAV_MODE] = mode.name
        }
    }

    val booksFolderUri: Flow<String?> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_BOOKS_FOLDER_URI]
        }

    suspend fun setBooksFolderUri(uri: String?) {
        context.folioSettingsDataStore.edit { prefs ->
            if (uri != null) {
                prefs[KEY_BOOKS_FOLDER_URI] = uri
            } else {
                prefs.remove(KEY_BOOKS_FOLDER_URI)
            }
        }
    }

    fun getFolderDisplayName(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            if (doc != null && !doc.name.isNullOrBlank()) {
                doc.name
            } else {
                val decoded = Uri.decode(uriString)
                val segment = decoded.substringAfterLast(':').substringAfterLast('/')
                segment.ifBlank { uriString }
            }
        } catch (_: Exception) {
            null
        }
    }
}
