package com.makemission.folio.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt

/**
 * Deterministic, on-device bionic reading (§5).
 *
 * Analyzes each word's onset / nucleus / coda to find the first syllable
 * without a dictionary or network. The first syllable is then bolded to
 * create a visual anchor that speeds up scanning.
 *
 * Vowel set includes `y` when not word-initial; consecutive vowels form a
 * single nucleus. Coda handling avoids awkward splits (e.g. a single
 * consonant that is the onset of the next syllable is not pulled into the
 * first syllable). Bold length is clamped to ~60% so longer words keep a
 * trailing tail.
 */
object BionicReading {

    private val vowels = setOf('a', 'e', 'i', 'o', 'u')

    private fun isVowel(c: Char, index: Int): Boolean {
        val lc = c.lowercaseChar()
        if (lc in vowels) return true
        // y is vowel when not word-initial
        return lc == 'y' && index != 0
    }

    private fun isConsonant(c: Char, index: Int): Boolean =
        c.isLetter() && !isVowel(c, index)

    /**
     * Returns exclusive end index of the bold prefix within [word].
     * [word] should be letters-only (no leading/trailing punctuation).
     */
    fun boldEndForWord(word: String): Int {
        val n = word.length
        if (n <= 1) return n
        if (n <= 3) return 1
        // Find first vowel (nucleus onset)
        var firstVowel = -1
        for (i in word.indices) {
            if (isVowel(word[i], i)) {
                firstVowel = i
                break
            }
        }
        if (firstVowel == -1) {
            // No vowel (e.g. "myths") — fallback to ~40%
            return (n * 0.4).roundToInt().coerceIn(1, n - 1)
        }
        // End of first vowel cluster (nucleus)
        var nucleusEnd = firstVowel
        while (nucleusEnd + 1 < n && isVowel(word[nucleusEnd + 1], nucleusEnd + 1)) {
            nucleusEnd++
        }
        var end = nucleusEnd + 1 // exclusive
        // Coda: include one trailing consonant only if it is not the onset of the next syllable
        if (end < n && isConsonant(word[end], end)) {
            val nextIsVowel = end + 1 < n && isVowel(word[end + 1], end + 1)
            if (!nextIsVowel) {
                end += 1
                // Don't greedily consume a cluster like "str" — just one coda consonant
            }
        }
        // Clamp so bold is not the whole word and not too short
        val maxBold = (n * 0.6).roundToInt().coerceAtLeast(1).coerceAtMost(n - 1)
        val minBold = 1
        return end.coerceIn(minBold, maxBold)
    }

    /**
     * Build an [AnnotatedString] for [paragraph] where the first syllable of
     * each word is bolded. Non-letters and spaces are left as-is and not bolded.
     * Deterministic and entirely on-device.
     */
    fun toBionicAnnotated(paragraph: String, boldStyle: SpanStyle): AnnotatedString {
        if (paragraph.isEmpty()) return AnnotatedString(paragraph)
        return buildAnnotatedString {
            var i = 0
            while (i < paragraph.length) {
                val c = paragraph[i]
                if (c.isLetter()) {
                    var j = i
                    while (j < paragraph.length && (paragraph[j].isLetter() || paragraph[j] == '\'')) {
                        j++
                    }
                    val rawWord = paragraph.substring(i, j)
                    // Separate leading/trailing apostrophes for analysis, but keep them in output
                    val core = rawWord.trim('\'')
                    if (core.isEmpty() || core.length == 1) {
                        // Single letter or apostrophe-only — bold as-is
                        if (core.isNotEmpty()) {
                            withStyle(boldStyle) { append(rawWord) }
                        } else {
                            append(rawWord)
                        }
                    } else {
                        // Compute bold length on core (without apostrophes for syllable logic)
                        val coreForAnalysis = core.replace("'", "")
                        if (coreForAnalysis.isEmpty()) {
                            append(rawWord)
                        } else {
                            val boldLenCore = boldEndForWord(coreForAnalysis)
                            // Map boldLenCore back onto rawWord (which may contain apostrophes)
                            // For simplicity, bold the first boldLenCore letters of rawWord, skipping apostrophes
                            var boldCount = 0
                            var rawBoldEnd = 0
                            var idx = 0
                            while (idx < rawWord.length && boldCount < boldLenCore) {
                                if (rawWord[idx].isLetter()) boldCount++
                                rawBoldEnd = idx + 1
                                idx++
                            }
                            if (rawBoldEnd > 0) {
                                withStyle(boldStyle) { append(rawWord.substring(0, rawBoldEnd)) }
                                append(rawWord.substring(rawBoldEnd))
                            } else {
                                append(rawWord)
                            }
                        }
                    }
                    i = j
                } else {
                    append(c.toString())
                    i++
                }
            }
        }
    }

    /** Convenience: bold style is just weight — caller controls family/color. */
    fun boldSpan(): SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)
}
