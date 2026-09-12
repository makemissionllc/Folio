package com.makemission.folio.data.model

import androidx.compose.ui.graphics.Color
import com.makemission.folio.ui.theme.FolioAmber
import com.makemission.folio.ui.theme.FolioBurgundy
import com.makemission.folio.ui.theme.FolioBurgundyContainer
import com.makemission.folio.ui.theme.FolioDeepGreen
import com.makemission.folio.ui.theme.FolioDeepGreenContainer

/**
 * Minimal book model for the library grid.
 *
 * Storage (Room) and cover-image loading land in a later step; for the
 * editorial library UI we keep the cover as a flat palette color so the
 * grid renders without network or asset dependencies.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverColor: Color,
    /** Present for imported books — private file path (SAF copy). */
    val filePath: String? = null,
    /** Present when EpubParser extracted a cover image. */
    val coverImagePath: String? = null,
)

/** Single built-in sample — a short "How to use Folio" guide/manual (replaces 8 placeholder titles). */
fun curatedSampleBooks(): List<Book> = listOf(
    Book("folio-guide", "How to use Folio", "Folio", FolioDeepGreen),
)

/** Palette fallback for books that don't carry an explicit cover color. */
val FolioCoverPalette: List<Color> = listOf(
    FolioBurgundy,
    FolioDeepGreen,
    FolioBurgundyContainer,
    FolioDeepGreenContainer,
    FolioAmber,
    Color(0xFF7A3B2E),
)
