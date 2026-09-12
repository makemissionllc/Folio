package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bookmark — distinct from Highlight (§4).
 * While Highlight selects text (stylus ink + LCS anchor), a bookmark just
 * marks a reading spot (chapter/paragraph position). No text selection, no
 * Multiply rendering — just a quick jump target.
 *
 * Built on top of existing Highlight/Room setup, not a rewrite. Stored
 * alongside ReadingProgress/Highlight/VocabularyCard in FolioDatabase.
 *
 * position = paragraphIndex within [chapterIndex] (spec's "position").
 * [previewText] is a short snippet (first 120 chars of the paragraph) for
 * the list UI — derived at bookmark time, no extra query.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId", "chapterIndex", "paragraphIndex"], unique = true)],
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int = 0,
    /** Spec's "position" — paragraph index inside the chapter. */
    val paragraphIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Short preview of the bookmarked paragraph (120 chars) for the bottom sheet. */
    val previewText: String = "",
)
