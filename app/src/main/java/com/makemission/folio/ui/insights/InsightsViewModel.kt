package com.makemission.folio.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.makemission.folio.data.db.FolioDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Read-only aggregation over existing Room data — no parallel tracking.
 * Queries Highlight / Bookmark / ReadingProgress / VocabularyCard / BookEntity
 * that already exist (§6 Technical Foundation). Structural inspiration from
 * book-story-master's history grouping only.
 */
data class InsightsState(
    val isLoading: Boolean = true,
    // Shelf
    val totalBooks: Int = 0,
    val inProgressBooks: Int = 0,
    // Marginalia
    val totalHighlights: Int = 0,
    val totalBookmarks: Int = 0,
    // Vocabulary (SM-2)
    val totalVocab: Int = 0,
    val dueVocab: Int = 0,
    val masteredVocab: Int = 0,
    // Rhythm from ReadingProgress.lastReadMillis
    val readingSessions: Int = 0,
    val distinctDays: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastReadLabel: String? = null,
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FolioDatabase.get(application)

    val state: StateFlow<InsightsState> = combine(
        db.bookDao().observeAll(),
        db.highlightDao().observeAll(),
        db.bookmarkDao().observeAll(),
        db.readingProgressDao().observeAll(),
        db.vocabularyDao().observeAll(),
    ) { books, highlights, bookmarks, progress, vocab ->

        val totalBooks = books.size
        val inProgressBooks = progress.size // each progress row = one book in progress

        val totalHighlights = highlights.size
        val totalBookmarks = bookmarks.size

        val totalVocab = vocab.size
        val now = System.currentTimeMillis()
        val dueVocab = vocab.count { it.dueAt <= now }
        val masteredVocab = vocab.count { it.repetitions >= 3 || it.intervalDays >= 21 }

        val readingSessions = progress.size
        // Streaks from lastReadMillis
        val zone = ZoneId.systemDefault()
        val days = progress.mapNotNull { p ->
            try { Instant.ofEpochMilli(p.lastReadMillis).atZone(zone).toLocalDate() } catch (_: Exception) { null }
        }.distinct().sorted()

        val distinctDays = days.size
        val (currentStreak, longestStreak) = computeStreaks(days)
        val lastReadLabel = when {
            days.isEmpty() -> null
            else -> {
                val last = days.maxOrNull() ?: return@combine InsightsState(
                    isLoading = false,
                    totalBooks = totalBooks,
                    inProgressBooks = inProgressBooks,
                    totalHighlights = totalHighlights,
                    totalBookmarks = totalBookmarks,
                    totalVocab = totalVocab,
                    dueVocab = dueVocab,
                    masteredVocab = masteredVocab,
                    readingSessions = readingSessions,
                    distinctDays = distinctDays,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    lastReadLabel = null,
                )
                val today = LocalDate.now(zone)
                val diff = ChronoUnit.DAYS.between(last, today)
                when (diff) {
                    0L -> "today"
                    1L -> "yesterday"
                    else -> "${diff}d ago"
                }
            }
        }

        InsightsState(
            isLoading = false,
            totalBooks = totalBooks,
            inProgressBooks = inProgressBooks,
            totalHighlights = totalHighlights,
            totalBookmarks = totalBookmarks,
            totalVocab = totalVocab,
            dueVocab = dueVocab,
            masteredVocab = masteredVocab,
            readingSessions = readingSessions,
            distinctDays = distinctDays,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastReadLabel = lastReadLabel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState(isLoading = true))

    private fun computeStreaks(sortedDays: List<LocalDate>): Pair<Int, Int> {
        if (sortedDays.isEmpty()) return 0 to 0
        // Longest streak anywhere
        var longest = 1
        var run = 1
        for (i in 1 until sortedDays.size) {
            val diff = ChronoUnit.DAYS.between(sortedDays[i - 1], sortedDays[i])
            if (diff == 1L) run++ else if (diff > 1L) run = 1
            if (run > longest) longest = run
        }
        // Current streak ending at most recent day, or today if contiguous
        val today = LocalDate.now(ZoneId.systemDefault())
        val last = sortedDays.last()
        val gapToToday = ChronoUnit.DAYS.between(last, today)
        // If last reading was >1 day ago, current streak is 0 (or last run if we consider trailing)
        // We show the trailing streak ending at last reading, but 0 if gap >1 to encourage return.
        val current = if (gapToToday <= 1L) {
            // count backwards from last
            var c = 1
            for (i in sortedDays.size - 1 downTo 1) {
                if (ChronoUnit.DAYS.between(sortedDays[i - 1], sortedDays[i]) == 1L) c++ else break
            }
            // If gap is 1, streak still holds; gap 0 means today, keep as is.
            c
        } else {
            0
        }
        return current to longest
    }
}
