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
 * Folio app theme.
 *
 * Structure follows the reference app (`book-story-master`,
 * `ui/theme/Theme.kt`): a single `@Composable` that resolves a
 * Material3 [colorScheme] and installs it via [MaterialTheme].
 * Unlike the reference app's many color themes, Folio ships one
 * brand scheme — [FolioDarkColorScheme] / [FolioLightColorScheme].
 *
 * Dynamic color is intentionally off so the Folio palette
 * (deep green / burgundy / amber) always wins over wallpaper colors.
 *
 * @param darkTheme defaults to the system setting; the dark scheme is the
 * Folio library default per the spec.
 */
@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FolioDarkColorScheme else FolioLightColorScheme

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
