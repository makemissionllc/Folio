package com.makemission.folio.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Dark palette options for Folio — builds on top of the existing deep-green
 * Folio palette (DEFAULT) without removing it. Each palette stays editorial
 * and pairs sensibly with the existing burgundy/amber accents.
 *
 * Structural inspiration from book-story-master's Theme enum + colorScheme() mapping only.
 */
enum class FolioPalette(
    val displayName: String,
    val description: String,
) {
    DEFAULT(
        displayName = "Folio Green",
        description = "Deep green #004F39 · editorial default"
    ),
    OLED(
        displayName = "True Black",
        description = "Pure black · OLED-friendly, max contrast"
    ),
    SEPIA(
        displayName = "Warm Sepia",
        description = "Dark brown · warm paper, low light"
    ),
    SLATE(
        displayName = "Cool Slate",
        description = "Blue-slate · calm, cool reading"
    );

    companion object {
        fun fromKey(key: String): FolioPalette =
            entries.find { it.name == key } ?: SLATE
    }
}

// True Black / OLED-friendly — pure black background, charcoal surfaces.
// Keeps Folio amber (#F7B538) and burgundy (#780116) accents unchanged for coherence.
private val OledBackground = Color(0xFF080808)
private val OledSurface = Color(0xFF0A0A0A)
private val OledSurfaceVariant = Color(0xFF1E1E1E)
private val OledContainerLowest = Color(0xFF000000)
private val OledContainerLow = Color(0xFF0A0A0A)
private val OledContainer = Color(0xFF1E1E1E)
private val OledContainerHigh = Color(0xFF2A2A2A)
private val OledContainerHighest = Color(0xFF333333)

val FolioOledColorScheme: ColorScheme = darkColorScheme(
    primary = FolioAmber,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = FolioBurgundy,
    onPrimaryContainer = FolioOffWhite,
    secondary = FolioAmber,
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = FolioBurgundyContainer,
    onSecondaryContainer = FolioOffWhite,
    tertiary = FolioAmber,
    onTertiary = Color(0xFF1A1A1A),
    background = OledBackground,
    onBackground = FolioOffWhite,
    surface = OledSurface,
    onSurface = FolioOffWhite,
    surfaceVariant = OledSurfaceVariant,
    onSurfaceVariant = FolioOffWhite,
    surfaceContainerLowest = OledContainerLowest,
    surfaceContainerLow = OledContainerLow,
    surfaceContainer = OledContainer,
    surfaceContainerHigh = OledContainerHigh,
    surfaceContainerHighest = OledContainerHighest,
    outline = FolioAmber.copy(alpha = 0.45f),
    outlineVariant = FolioOffWhite.copy(alpha = 0.20f),
    scrim = Color.Black,
    inverseSurface = FolioOffWhite,
    inverseOnSurface = OledBackground,
    inversePrimary = FolioBurgundy,
)

// Warm Sepia / Brown — dark umber background, warm clay surfaces.
private val SepiaBackground = Color(0xFF1F140E)
private val SepiaSurface = Color(0xFF251A12)
private val SepiaSurfaceVariant = Color(0xFF3D2F20)
private val SepiaContainerLowest = Color(0xFF120C08)
private val SepiaContainerLow = Color(0xFF1F140E)
private val SepiaContainer = Color(0xFF3D2F20)
private val SepiaContainerHigh = Color(0xFF4D3A28)
private val SepiaContainerHighest = Color(0xFF5C4632)

val FolioSepiaColorScheme: ColorScheme = darkColorScheme(
    primary = FolioAmber,
    onPrimary = Color(0xFF1F140E),
    primaryContainer = FolioBurgundy,
    onPrimaryContainer = FolioOffWhite,
    secondary = FolioAmber,
    onSecondary = Color(0xFF1F140E),
    secondaryContainer = FolioBurgundyContainer,
    onSecondaryContainer = FolioOffWhite,
    tertiary = FolioAmber,
    onTertiary = Color(0xFF1F140E),
    background = SepiaBackground,
    onBackground = Color(0xFFFFF1D6),
    surface = SepiaSurface,
    onSurface = Color(0xFFFFF1D6),
    surfaceVariant = SepiaSurfaceVariant,
    onSurfaceVariant = Color(0xFFFFF1D6),
    surfaceContainerLowest = SepiaContainerLowest,
    surfaceContainerLow = SepiaContainerLow,
    surfaceContainer = SepiaContainer,
    surfaceContainerHigh = SepiaContainerHigh,
    surfaceContainerHighest = SepiaContainerHighest,
    outline = FolioAmber.copy(alpha = 0.45f),
    outlineVariant = Color(0xFFFFF1D6).copy(alpha = 0.22f),
    scrim = Color.Black,
    inverseSurface = Color(0xFFFFF1D6),
    inverseOnSurface = SepiaBackground,
    inversePrimary = FolioBurgundy,
)

// Cool Slate — dark blue-slate background, desaturated slate surfaces.
private val SlateBackground = Color(0xFF111114)
private val SlateSurface = Color(0xFF1B1B1F)
private val SlateSurfaceVariant = Color(0xFF2A2A30)
private val SlateContainerLowest = Color(0xFF0D0D10)
private val SlateContainerLow = Color(0xFF18181C)
private val SlateContainer = Color(0xFF252529)
private val SlateContainerHigh = Color(0xFF303036)
private val SlateContainerHighest = Color(0xFF3B3B42)

val FolioSlateColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF071321),
    primaryContainer = Color(0xFF174579),
    onPrimaryContainer = FolioOffWhite,
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF071321),
    secondaryContainer = Color(0xFF174579),
    onSecondaryContainer = FolioOffWhite,
    tertiary = Color(0xFFFFD980),
    onTertiary = Color(0xFF0F1A20),
    background = SlateBackground,
    onBackground = FolioOffWhite,
    surface = SlateSurface,
    onSurface = FolioOffWhite,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = FolioOffWhite,
    surfaceContainerLowest = SlateContainerLowest,
    surfaceContainerLow = SlateContainerLow,
    surfaceContainer = SlateContainer,
    surfaceContainerHigh = SlateContainerHigh,
    surfaceContainerHighest = SlateContainerHighest,
    outline = Color(0xFF60A5FA).copy(alpha = 0.45f),
    outlineVariant = FolioOffWhite.copy(alpha = 0.20f),
    scrim = Color.Black,
    inverseSurface = FolioOffWhite,
    inverseOnSurface = SlateBackground,
    inversePrimary = FolioBlue,
)

/** Resolve dark ColorScheme for [palette]; DEFAULT reuses existing [FolioDarkColorScheme]. */
fun folioDarkSchemeFor(palette: FolioPalette): ColorScheme = when (palette) {
    FolioPalette.DEFAULT -> FolioDarkColorScheme
    FolioPalette.OLED -> FolioOledColorScheme
    FolioPalette.SEPIA -> FolioSepiaColorScheme
    FolioPalette.SLATE -> FolioSlateColorScheme
}
