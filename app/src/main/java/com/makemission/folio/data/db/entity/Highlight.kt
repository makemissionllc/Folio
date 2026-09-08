package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stylus highlight (§4) — persisted per book.
 *
 * Extends the existing schema (§6) rather than replacing it.
 * `pointsData` stores the normalized ink path as "x1,y1,x2,y2,..." where
 * x/y are 0..1 relative to the reading viewport. This keeps highlights
 * resolution-independent and lets the Room row capture which book, which
 * chapter, and the ink position.
 *
 * `pressuresData` / `tiltsData` enable organic stroke width (§4): pressure
 * (0..1) and tilt (radians 0..PI/2) captured per point via MotionEvent and
 * used with Multiply true-ink rendering. Empty strings mean fixed-width
 * (legacy rows). Lasso extraction is handled separately and not stored as
 * a highlight.
 */
@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int = 0,
    /** Normalized path: "0.12,0.34,0.13,0.35,..." — empty if not freehand. */
    val pointsData: String,
    /** Comma-separated pressures per point (0..1), parallel to pointsData. */
    val pressuresData: String = "",
    /** Comma-separated tilts in radians per point (0..PI/2). */
    val tiltsData: String = "",
    /** ARGB int, e.g. FolioAmber (#F7B538). */
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
