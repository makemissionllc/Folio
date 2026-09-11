package com.makemission.folio.ui.reader

/**
 * Reading navigation style — persisted via DataStore.
 *
 * CONTINUOUS (default): classic vertical scroll through the whole book, chapter
 * boundaries are just amber rules while scrolling. CHAPTER_SWIPE: vertical scroll
 * within a chapter, horizontal swipe between chapters (tablet: spreads/pairs).
 * Builds on existing LazyColumn + TruePageEngine + volume-key handling; don't rewrite those.
 */
enum class ReadingNavigationMode(
    val displayName: String,
    val description: String,
) {
    CONTINUOUS(
        displayName = "Continuous scroll",
        description = "Vertical through whole book"
    ),
    CHAPTER_SWIPE(
        displayName = "Chapter swipe",
        description = "Swipe left/right between chapters"
    );

    companion object {
        fun fromKey(key: String): ReadingNavigationMode =
            entries.find { it.name == key } ?: CONTINUOUS
    }
}
