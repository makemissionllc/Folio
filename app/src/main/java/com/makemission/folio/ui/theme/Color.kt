package com.makemission.folio.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Folio brand palette (see Folio_Project.md §2 — Visual Design System).
 *
 * - [FolioDeepGreen] — primary backgrounds: library views and dark mode.
 * - [FolioBurgundy] — active states, category tags, interactive buttons.
 * - [FolioAmber] — accent: default stylus highlight, progress, focal points.
 */
val FolioDeepGreen = Color(0xFF004F39)
val FolioBurgundy = Color(0xFF780116)
val FolioAmber = Color(0xFFF7B538)

// Supporting tones derived from the brand palette.
val FolioDeepGreenDark = Color(0xFF003325)
val FolioDeepGreenContainer = Color(0xFF0B5C45)
val FolioBurgundyContainer = Color(0xFF9A1B26)
val FolioPaper = Color(0xFFFBF6EC)
val FolioPaperContainer = Color(0xFFF1E8D2)
val FolioInk = Color(0xFF1C1B17)
val FolioOffWhite = Color(0xFFFFF8E7)

/**
 * Dark scheme is the Folio default: deep-green surfaces with amber accents.
 * Burgundy marks active/selected states.
 */
val FolioDarkColorScheme = darkColorScheme(
    primary = FolioAmber,
    onPrimary = FolioDeepGreenDark,
    primaryContainer = FolioBurgundy,
    onPrimaryContainer = FolioOffWhite,
    secondary = FolioAmber,
    onSecondary = FolioDeepGreenDark,
    secondaryContainer = FolioBurgundyContainer,
    onSecondaryContainer = FolioOffWhite,
    tertiary = FolioAmber,
    onTertiary = FolioDeepGreenDark,
    background = FolioDeepGreen,
    onBackground = FolioOffWhite,
    surface = FolioDeepGreen,
    onSurface = FolioOffWhite,
    surfaceVariant = FolioDeepGreenContainer,
    onSurfaceVariant = FolioOffWhite,
    surfaceContainerLowest = FolioDeepGreenDark,
    surfaceContainerLow = FolioDeepGreen,
    surfaceContainer = FolioDeepGreenContainer,
    surfaceContainerHigh = Color(0xFF147052),
    surfaceContainerHighest = Color(0xFF1E8060),
    outline = FolioAmber.copy(alpha = 0.5f),
    outlineVariant = FolioOffWhite.copy(alpha = 0.24f),
    scrim = Color.Black,
    inverseSurface = FolioOffWhite,
    inverseOnSurface = FolioDeepGreen,
    inversePrimary = FolioBurgundy,
)

/**
 * Light scheme for daytime/paper reading: warm paper surfaces, deep-green
 * primary, burgundy for active states, amber reserved for highlights.
 */
val FolioLightColorScheme = lightColorScheme(
    primary = FolioDeepGreen,
    onPrimary = Color.White,
    primaryContainer = FolioDeepGreenContainer,
    onPrimaryContainer = Color.White,
    secondary = FolioBurgundy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3D9C8),
    onSecondaryContainer = FolioBurgundy,
    tertiary = Color(0xFF9A6B0A),
    onTertiary = Color.White,
    tertiaryContainer = FolioAmber,
    onTertiaryContainer = FolioDeepGreenDark,
    background = FolioPaper,
    onBackground = FolioInk,
    surface = FolioPaper,
    onSurface = FolioInk,
    surfaceVariant = FolioPaperContainer,
    onSurfaceVariant = FolioInk,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FolioPaper,
    surfaceContainer = FolioPaperContainer,
    surfaceContainerHigh = Color(0xFFE7DCC2),
    surfaceContainerHighest = Color(0xFFDCD0B4),
    outline = FolioDeepGreen.copy(alpha = 0.4f),
    outlineVariant = FolioDeepGreen.copy(alpha = 0.16f),
    scrim = Color.Black,
    inverseSurface = FolioDeepGreen,
    inverseOnSurface = FolioOffWhite,
    inversePrimary = FolioAmber,
)
