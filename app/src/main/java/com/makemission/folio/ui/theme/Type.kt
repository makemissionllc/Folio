package com.makemission.folio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.makemission.folio.R

/**
 * Folio typography — Inter for the Android interface, reader-selected type for book text.
 *
 * Inter is bundled under SIL OFL 1.1 for a consistent San Francisco-inspired
 * interface across Android devices. App chrome uses its real weight files to
 * differentiate hierarchy; book text remains controlled by the reader's font setting.
 *
 * Reading typography (actual book text) is overridden in ReadingScreen and the
 * pagination engines from the reader's persisted font selection.
 */
private val FolioUiFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black),
)

private val FolioUiHeavy = TextStyle(
    fontFamily = FolioUiFamily,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.5).sp,
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
    bodyLarge = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FolioUiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    // Reader pages override the interface font with the user's selected book typeface.
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
