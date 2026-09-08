package com.makemission.folio.data.db.entity

import androidx.room.Entity
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
    val lastReadMillis: Long = System.currentTimeMillis(),
)
