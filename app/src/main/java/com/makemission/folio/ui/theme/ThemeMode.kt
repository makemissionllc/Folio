package com.makemission.folio.ui.theme

/**
 * Theme mode for Folio — controls Light / Dark / Auto (follows system).
 *
 * Stored via DataStore `theme_mode` string key. AUTO is the default so
 * fresh installs follow the device's system dark/light setting.
 *
 * When dark mode is active (whether via AUTO+system dark or manual DARK),
 * the dark palette defaults to Cool Slate unless the user has picked a
 * different palette via [FolioPalette] picker — that coordination lives in
 * [com.makemission.folio.data.settings.SettingsRepository] (SLATE default)
 * and [FolioTheme] (palette param), not here.
 */
enum class ThemeMode(
    val displayName: String,
    val description: String,
) {
    LIGHT(
        displayName = "Light",
        description = "Always light"
    ),
    DARK(
        displayName = "Dark",
        description = "Always dark"
    ),
    AUTO(
        displayName = "Auto",
        description = "Follows system"
    );

    companion object {
        fun fromKey(key: String): ThemeMode =
            entries.find { it.name == key } ?: AUTO
    }
}
