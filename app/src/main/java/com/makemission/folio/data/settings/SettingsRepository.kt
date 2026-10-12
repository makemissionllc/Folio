package com.makemission.folio.data.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.makemission.folio.ui.reader.FolioFontSize
import com.makemission.folio.ui.reader.FolioHighlightColor
import com.makemission.folio.ui.reader.FolioHighlightStyle
import com.makemission.folio.ui.reader.FolioLineSpacing
import com.makemission.folio.ui.reader.FolioMargin
import com.makemission.folio.ui.reader.FolioReadingFont
import com.makemission.folio.ui.reader.ReadingNavigationMode
import com.makemission.folio.ui.reader.ReaderPageStyle
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
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_DARK_PALETTE = stringPreferencesKey("dark_palette")
        private val KEY_TIME_TINT_ENABLED = booleanPreferencesKey("time_tint_enabled")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_READING_NAV_MODE = stringPreferencesKey("reading_nav_mode")
        private val KEY_BOOKS_FOLDER_URI = stringPreferencesKey("books_folder_uri")
        private val KEY_BIONIC_ENABLED = booleanPreferencesKey("bionic_enabled")
        private val KEY_ADAPTIVE_CONTRAST_ENABLED = booleanPreferencesKey("adaptive_contrast_enabled")
        private val KEY_READING_FONT = stringPreferencesKey("reading_font")
        private val KEY_READING_FONT_SIZE = stringPreferencesKey("reading_font_size")
        private val KEY_READING_LINE_SPACING = stringPreferencesKey("reading_line_spacing")
        private val KEY_READING_MARGIN = stringPreferencesKey("reading_margin")
        private val KEY_READER_PAGE_STYLE = stringPreferencesKey("reader_page_style")
        private val KEY_READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        private val KEY_HIGHLIGHT_COLOR = stringPreferencesKey("highlight_color")
        private val KEY_HIGHLIGHT_STYLE = stringPreferencesKey("highlight_style")
        private val KEY_TABLET_COMPACT_LIBRARY_GRID = booleanPreferencesKey("tablet_compact_library_grid")
        private val KEY_LIBRARY_SHELF = stringPreferencesKey("library_shelf")
        private val KEY_LIBRARY_SORT = stringPreferencesKey("library_sort")

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

    /** Keep the display awake only while the reader is visible. */
    val keepScreenAwake: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_KEEP_SCREEN_AWAKE] ?: false
        }

    suspend fun setKeepScreenAwake(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_AWAKE] = value
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

    val bionicEnabled: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_BIONIC_ENABLED] ?: false
        }

    suspend fun setBionicEnabled(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_BIONIC_ENABLED] = value
        }
    }

    val adaptiveContrastEnabled: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs ->
            prefs[KEY_ADAPTIVE_CONTRAST_ENABLED] ?: false
        }

    suspend fun setAdaptiveContrastEnabled(value: Boolean) {
        context.folioSettingsDataStore.edit { prefs ->
            prefs[KEY_ADAPTIVE_CONTRAST_ENABLED] = value
        }
    }

    // — Reading typography (Kindle/Apple Books–comparable, persisted) —
    val readingFont: Flow<FolioReadingFont> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioReadingFont.fromKey(prefs[KEY_READING_FONT])
        }
    suspend fun setReadingFont(font: FolioReadingFont) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READING_FONT] = font.name }
    }

    val readingFontSize: Flow<FolioFontSize> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioFontSize.fromKey(prefs[KEY_READING_FONT_SIZE])
        }
    suspend fun setReadingFontSize(size: FolioFontSize) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READING_FONT_SIZE] = size.name }
    }

    val readingLineSpacing: Flow<FolioLineSpacing> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioLineSpacing.fromKey(prefs[KEY_READING_LINE_SPACING])
        }
    suspend fun setReadingLineSpacing(spacing: FolioLineSpacing) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READING_LINE_SPACING] = spacing.name }
    }

    val readingMargin: Flow<FolioMargin> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioMargin.fromKey(prefs[KEY_READING_MARGIN])
        }
    suspend fun setReadingMargin(margin: FolioMargin) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READING_MARGIN] = margin.name }
    }

    val readerPageStyle: Flow<ReaderPageStyle> =
        context.folioSettingsDataStore.data.map { prefs -> ReaderPageStyle.fromKey(prefs[KEY_READER_PAGE_STYLE]) }

    suspend fun setReaderPageStyle(style: ReaderPageStyle) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READER_PAGE_STYLE] = style.name }
    }

    /** -1 delegates to the system; otherwise this is a reader-only window override. */
    val readerBrightness: Flow<Float> =
        context.folioSettingsDataStore.data.map { prefs -> prefs[KEY_READER_BRIGHTNESS] ?: -1f }

    suspend fun setReaderBrightness(value: Float) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_READER_BRIGHTNESS] = value.coerceIn(0.05f, 1f) }
    }

    val highlightColor: Flow<FolioHighlightColor> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioHighlightColor.fromKey(prefs[KEY_HIGHLIGHT_COLOR])
        }
    suspend fun setHighlightColor(color: FolioHighlightColor) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_HIGHLIGHT_COLOR] = color.name }
    }

    val highlightStyle: Flow<FolioHighlightStyle> =
        context.folioSettingsDataStore.data.map { prefs ->
            FolioHighlightStyle.fromKey(prefs[KEY_HIGHLIGHT_STYLE])
        }
    suspend fun setHighlightStyle(style: FolioHighlightStyle) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_HIGHLIGHT_STYLE] = style.name }
    }

    /** Tablet library density preference; roomy covers remain the default. */
    val tabletCompactLibraryGrid: Flow<Boolean> =
        context.folioSettingsDataStore.data.map { prefs -> prefs[KEY_TABLET_COMPACT_LIBRARY_GRID] ?: false }

    suspend fun setTabletCompactLibraryGrid(compact: Boolean) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_TABLET_COMPACT_LIBRARY_GRID] = compact }
    }

    /** Keeps the last library shelf selected across app launches. */
    val libraryShelf: Flow<String> =
        context.folioSettingsDataStore.data.map { prefs -> prefs[KEY_LIBRARY_SHELF] ?: "ALL" }

    suspend fun setLibraryShelf(shelf: String) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_LIBRARY_SHELF] = shelf }
    }

    /** Keeps the last library sort selected across app launches. */
    val librarySort: Flow<String> =
        context.folioSettingsDataStore.data.map { prefs -> prefs[KEY_LIBRARY_SORT] ?: "RECENT" }

    suspend fun setLibrarySort(sort: String) {
        context.folioSettingsDataStore.edit { prefs -> prefs[KEY_LIBRARY_SORT] = sort }
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
