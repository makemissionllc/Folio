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
// Neutral, system-like surfaces keep the reading brand present without
// tinting every screen cream or green.
val FolioPaper = Color(0xFFF5F5F7)
val FolioPaperContainer = Color(0xFFE9E9ED)
val FolioInk = Color(0xFF1D1D1F)
val FolioOffWhite = Color(0xFFFFF8E7)
val FolioBlue = Color(0xFF0969DA)
val FolioReadingPaper = Color(0xFFFBF6EA)

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
    primary = FolioBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBFF),
    onPrimaryContainer = Color(0xFF06468E),
    secondary = FolioBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEBFF),
    onSecondaryContainer = Color(0xFF06468E),
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
    surfaceContainerHigh = Color(0xFFE1E1E6),
    surfaceContainerHighest = Color(0xFFD6D6DC),
    outline = Color(0xFF8E8E93).copy(alpha = 0.42f),
    outlineVariant = Color(0xFF8E8E93).copy(alpha = 0.18f),
    scrim = Color.Black,
    inverseSurface = FolioDeepGreen,
    inverseOnSurface = FolioOffWhite,
    inversePrimary = FolioAmber,
)
