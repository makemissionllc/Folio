package com.makemission.folio.data.anchor

import com.makemission.folio.data.epub.EpubParser
import kotlin.math.max

/**
 * LCS-based annotation anchoring (§5).
 *
 * Instead of byte offsets, each highlight stores its surrounding text
 * (anchor). When the EPUB file changes, we scan the new parsed text for
 * the closest anchor match using a Longest Common Subsequence diff and
 * reattach the highlight. Pure on-device, no network.
 *
 * If no reasonable match is found the highlight is left orphaned.
 */
object LcsAnchor {

    /** Minimum LCS similarity (0..1) to consider a match — avoids random guessing. */
    const val MATCH_THRESHOLD = 0.55

    /** Length of snippet stored per highlight. */
    const val ANCHOR_SNIPPET_LEN = 80

    /**
     * Extract a snippet around the highlight position: take the paragraph
     * that contains the highlight and trim to [ANCHOR_SNIPPET_LEN].
     */
    fun snippetForHighlight(
        chapters: List<EpubParser.EpubChapter>,
        chapterIndex: Int,
        paragraphIndex: Int? = null,
    ): String {
        val ch = chapters.getOrNull(chapterIndex) ?: return ""
        val para = when {
            paragraphIndex != null && paragraphIndex in ch.paragraphs.indices -> ch.paragraphs[paragraphIndex]
            ch.paragraphs.isNotEmpty() -> ch.paragraphs.first()
            else -> ch.title
        }
        return para.take(ANCHOR_SNIPPET_LEN).trim()
    }

    /** Classic LCS length (DP, O(m*n) with two rows). */
    fun lcsLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1)
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1
                else max(prev[j], curr[j - 1])
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }

    fun similarity(anchor: String, candidate: String): Double {
        if (anchor.isEmpty() || candidate.isEmpty()) return 0.0
        val lcs = lcsLength(anchor, candidate)
        return lcs.toDouble() / anchor.length.toDouble()
    }

    data class Match(
        val chapterIndex: Int,
        val paragraphIndex: Int,
        val similarity: Double,
        val paragraphText: String,
    )

    /**
     * Scan all paragraphs for the best LCS match to [anchorText].
     * Returns null if best similarity < [threshold].
     */
    fun findBestMatch(
        anchorText: String,
        chapters: List<EpubParser.EpubChapter>,
        threshold: Double = MATCH_THRESHOLD,
    ): Match? {
        if (anchorText.isBlank() || chapters.isEmpty()) return null
        var best: Match? = null
        var bestScore = 0.0
        for ((chIdx, ch) in chapters.withIndex()) {
            // Also consider chapter title as candidate
            val titleSim = similarity(anchorText, ch.title)
            if (titleSim > bestScore) {
                bestScore = titleSim
                best = Match(chIdx, -1, titleSim, ch.title)
            }
            for ((pIdx, para) in ch.paragraphs.withIndex()) {
                // Compare against full paragraph and also a windowed slice
                // to handle anchors that are substrings
                val sim = similarity(anchorText, para)
                if (sim > bestScore) {
                    bestScore = sim
                    best = Match(chIdx, pIdx, sim, para)
                }
                // Sliding window for long paragraphs (anchor may be substring of longer para)
                if (para.length > anchorText.length * 1.5) {
                    val window = para.length - anchorText.length
                    var wBest = 0.0
                    // Sample every 20 chars to keep it cheap
                    var off = 0
                    while (off <= window) {
                        val slice = para.substring(off, off + anchorText.length)
                        val ws = similarity(anchorText, slice)
                        if (ws > wBest) wBest = ws
                        off += 20
                    }
                    if (wBest > bestScore) {
                        bestScore = wBest
                        best = Match(chIdx, pIdx, wBest, para)
                    }
                }
            }
        }
        return if (best != null && bestScore >= threshold) best else null
    }
}
