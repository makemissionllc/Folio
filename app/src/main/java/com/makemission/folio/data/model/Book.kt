package com.makemission.folio.data.model

import androidx.compose.ui.graphics.Color
import com.makemission.folio.ui.theme.FolioAmber
import com.makemission.folio.ui.theme.FolioBurgundy
import com.makemission.folio.ui.theme.FolioBurgundyContainer
import com.makemission.folio.ui.theme.FolioDeepGreen
import com.makemission.folio.ui.theme.FolioDeepGreenContainer

enum class ShelfStatus(val label: String) {
    AUTO("New"),
    WANT("Want to Read"),
    FINISHED("Finished");

    companion object {
        fun fromStored(value: String): ShelfStatus = entries.firstOrNull { it.name == value } ?: AUTO
    }
}

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
    val shelfStatus: ShelfStatus = ShelfStatus.AUTO,
    val progressFraction: Float = 0f,
    val lastReadMillis: Long = 0L,
)

/** Single built-in sample — a short "How to use Folio" guide/manual (replaces 8 placeholder titles). */
fun curatedSampleBooks(): List<Book> = listOf(
    Book("folio-guide", "How to use Folio", "Folio", FolioDeepGreen),
)

/** Palette fallback for books that don't carry an explicit cover color. */
val FolioCoverPalette: List<Color> = listOf(
    Color(0xFF24527E),
    Color(0xFF923641),
    Color(0xFF316451),
    Color(0xFFC18C08),
    Color(0xFF51447A),
    Color(0xFF303032),
    Color(0xFFBE5934),
)
