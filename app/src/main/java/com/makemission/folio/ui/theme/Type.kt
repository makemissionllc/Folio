package com.makemission.folio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Folio typography — SF-style system font for all UI chrome, serif for reading.
 *
 * App chrome (library, settings, navigation, labels, buttons, etc.) uses a
 * San Francisco–style system sans (Android's default Roboto via [FontFamily.Default],
 * the closest SF analogue on Android) with WEIGHT to differentiate hierarchy:
 * headlines heavy/black, titles bold/extraBold, labels medium/regular — not
 * different font families. This keeps the interface calm and premium iOS-like.
 *
 * Reading typography (actual book text) stays serif [FolioReadingFamily] for
 * immersive legibility (see Folio_Project.md §2) and is NOT changed here.
 */
private val FolioUiFamily = FontFamily.Default // SF-style: Roboto on Android
private val FolioReadingFamily = FontFamily.Serif

private val FolioUiHeavy = TextStyle(
    fontFamily = FolioUiFamily,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.5).sp,
)

private val FolioReadingStyle = TextStyle(
    fontFamily = FolioReadingFamily,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.2.sp,
)

val FolioTypography = Typography(
    displayLarge = FolioUiHeavy.copy(fontSize = 57.sp, lineHeight = 60.sp),
    displayMedium = FolioUiHeavy.copy(fontSize = 45.sp, lineHeight = 48.sp),
    displaySmall = FolioUiHeavy.copy(fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = FolioUiHeavy.copy(fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = FolioUiHeavy.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp),
    headlineSmall = FolioUiHeavy.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = FolioReadingStyle.copy(fontSize = 17.sp, lineHeight = 27.sp),
    bodyMedium = FolioReadingStyle.copy(fontSize = 15.sp, lineHeight = 24.sp),
    // UI chrome small body — sans regular, not serif (reading keeps serif via bodyLarge/Medium)
    bodySmall = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)
