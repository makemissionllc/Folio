package com.makemission.folio.data.xray

import com.makemission.folio.data.epub.EpubParser
import kotlin.math.ln

/**
 * TF-IDF X-Ray extractor (§5).
 *
 * For each chapter, compares term frequency in that chapter vs. frequency
 * across the whole book. Candidate "nouns" are capitalized words (proper
 * nouns) filtered by stopwords and length — no NLP tagger, no network,
 * fully deterministic and on-device.
 */
object XRayExtractor {

    private val stopwords = setOf(
        "the", "and", "for", "with", "this", "that", "from", "have", "will", "would", "could", "should",
        "when", "where", "what", "which", "their", "there", "were", "been", "being", "about", "into",
        "through", "after", "before", "under", "over", "between", "among", "she", "her", "his", "him",
        "they", "them", "you", "your", "our", "was", "are", "had", "has", "did", "does", "not", "but",
        "all", "one", "each", "other", "many", "some", "such", "only", "than", "then", "once", "here",
        "also", "very", "just", "more", "most", "can", "may", "might", "must", "shall", "being", "been",
        "were", "had", "has", "have", "was", "are", "is", "a", "an", "in", "on", "at", "by", "of", "to",
        "as", "it", "its", "if", "so", "or", "no", "yes", "we", "he", "she", "it", "i", "me", "my", "mine",
        "our", "ours", "your", "yours", "his", "hers", "its", "their", "theirs", "am", "is", "are", "was",
        "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will", "would", "shall",
        "should", "may", "might", "must", "can", "could"
    )

    private val capitalizedWord = Regex("\\b[A-Z][a-z]{2,}\\b")

    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\W+")).filter { it.isNotBlank() }

    private fun candidateTerms(text: String): List<String> {
        return capitalizedWord.findAll(text)
            .map { it.value }
            .filter { it.length >= 3 }
            .filter { it.lowercase() !in stopwords }
            .toList()
    }

    /**
     * Build per-chapter X-Ray index. Top [topK] terms per chapter by TF-IDF.
     * Returns map chapterIndex -> list of terms sorted descending by score.
     */
    fun extract(
        chapters: List<EpubParser.EpubChapter>,
        topK: Int = 8,
    ): Map<Int, List<XRayTerm>> {
        if (chapters.isEmpty()) return emptyMap()
        val totalChapters = chapters.size

        // Per-chapter term counts and total words
        val chapterTermCounts = mutableListOf<Map<String, Int>>()
        val chapterTotalWords = mutableListOf<Int>()
        val termDocFreq = mutableMapOf<String, Int>() // df

        for (ch in chapters) {
            val fullText = buildString {
                append(ch.title); append(" ")
                ch.paragraphs.forEach { append(it); append(" ") }
            }
            val words = tokenize(fullText)
            chapterTotalWords.add(words.size.coerceAtLeast(1))
            val candidates = candidateTerms(fullText)
            val counts = candidates.groupingBy { it.lowercase() }.eachCount()
            chapterTermCounts.add(counts)
            // df: in how many chapters does term appear?
            for (term in counts.keys) {
                termDocFreq[term] = (termDocFreq[term] ?: 0) + 1
            }
        }

        // Also need total frequency per term across book (for display)
        val totalFreq = mutableMapOf<String, Int>()
        for (counts in chapterTermCounts) {
            for ((term, c) in counts) {
                totalFreq[term] = (totalFreq[term] ?: 0) + c
            }
        }

        // For each term, which chapters contain it (for tap detail)
        val termToChapters = mutableMapOf<String, MutableList<Int>>()
        for ((chIdx, counts) in chapterTermCounts.withIndex()) {
            for (term in counts.keys) {
                termToChapters.getOrPut(term) { mutableListOf() }.add(chIdx)
            }
        }

        // TF-IDF per chapter
        val result = mutableMapOf<Int, List<XRayTerm>>()
        for ((chIdx, counts) in chapterTermCounts.withIndex()) {
            val totalWords = chapterTotalWords[chIdx].toDouble()
            val scored = counts.map { (termLower, count) ->
                val tf = count / totalWords
                val df = termDocFreq[termLower] ?: 1
                val idf = ln(totalChapters.toDouble() / df.toDouble())
                val score = tf * idf
                // Recover display form: first capitalized occurrence in this chapter
                val display = candidateTerms(
                    chapters[chIdx].let { ch -> ch.title + " " + ch.paragraphs.joinToString(" ") }
                ).firstOrNull { it.lowercase() == termLower } ?: termLower.replaceFirstChar { it.uppercase() }
                XRayTerm(
                    term = display,
                    normalized = termLower,
                    score = score,
                    chapterIndices = termToChapters[termLower]?.toList() ?: emptyList(),
                    totalFrequency = totalFreq[termLower] ?: count,
                )
            }.sortedByDescending { it.score }
                .take(topK)
            result[chIdx] = scored
        }
        return result
    }

    /** Precomputed global stats for progressive per-chapter extraction (lightweight). */
    data class GlobalStats(
        val totalChapters: Int,
        val chapterTermCounts: List<Map<String, Int>>,
        val chapterTotalWords: List<Int>,
        val termDocFreq: Map<String, Int>,
        val totalFreq: Map<String, Int>,
        val termToChapters: Map<String, List<Int>>,
    )

    fun precomputeGlobalStats(chapters: List<EpubParser.EpubChapter>): GlobalStats {
        if (chapters.isEmpty()) return GlobalStats(0, emptyList(), emptyList(), emptyMap(), emptyMap(), emptyMap())
        val chapterTermCounts = mutableListOf<Map<String, Int>>()
        val chapterTotalWords = mutableListOf<Int>()
        val termDocFreq = mutableMapOf<String, Int>()
        for (ch in chapters) {
            val fullText = buildString { append(ch.title); append(" "); ch.paragraphs.forEach { append(it); append(" ") } }
            val words = tokenize(fullText)
            chapterTotalWords.add(words.size.coerceAtLeast(1))
            val candidates = candidateTerms(fullText)
            val counts = candidates.groupingBy { it.lowercase() }.eachCount()
            chapterTermCounts.add(counts)
            for (term in counts.keys) termDocFreq[term] = (termDocFreq[term] ?: 0) + 1
        }
        val totalFreq = mutableMapOf<String, Int>()
        for (counts in chapterTermCounts) for ((term, c) in counts) totalFreq[term] = (totalFreq[term] ?: 0) + c
        val termToChapters = mutableMapOf<String, MutableList<Int>>()
        for ((chIdx, counts) in chapterTermCounts.withIndex()) for (term in counts.keys) termToChapters.getOrPut(term) { mutableListOf() }.add(chIdx)
        val immutableTermToChapters = termToChapters.mapValues { it.value.toList() }
        return GlobalStats(chapters.size, chapterTermCounts, chapterTotalWords, termDocFreq, totalFreq, immutableTermToChapters)
    }

    /** Extract only [chapterIndex] using precomputed [stats] — for progressive prioritization. */
    fun extractChapter(
        chapters: List<EpubParser.EpubChapter>,
        chapterIndex: Int,
        stats: GlobalStats,
        topK: Int = 8,
    ): List<XRayTerm> {
        if (chapterIndex !in chapters.indices) return emptyList()
        if (stats.totalChapters != chapters.size) return extract(chapters, topK)[chapterIndex] ?: emptyList()
        val counts = stats.chapterTermCounts.getOrNull(chapterIndex) ?: return emptyList()
        val totalWords = stats.chapterTotalWords.getOrNull(chapterIndex)?.toDouble() ?: 1.0
        val scored = counts.map { (termLower, count) ->
            val tf = count / totalWords
            val df = stats.termDocFreq[termLower] ?: 1
            val idf = ln(stats.totalChapters.toDouble() / df.toDouble())
            val score = tf * idf
            val display = candidateTerms(chapters[chapterIndex].let { ch -> ch.title + " " + ch.paragraphs.joinToString(" ") }).firstOrNull { it.lowercase() == termLower } ?: termLower.replaceFirstChar { it.uppercase() }
            XRayTerm(
                term = display,
                normalized = termLower,
                score = score,
                chapterIndices = stats.termToChapters[termLower] ?: emptyList(),
                totalFrequency = stats.totalFreq[termLower] ?: count,
            )
        }.sortedByDescending { it.score }.take(topK)
        return scored
    }
}
