package com.makemission.folio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Folio typography pairing (see Folio_Project.md §2).
 *
 * - Headers: bold, heavy sans-serif. Placeholder for Druk Wide /
 *   Helvetica Neue Bold until licensed fonts are bundled (see
 *   TODO below — drop font files into `res/font` and wire a
 *   [FontFamily] here).
 * - Body: classic serif for immersive, high-legibility reading.
 */
private val FolioHeaderFamily = FontFamily.SansSerif
private val FolioBodyFamily = FontFamily.Serif

private val FolioHeaderStyle = TextStyle(
    fontFamily = FolioHeaderFamily,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.5).sp,
)

private val FolioBodyStyle = TextStyle(
    fontFamily = FolioBodyFamily,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.2.sp,
)

val FolioTypography = Typography(
    displayLarge = FolioHeaderStyle.copy(fontSize = 57.sp, lineHeight = 60.sp),
    displayMedium = FolioHeaderStyle.copy(fontSize = 45.sp, lineHeight = 48.sp),
    displaySmall = FolioHeaderStyle.copy(fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = FolioHeaderStyle.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = FolioHeaderStyle.copy(fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = FolioHeaderStyle.copy(fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = FolioHeaderStyle.copy(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = FolioHeaderStyle.copy(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = FolioHeaderStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = FolioBodyStyle.copy(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = FolioBodyStyle.copy(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = FolioBodyStyle.copy(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = FolioHeaderStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = FolioHeaderStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = FolioHeaderStyle.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
