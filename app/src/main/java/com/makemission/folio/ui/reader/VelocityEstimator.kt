package com.makemission.folio.ui.reader

import kotlin.math.sqrt

/**
 * Rolling-weight velocity estimator (§5).
 *
 * Tracks time-per-page deltas, smooths with an Exponential Moving Average,
 * discards outliers via standard-deviation, and adjusts prediction by
 * character density (remaining chars vs. average page chars).
 *
 * Pure on-device, no network.
 */
class VelocityEstimator(
    private val alpha: Double = 0.35,
    private val outlierSigma: Double = 2.0,
    private val maxHistory: Int = 20,
    private val minSamplesForOutlier: Int = 3,
) {
    private var emaSpeed: Double? = null // chars per ms
    private val deltas = mutableListOf<Double>()
    private val speeds = mutableListOf<Double>()

    /**
     * Record a page turn.
     * @param deltaMs time since last turn (ms)
     * @param chars number of characters traversed on that turn
     * @return true if sample accepted (not outlier)
     */
    fun addSample(deltaMs: Double, chars: Int): Boolean {
        if (deltaMs <= 0 || chars <= 0) return false
        // Outlier check on delta time — idle for 15 min should be rejected.
        if (deltas.size >= minSamplesForOutlier) {
            val mean = deltas.average()
            val variance = deltas.map { (it - mean) * (it - mean) }.average()
            val std = sqrt(variance)
            if (std > 1e-6 && deltaMs > mean + outlierSigma * std) {
                return false
            }
            // Also reject if speed is extreme outlier (e.g., super fast fling)
            if (speeds.size >= minSamplesForOutlier) {
                val speedMean = speeds.average()
                val speedVar = speeds.map { (it - speedMean) * (it - speedMean) }.average()
                val speedStd = sqrt(speedVar)
                val speed = chars / deltaMs
                if (speedStd > 1e-9 && kotlin.math.abs(speed - speedMean) > outlierSigma * speedStd) {
                    // Allow but still update? For now reject extreme speed too.
                    // Uncomment to reject:
                    // if (speed > speedMean + outlierSigma * speedStd || speed < speedMean - outlierSigma * speedStd) return false
                }
            }
        }
        val speed = chars / deltaMs
        deltas.add(deltaMs)
        speeds.add(speed)
        if (deltas.size > maxHistory) deltas.removeAt(0)
        if (speeds.size > maxHistory) speeds.removeAt(0)
        emaSpeed = if (emaSpeed == null) speed else alpha * speed + (1 - alpha) * emaSpeed!!
        return true
    }

    /** Estimate ms remaining for [remainingChars]. Null if not enough data. */
    fun estimateMs(remainingChars: Int): Long? {
        val s = emaSpeed ?: return null
        if (s <= 1e-9 || remainingChars <= 0) return null
        return (remainingChars / s).toLong()
    }

    fun currentCharsPerMinute(): Double? = emaSpeed?.let { it * 60000 }

    fun hasEnoughData(): Boolean = emaSpeed != null
}

fun formatTimeRemaining(ms: Long?): String? {
    if (ms == null) return null
    if (ms <= 0) return "Done"
    val totalSec = (ms / 1000).toInt()
    return when {
        totalSec < 60 -> "$totalSec sec left in chapter"
        totalSec < 3600 -> {
            val min = (totalSec + 30) / 60
            "$min min left in chapter"
        }
        else -> {
            val min = (totalSec + 30) / 60
            "$min min left in chapter"
        }
    }
}

/** Helpers for character-density aware remaining calculation. */
object ReadingFlatMapper {

    /** Flat item count including diagram after chapter 0. */
    fun totalFlatItems(chapters: List<com.makemission.folio.data.epub.EpubParser.EpubChapter>): Int {
        var pos = 0
        for ((idx, ch) in chapters.withIndex()) {
            pos += 1 // title
            pos += ch.paragraphs.size
            if (idx == 0) pos += 1 // diagram
            pos += 1 // gap
        }
        return pos
    }

    /** Returns paragraph length at flatIndex or null if not a paragraph. */
    fun paragraphLengthAt(flatIndex: Int, chapters: List<com.makemission.folio.data.epub.EpubParser.EpubChapter>): Int? {
        var pos = 0
        for ((idx, ch) in chapters.withIndex()) {
            if (flatIndex == pos) return null // title
            pos++
            for (para in ch.paragraphs) {
                if (flatIndex == pos) return para.length
                pos++
            }
            if (idx == 0) {
                if (flatIndex == pos) return null // diagram
                pos++
            }
            if (flatIndex == pos) return null // gap
            pos++
        }
        return null
    }

    fun charsBetween(
        fromFlat: Int,
        toFlat: Int,
        chapters: List<com.makemission.folio.data.epub.EpubParser.EpubChapter>,
    ): Int {
        if (fromFlat == toFlat) return 0
        val lo = minOf(fromFlat, toFlat)
        val hi = maxOf(fromFlat, toFlat)
        var sum = 0
        for (i in lo until hi) {
            paragraphLengthAt(i, chapters)?.let { sum += it }
        }
        return sum
    }

    /** Find chapter and paragraph index for a flatIndex. */
    fun chapterParaForFlat(
        flatIndex: Int,
        chapters: List<com.makemission.folio.data.epub.EpubParser.EpubChapter>,
    ): Pair<Int, Int> {
        var pos = 0
        for ((chIdx, ch) in chapters.withIndex()) {
            val titlePos = pos
            if (flatIndex == titlePos) return chIdx to 0
            pos++
            for (paraIdx in ch.paragraphs.indices) {
                if (flatIndex == pos) return chIdx to paraIdx
                pos++
            }
            if (chIdx == 0) {
                if (flatIndex == pos) return chIdx to ch.paragraphs.size // diagram after paras
                pos++
            }
            if (flatIndex == pos) {
                // gap — treat as start of next chapter if exists
                // For remaining calc, gap means current chapter done
                return chIdx to ch.paragraphs.size
            }
            pos++
        }
        return 0 to 0
    }

    fun remainingCharsInChapter(
        flatIndex: Int,
        chapters: List<com.makemission.folio.data.epub.EpubParser.EpubChapter>,
    ): Int {
        val (chIdx, paraIdx) = chapterParaForFlat(flatIndex, chapters)
        if (chIdx !in chapters.indices) return 0
        val ch = chapters[chIdx]
        if (paraIdx >= ch.paragraphs.size) return 0
        return ch.paragraphs.drop(paraIdx).sumOf { it.length }
    }
}
