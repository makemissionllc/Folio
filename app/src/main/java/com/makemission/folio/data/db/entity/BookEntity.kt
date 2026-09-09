package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted book metadata for imported EPUBs (§6 Technical Foundation).
 * Extends the schema without replacing it — stored alongside progress/highlights.
 * [fileHash] stores SHA-256 hex of file content for duplicate detection during
 * device scanning (match by file content). [importedFromPath] records the
 * original device path/name for filename/path tracking fallback.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    /** Absolute path in app-private storage (copied via SAF or auto-scan). */
    val filePath: String,
    /** Absolute path to extracted cover image, if any. */
    val coverImagePath: String?,
    val addedAt: Long = System.currentTimeMillis(),
    /** SHA-256 hex of EPUB file content — for scan deduplication (null for legacy rows). */
    val fileHash: String? = null,
    /** Original display name or device path at import time — for filename/path dedup fallback. */
    val importedFromPath: String? = null,
)
