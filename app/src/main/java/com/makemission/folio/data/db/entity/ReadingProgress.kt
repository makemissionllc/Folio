package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Minimal progress tracking per §6 — Room stores page/position for each book.
 * Annotations and vocabulary metrics land later.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0,
    /** Approximate whole-book position, 0..1, captured while reading. */
    @ColumnInfo(defaultValue = "0") val progressFraction: Float = 0f,
    val lastReadMillis: Long = System.currentTimeMillis(),
)
