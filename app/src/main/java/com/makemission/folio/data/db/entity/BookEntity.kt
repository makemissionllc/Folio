package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted book metadata for imported EPUBs (§6 Technical Foundation).
 * Extends the schema without replacing it — stored alongside progress/highlights.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    /** Absolute path in app-private storage (copied via SAF). */
    val filePath: String,
    /** Absolute path to extracted cover image, if any. */
    val coverImagePath: String?,
    val addedAt: Long = System.currentTimeMillis(),
)
