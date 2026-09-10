package com.makemission.folio.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.makemission.folio.ui.theme.FolioPalette
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
            FolioPalette.fromKey(prefs[KEY_DARK_PALETTE] ?: FolioPalette.DEFAULT.name)
        }

    suspend fun setDarkPalette(palette: FolioPalette) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_DARK_PALETTE] = palette.name
        }
    }
}
