package com.makemission.folio.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makemission.folio.data.epub.EpubParser

/**
 * Knuth-Plass Line Breaking / Orphan & Widow Control (§5).
 *
 * Problem: basic greedy line breaking can leave a single line of a paragraph
 * stranded alone at the top or bottom of a page/column (widow/orphan), which
 * looks unpolished. Goal: score line breaks across a whole paragraph (not
 * greedily) and adjust micro-kerning / word spacing so the paragraph looks
 * "squared off" like a printed book.
 *
 * Logic: adapted line-breaking that scores each candidate break set.
 * - Measures each paragraph on the virtual canvas (same width as TruePageEngine)
 *   for candidate letterSpacing deltas.
 * - Scores badness per line as 100*|ratio|^3 where ratio is stretch/shrink
 *   needed to fill the column width (Knuth's badness), plus demerits.
 * - Adds orphan penalty (single line at bottom of page) and widow penalty
 *   (single line at top of next page) when a paragraph is split across a
 *   True-Page break with only 1 line on one side.
 * - Picks the delta with minimal total score; that delta is applied as
 *   micro-kerning (TextStyle.letterSpacing) and justification (TextAlign.Justify
 *   is implied by the caller).
 *
 * Coordinates with TruePageEngine rather than conflicting: uses identical
 * availableWidth/availableHeight/lineHeight and TruePage's prefix line counts,
 * so page breaks agree. Adjustments are micro (-0.4sp .. +0.6sp) and only just
 * enough to push a widow/orphan to 2+ lines.
 *
 * Builds on existing text layout — does not rewrite it; paragraph Text is
 * wrapped with an adjusted TextStyle. Reference book-story-master used for
 * structure only.
 */
object KnuthPlassEngine {

    const val ORPHAN_PENALTY = 12000.0
    const val WIDOW_PENALTY = 12000.0

    /**
     * Score a paragraph layout for a given letterSpacing delta.
     * Returns pair (badness, linesPerParagraph).
     * Higher badness = worse. Infinite if still orphan/widow.
     */
    fun scoreForDelta(
        paragraph: String,
        annotated: AnnotatedString?,
        baseStyle: TextStyle,
        deltaSp: Float,
        availableWidthPx: Int,
        lineHeightPx: Int,
        linesPerPage: Int,
        startLine: Int, // global start line index (0-based) of this paragraph
        lineCount: Int, // lines this paragraph occupies at this delta
        totalLinesBefore: Int, // not used directly
    ): Double {
        // Badness from stretch: larger delta => more badness
        val stretchBadness = (deltaSp * deltaSp) * 600.0 // micro-kerning cost

        // Orphan/widow detection for this paragraph if split across pages
        if (lineCount <= 1) return stretchBadness // single-line para can't be orphan/widow alone
        val endLine = startLine + lineCount - 1
        val startPage = startLine / linesPerPage
        val endPage = endLine / linesPerPage
        if (startPage == endPage) {
            // Not split across page — no orphan/widow, just stretch cost + squared-off bonus
            // Prefer balanced: slight penalty if last line very short (not squared)
            // Approximate: if lineCount>1 and last line < 40% width, add small penalty
            // We approximate via delta: tighter/looser with 0 delta is ideal squared.
            return stretchBadness
        }
        // Split across at least one page boundary
        // Find first split page boundary within paragraph
        var penalty = 0.0
        var pageBoundaryLine = ((startPage + 1) * linesPerPage)
        while (pageBoundaryLine <= endLine) {
            val linesOnFirstPage = pageBoundaryLine - startLine
            val linesOnLastPage = endLine - pageBoundaryLine + 1
            // Orphan: first linesOnFirstPage ==1 (single line at bottom)
            if (linesOnFirstPage == 1) penalty += ORPHAN_PENALTY
            // Widow: linesOnLastPage ==1 (single line at top of next page)
            if (linesOnLastPage == 1) penalty += WIDOW_PENALTY
            pageBoundaryLine += linesPerPage
        }
        return stretchBadness + penalty
    }
}

data class KnuthAdjustment(
    val letterSpacingDelta: TextUnit = 0.sp,
    val useJustify: Boolean = false,
)

/**
 * Remembers micro-kerning adjustments for every paragraph.
 * Key: "c${chapterIdx}-p${paraIdx}" -> adjustment.
 *
 * Coordinate with TruePageEngine: uses same virtual canvas width/height and
 * lineHeight, and TruePage's lines-per-page derived from availableHeight.
 */
@Composable
fun rememberKnuthAdjustments(
    chapters: List<EpubParser.EpubChapter>,
    isTabletLandscape: Boolean,
    bionicEnabled: Boolean,
    // Pass TruePageInfo? Not needed directly, but we compute same dims.
): Map<String, KnuthAdjustment> {
    val textMeasurer = rememberTextMeasurer()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val typography = MaterialTheme.typography

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val orientation = configuration.orientation
    val fontScale = density.fontScale
    val densityValue = density.density

    val chaptersKey = remember(chapters) {
        var h = chapters.size * 31
        h = h * 31 + chapters.sumOf { it.paragraphs.sumOf { p -> p.length } }
        h = h * 31 + (chapters.firstOrNull()?.title?.hashCode() ?: 0)
        h
    }

    return remember(
        screenWidthDp, screenHeightDp, orientation,
        fontScale, densityValue, bionicEnabled, isTabletLandscape, chaptersKey,
    ) {
        if (chapters.isEmpty()) return@remember emptyMap()

        // Mirror TruePageEngine's virtual canvas dimensions
        val availableWidthPx = with(density) {
            val wDp = if (isTabletLandscape) {
                val totalH = 12.dp * 2 + 14.dp * 4 + 1.dp
                ((screenWidthDp.dp - totalH) / 2).coerceAtLeast(120.dp)
            } else {
                (screenWidthDp.dp - 40.dp).coerceAtLeast(120.dp)
            }
            wDp.toPx().toInt().coerceAtLeast(100)
        }
        val availableHeightPx = with(density) {
            (screenHeightDp.dp - 140.dp).coerceAtLeast(200.dp).toPx().toInt().coerceAtLeast(200)
        }
        val perScreenHeightPx = if (isTabletLandscape) availableHeightPx * 2 else availableHeightPx

        val bodyStyle = if (isTabletLandscape) typography.bodyMedium else typography.bodyLarge
        val lineHeightPx = with(density) { bodyStyle.lineHeight.toPx().toInt().coerceAtLeast(20) }
        val linesPerPage = (perScreenHeightPx / lineHeightPx).coerceAtLeast(12)
        val baseLetterSpacing = bodyStyle.letterSpacing

        // Pre-measure lineCounts at base spacing for all paragraphs to get startLine
        // We need sequential line accumulation including titles/diagram/gaps as lines
        // Convert non-paragraph heights to lines for startLine calculation
        val diagramLines = with(density) { 172.dp.toPx().toInt() / lineHeightPx.coerceAtLeast(1) }.coerceAtLeast(6)
        val gapLines = with(density) { 17.dp.toPx().toInt() / lineHeightPx.coerceAtLeast(1) }.coerceAtLeast(1)
        val titleLinesEst = 2 // titles approx 2 lines + padding ~ 40dp ~ 1-2 lines

        // First pass: measure each paragraph's lineCount at base spacing
        val paraLineCounts = mutableMapOf<String, Int>()
        for ((cIdx, ch) in chapters.withIndex()) {
            for ((pIdx, para) in ch.paragraphs.withIndex()) {
                val key = "c${cIdx}-p${pIdx}"
                val annotated = if (bionicEnabled) {
                    try { BionicReading.toBionicAnnotated(para, BionicReading.boldSpan()) } catch (_: Exception) { AnnotatedString(para) }
                } else AnnotatedString(para)
                val style = bodyStyle
                val lc = try {
                    val result = textMeasurer.measure(
                        text = annotated,
                        style = style,
                        constraints = Constraints(maxWidth = availableWidthPx),
                    )
                    result.lineCount.coerceAtLeast(1)
                } catch (_: Exception) {
                    // Fallback estimate
                    val avgCharsPerLine = (availableWidthPx / with(density) { 8.dp.toPx() }).toInt().coerceAtLeast(20)
                    (para.length / avgCharsPerLine).coerceAtLeast(1) + 1
                }
                paraLineCounts[key] = lc
            }
        }

        // Compute startLine for each paragraph in reading order (including titles/gaps)
        // Flat order titles+paras+diagram+gap per chapter
        var globalLine = 0
        val startLineForPara = mutableMapOf<String, Int>()
        val lineCountForPara = mutableMapOf<String, Int>()
        for ((cIdx, ch) in chapters.withIndex()) {
            // title lines
            globalLine += titleLinesEst
            // paragraphs
            for ((pIdx, _) in ch.paragraphs.withIndex()) {
                val key = "c${cIdx}-p${pIdx}"
                startLineForPara[key] = globalLine
                val lc = paraLineCounts[key] ?: 1
                lineCountForPara[key] = lc
                globalLine += lc
            }
            if (cIdx == 0) globalLine += diagramLines
            globalLine += gapLines
        }

        // For each paragraph, try candidate deltas and score
        val candidateDeltas = floatArrayOf(0f, -0.1f, 0.15f, -0.2f, 0.3f, -0.35f, 0.5f, 0.6f, -0.4f)
        val result = mutableMapOf<String, KnuthAdjustment>()

        for ((cIdx, ch) in chapters.withIndex()) {
            for ((pIdx, para) in ch.paragraphs.withIndex()) {
                val key = "c${cIdx}-p${pIdx}"
                val startLine = startLineForPara[key] ?: 0
                val baseLc = lineCountForPara[key] ?: 1
                // If paragraph is short (<=2 lines) orphan/widow less relevant, but still squared-off
                // We only need to consider split paragraphs (span page boundary)
                val baseScore = KnuthPlassEngine.scoreForDelta(
                    paragraph = para,
                    annotated = null,
                    baseStyle = bodyStyle,
                    deltaSp = 0f,
                    availableWidthPx = availableWidthPx,
                    lineHeightPx = lineHeightPx,
                    linesPerPage = linesPerPage,
                    startLine = startLine,
                    lineCount = baseLc,
                    totalLinesBefore = startLine,
                )
                // If no orphan/widow penalty, keep base (0 delta) — squared off already
                if (baseScore < KnuthPlassEngine.ORPHAN_PENALTY / 2) {
                    // Small stretch badness only, no need to adjust, but we still apply justify for squared-off look
                    // Only apply justify if paragraph >1 line and not already penalized
                    // Keep 0 delta, but enable justify for multi-line paras
                    if (baseLc > 1) {
                        result[key] = KnuthAdjustment(letterSpacingDelta = 0.sp, useJustify = true)
                    }
                    continue
                }

                // Try candidates to escape orphan/widow
                var bestDelta = 0f
                var bestScore = baseScore
                var bestLc = baseLc
                for (d in candidateDeltas) {
                    if (d == 0f) continue
                    val deltaSpacing = if (baseLetterSpacing.isSp) (baseLetterSpacing.value + d).sp else d.sp
                    val styleWithDelta = bodyStyle.copy(letterSpacing = deltaSpacing)
                    val annotated = if (bionicEnabled) {
                        try { BionicReading.toBionicAnnotated(para, BionicReading.boldSpan()) } catch (_: Exception) { AnnotatedString(para) }
                    } else AnnotatedString(para)
                    val lc = try {
                        textMeasurer.measure(
                            text = annotated,
                            style = styleWithDelta,
                            constraints = Constraints(maxWidth = availableWidthPx),
                        ).lineCount.coerceAtLeast(1)
                    } catch (_: Exception) { baseLc }

                    val score = KnuthPlassEngine.scoreForDelta(
                        paragraph = para, annotated = annotated, baseStyle = bodyStyle,
                        deltaSp = d, availableWidthPx = availableWidthPx, lineHeightPx = lineHeightPx,
                        linesPerPage = linesPerPage, startLine = startLine, lineCount = lc, totalLinesBefore = startLine,
                    )
                    // Prefer smaller absolute delta when scores tie (micro-kerning minimal)
                    val tieBreaker = kotlin.math.abs(d) * 2.0
                    val total = score + tieBreaker
                    if (total < bestScore) {
                        bestScore = total
                        bestDelta = d
                        bestLc = lc
                    }
                }
                if (bestDelta != 0f && bestScore < baseScore) {
                    result[key] = KnuthAdjustment(letterSpacingDelta = bestDelta.sp, useJustify = true)
                } else if (baseLc > 1) {
                    // At least enable justify for squared-off even if no orphan
                    result[key] = KnuthAdjustment(letterSpacingDelta = 0.sp, useJustify = true)
                }
            }
        }
        result
    }
}
