package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stylus highlight (§4 + §5 LCS anchors) — persisted per book.
 *
 * Extends the existing schema (§6) rather than replacing it.
 * `pointsData` stores the normalized ink path as "x1,y1,x2,y2,..." where
 * x/y are 0..1 relative to the reading viewport. This keeps highlights
 * resolution-independent and lets the Room row capture which book, which
 * chapter, and the ink position. For text-attached highlights, pointsData
 * is kept for stylus organic appearance but the primary anchor is now
 * paragraphIndex + startOffset/endOffset.
 *
 * `pressuresData` / `tiltsData` enable organic stroke width (§4): pressure
 * (0..1) and tilt (radians 0..PI/2) captured per point via MotionEvent and
 * used with Multiply true-ink rendering. Empty strings mean fixed-width
 * (legacy rows). Lasso extraction is handled separately and not stored as
 * a highlight.
 *
 * `anchorText` (§5 LCS) stores the surrounding paragraph snippet near the
 * highlight (e.g. 80 chars) as a contextual anchor. On reopen/reimport,
 * an LCS scan relocates the highlight to the closest matching paragraph
 * if the EPUB changed, otherwise it stays orphaned.
 *
 * Text-attached fields (added in v11): `paragraphIndex`, `startOffset`,
 * `endOffset` store the precise character range within the paragraph as the
 * primary anchor. This makes highlights reflow-safe (font size / margin /
 * device changes) and precisely aligned to glyphs via
 * TextLayoutResult.getBoundingBox(). Legacy rows have -1 for these fields
 * and fall back to pointsData/LCS rendering.
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
    /** Surrounding text snippet (e.g. 80 chars) for LCS anchoring (§5). */
    val anchorText: String = "",
    /** True if anchor could not be relocated after file change — left orphaned. */
    val isOrphaned: Boolean = false,
    /** ARGB int, e.g. FolioAmber (#F7B538). Now supports multiple user-selectable colors. */
    val color: Int,
    /** Highlight style — FILL (Multiply fill, default) or UNDERLINE, persisted per highlight. */
    val style: String = "FILL",
    /** Text-attached: paragraph index within chapter, -1 = legacy stroke-only. */
    val paragraphIndex: Int = -1,
    /** Text-attached: start character offset within paragraph (inclusive), -1 = legacy. */
    val startOffset: Int = -1,
    /** Text-attached: end character offset within paragraph (exclusive), -1 = legacy. */
    val endOffset: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
)
