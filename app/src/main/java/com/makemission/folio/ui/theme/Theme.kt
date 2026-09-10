package com.makemission.folio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Folio app theme — now supports multiple dark palettes alongside the
 * default deep-green Folio palette. The selected [palette] persists via
 * DataStore (see [FolioPalette]) and applies app-wide (Library/Reading/
 * Settings/Insights/etc.), not just one screen. Light scheme stays warm
 * paper; dark scheme is chosen via [palette].
 *
 * Structure follows the reference app (`book-story-master`,
 * `ui/theme/Theme.kt`) but Folio's palettes are editorial (green/OLED/
 * sepia/slate) and keep burgundy/amber accents coherent, unlike the
 * reference's many arbitrary hues.
 *
 * Adaptive contrast (Colorimetric) coordinates rather than conflicts:
 * it lerps whichever palette's background is active (see
 * AdaptiveContrastEngine.adaptiveBackground), not overriding it.
 *
 * Dynamic color is intentionally off so the Folio palette always wins.
 *
 * @param darkTheme defaults to the system setting; the dark scheme is the
 * Folio library default per the spec.
 * @param palette which dark palette to use when [darkTheme] is true; DEFAULT
 * is the classic deep green and remains the default.
 */
@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: FolioPalette = FolioPalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) folioDarkSchemeFor(palette) else FolioLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(),
        typography = FolioTypography,
        content = content,
    )
}
