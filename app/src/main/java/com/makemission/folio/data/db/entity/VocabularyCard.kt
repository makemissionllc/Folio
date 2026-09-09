package com.makemission.folio.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Vocabulary card for SM-2 spaced repetition (§5).
 * Stored when a word is looked up via the offline dictionary. On-device only.
 */
@Entity(tableName = "vocabulary")
data class VocabularyCard(
    @PrimaryKey
    val word: String, // lowercased key, e.g. "marginalia"
    val definition: String,
    // SM-2 scheduling fields
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
