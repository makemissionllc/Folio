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
)

/** Curated seed — shows the grid with editorial variety until real storage exists. */
fun curatedSampleBooks(): List<Book> = listOf(
    Book("1", "The Great Gatsby", "F. Scott Fitzgerald", FolioBurgundy),
    Book("2", "Moby-Dick", "Herman Melville", FolioDeepGreen),
    Book("3", "Pride and Prejudice", "Jane Austen", FolioBurgundyContainer),
    Book("4", "Invisible Cities", "Italo Calvino", FolioDeepGreenContainer),
    Book("5", "The Odysseys", "Homer", Color(0xFF7A3B2E)),
    Book("6", "On Writing Well", "William Zinsser", Color(0xFF9A6B0A)),
    Book("7", "Dune", "Frank Herbert", FolioDeepGreen),
    Book("8", "Beloved", "Toni Morrison", FolioBurgundy),
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
