package com.makemission.folio.data.vocabulary

import com.makemission.folio.data.db.entity.VocabularyCard
import kotlin.math.roundToInt

/**
 * SM-2 (SuperMemo-2) scheduling — pure on-device, deterministic.
 * No network, no AI. Called after the user self-rates recall.
 *
 * Quality mapping for Folio's 4 buttons:
 *  Again = 0  (complete blackout)
 *  Hard  = 3  (correct but difficult)
 *  Good  = 4  (correct with hesitation)
 *  Easy  = 5  (perfect)
 *
 * Interval is in days, dueAt is millis.
 */
object Sm2 {

    const val AGAIN = 0
    const val HARD = 3
    const val GOOD = 4
    const val EASY = 5

    private const val MIN_EASE = 1.3f

    fun schedule(card: VocabularyCard, quality: Int, nowMillis: Long = System.currentTimeMillis()): VocabularyCard {
        require(quality in 0..5)
        var ease = card.easeFactor
        var interval = card.intervalDays
        var reps = card.repetitions

        if (quality < 3) {
            // Failed — reset
            reps = 0
            interval = 1
        } else {
            // Correct — advance
            interval = when (reps) {
                0 -> 1
                1 -> 6
                else -> (interval * ease).roundToInt().coerceAtLeast(1)
            }
            reps += 1
            // Update ease factor
            ease += (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
            if (ease < MIN_EASE) ease = MIN_EASE
        }

        val dueAt = nowMillis + interval * 24L * 60L * 60L * 1000L
        return card.copy(
            easeFactor = ease,
            intervalDays = interval,
            repetitions = reps,
            dueAt = dueAt,
            lastReviewedAt = nowMillis,
            reviewCount = card.reviewCount + 1,
        )
    }

    /** For a brand-new word looked up, create a card due now. */
    fun newCard(word: String, definition: String, nowMillis: Long = System.currentTimeMillis()): VocabularyCard =
        VocabularyCard(
            word = word.lowercase(),
            definition = definition,
            easeFactor = 2.5f,
            intervalDays = 0,
            repetitions = 0,
            dueAt = nowMillis,
            lastReviewedAt = null,
            reviewCount = 0,
            createdAt = nowMillis,
        )
}
