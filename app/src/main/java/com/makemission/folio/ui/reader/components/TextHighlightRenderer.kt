package com.makemission.folio.ui.reader.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import com.makemission.folio.data.db.entity.Highlight

/**
 * Helper for text-attached highlight rendering via TextLayoutResult.getBoundingBox().
 *
 * Draws precise rectangles behind/around glyphs (handling multi-line selections)
 * instead of freehand canvas strokes. Used for finger highlights (primary) and
 * stylus highlights that have been mapped to a text range (stored alongside stroke data).
 *
 * Legacy highlights with paragraphIndex == -1 are rendered via HighlightOverlay
 * freeform path (not here) — this renderer only handles text-attached.
 */
object TextHighlightRenderer {

    /**
     * Compute merged line rects for a single highlight range within a paragraph.
     * Returns list of rects, one per wrapped line that the range spans.
     */
    fun mergedLineRects(
        layout: TextLayoutResult,
        paragraph: String,
        start: Int,
        end: Int,
    ): List<Rect> {
        val s = start.coerceIn(0, paragraph.length)
        val e = end.coerceIn(0, paragraph.length)
        if (s >= e) return emptyList()
        // Collect bounding boxes per character offset
        val boxes = mutableListOf<Rect>()
        for (offset in s until e) {
            val box = try {
                layout.getBoundingBox(offset)
            } catch (_: Exception) {
                continue
            }
            // getBoundingBox may return 0-size rect for line breaks / invalid offsets
            if (box.width <= 0.5f || box.height <= 0.5f) continue
            boxes.add(box)
        }
        if (boxes.isEmpty()) return emptyList()
        // Group boxes by line (same top within 1px tolerance)
        // Word wrapping creates boxes with similar top/bottom per line.
        val grouped = mutableListOf<MutableList<Rect>>()
        for (box in boxes.sortedBy { it.top }) {
            val group = grouped.find { g ->
                val top = g.first().top
                kotlin.math.abs(top - box.top) < 1.5f
            }
            if (group != null) group.add(box) else grouped.add(mutableListOf(box))
        }
        // Merge each line group into a single rect spanning from min left to max right
        return grouped.mapNotNull { group ->
            if (group.isEmpty()) return@mapNotNull null
            val left = group.minOf { it.left }
            val right = group.maxOf { it.right }
            val top = group.minOf { it.top }
            val bottom = group.maxOf { it.bottom }
            if (right - left < 1f || bottom - top < 1f) null else Rect(left, top, right, bottom)
        }
    }

    /**
     * Filter highlights that are text-attached and belong to given chapter/paragraph.
     */
    fun highlightsForParagraph(
        highlights: List<Highlight>,
        chapterIndex: Int,
        paragraphIndex: Int,
    ): List<Highlight> {
        return highlights.filter {
            !it.isOrphaned &&
                it.chapterIndex == chapterIndex &&
                it.paragraphIndex == paragraphIndex &&
                it.startOffset >= 0 && it.endOffset > it.startOffset
        }
    }
}
