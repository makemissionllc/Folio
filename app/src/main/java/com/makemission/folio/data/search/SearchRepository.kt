package com.makemission.folio.data.search

import android.content.Context
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device search across imported books' parsed EPUB text + Room data.
 * No network — queries local Room highlights/bookmarks and parses private
 * files via [EpubParser]. Prioritizes highlights/bookmarks first, then
 * title/author, then general text. Structural inspiration from
 * book-story-master's SearchBooksUseCase only.
 */
object SearchRepository {

    enum class MatchType { HIGHLIGHT, BOOKMARK, TITLE_AUTHOR, CONTENT }

    data class SearchResult(
        val bookId: String,
        val bookTitle: String,
        val author: String,
        val chapterIndex: Int,
        val paragraphIndex: Int,
        val snippet: String,
        val matchType: MatchType,
        /** lower rank = higher priority */
        val rank: Int,
    )

    private const val MAX_RESULTS = 40
    private const val MAX_PER_BOOK = 6

    // Simple in-memory cache for parsed chapters to avoid re-parsing on each keystroke.
    // Thread-safe: search may be called from multiple coroutines while many background
    // workers write ParsedBookCache files concurrently (50+ books via SAF).
    private val chapterCache = java.util.concurrent.ConcurrentHashMap<String, List<EpubParser.EpubChapter>>()

    suspend fun searchLibrary(query: String, context: Context): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val qLower = q.lowercase()
        val db = FolioDatabase.get(context)
        val books = try { db.bookDao().getAll() } catch (_: Exception) { emptyList() }
        if (books.isEmpty()) return@withContext emptyList()

        val out = mutableListOf<SearchResult>()
        for (entity in books) {
            if (out.size >= MAX_RESULTS) break
            // Title / author match (high priority after highlights/bookmarks but before content)
            if (entity.title.contains(q, ignoreCase = true) || entity.author.contains(q, ignoreCase = true)) {
                out.add(
                    SearchResult(
                        bookId = entity.id,
                        bookTitle = entity.title,
                        author = entity.author,
                        chapterIndex = 0,
                        paragraphIndex = 0,
                        snippet = "${entity.title} — ${entity.author}".take(120),
                        matchType = MatchType.TITLE_AUTHOR,
                        rank = 1,
                    )
                )
            }
            // Highlights — prioritize inside user's existing highlights
            try {
                val highlights = db.highlightDao().getForBook(entity.id)
                for (h in highlights) {
                    if (out.size >= MAX_RESULTS) break
                    if (h.anchorText.contains(q, ignoreCase = true)) {
                        out.add(
                            SearchResult(
                                bookId = entity.id,
                                bookTitle = entity.title,
                                author = entity.author,
                                chapterIndex = h.chapterIndex,
                                paragraphIndex = 0, // highlight stores chapter only; jump to chapter start
                                snippet = h.anchorText.take(100),
                                matchType = MatchType.HIGHLIGHT,
                                rank = 0,
                            )
                        )
                        if (out.count { it.bookId == entity.id && it.matchType == MatchType.HIGHLIGHT } >= 2) break
                    }
                }
            } catch (_: Exception) {}

            // Bookmarks — also priority 0
            try {
                val bookmarks = db.bookmarkDao().getForBook(entity.id)
                for (bm in bookmarks) {
                    if (out.size >= MAX_RESULTS) break
                    if (bm.previewText.contains(q, ignoreCase = true)) {
                        out.add(
                            SearchResult(
                                bookId = entity.id,
                                bookTitle = entity.title,
                                author = entity.author,
                                chapterIndex = bm.chapterIndex,
                                paragraphIndex = bm.paragraphIndex,
                                snippet = bm.previewText.take(100),
                                matchType = MatchType.BOOKMARK,
                                rank = 0,
                            )
                        )
                        if (out.count { it.bookId == entity.id && it.matchType == MatchType.BOOKMARK } >= 2) break
                    }
                }
            } catch (_: Exception) {}

            // Full-text content search via EpubParser (on private file)
            // Defensive: a book still mid-processing (ParsedBookCache being written by BookWorker) or
            // with a corrupted/partial cache should not crash the whole search for 50+ books.
            // Skip that book's full-text portion gracefully.
            try {
                var perBookContent = 0
                val chapters = getChapters(entity, context)
                for ((cIdx, ch) in chapters.withIndex()) {
                    if (perBookContent >= MAX_PER_BOOK) break
                    // Chapter title match counts as CONTENT (rank 2)
                    if (ch.title.contains(q, ignoreCase = true)) {
                        out.add(
                            SearchResult(
                                bookId = entity.id,
                                bookTitle = entity.title,
                                author = entity.author,
                                chapterIndex = cIdx,
                                paragraphIndex = 0,
                                snippet = ch.title.take(100),
                                matchType = MatchType.CONTENT,
                                rank = 2,
                            )
                        )
                        perBookContent++
                    }
                    for ((pIdx, para) in ch.paragraphs.withIndex()) {
                        if (perBookContent >= MAX_PER_BOOK) break
                        if (para.lowercase().contains(qLower)) {
                            val idx = para.lowercase().indexOf(qLower)
                            val start = (idx - 40).coerceAtLeast(0)
                            val end = (idx + q.length + 40).coerceAtMost(para.length)
                            val snippet = para.substring(start, end).replace("\n", " ").trim()
                            out.add(
                                SearchResult(
                                    bookId = entity.id,
                                    bookTitle = entity.title,
                                    author = entity.author,
                                    chapterIndex = cIdx,
                                    paragraphIndex = pIdx,
                                    snippet = snippet,
                                    matchType = MatchType.CONTENT,
                                    rank = 2,
                                )
                            )
                            perBookContent++
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip this book's full-text if still processing / OOM / corrupt cache; title/highlights still searched above.
            } catch (_: OutOfMemoryError) {
            } catch (_: Throwable) {
            }
        }
        // Prioritize highlights/bookmarks first, then title/author, then general text
        out.sortedWith(compareBy<SearchResult> { it.rank }.thenBy { it.bookTitle }.thenBy { it.chapterIndex }.thenBy { it.paragraphIndex })
            .take(MAX_RESULTS)
    }

    suspend fun searchInBook(bookId: String, query: String, context: Context): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val qLower = q.lowercase()
        val db = FolioDatabase.get(context)
        val entity = try { db.bookDao().getById(bookId) } catch (_: Exception) { null } ?: return@withContext emptyList()

        val out = mutableListOf<SearchResult>()

        // Highlights priority
        try {
            for (h in db.highlightDao().getForBook(bookId)) {
                if (h.anchorText.contains(q, ignoreCase = true)) {
                    out.add(
                        SearchResult(
                            bookId = bookId,
                            bookTitle = entity.title,
                            author = entity.author,
                            chapterIndex = h.chapterIndex,
                            paragraphIndex = 0,
                            snippet = h.anchorText.take(100),
                            matchType = MatchType.HIGHLIGHT,
                            rank = 0,
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        try {
            for (bm in db.bookmarkDao().getForBook(bookId)) {
                if (bm.previewText.contains(q, ignoreCase = true)) {
                    out.add(
                        SearchResult(
                            bookId = bookId,
                            bookTitle = entity.title,
                            author = entity.author,
                            chapterIndex = bm.chapterIndex,
                            paragraphIndex = bm.paragraphIndex,
                            snippet = bm.previewText.take(100),
                            matchType = MatchType.BOOKMARK,
                            rank = 0,
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        try {
            val chapters = getChapters(entity, context)
            for ((cIdx, ch) in chapters.withIndex()) {
                if (ch.title.contains(q, ignoreCase = true)) {
                    out.add(
                        SearchResult(
                            bookId = bookId,
                            bookTitle = entity.title,
                            author = entity.author,
                            chapterIndex = cIdx,
                            paragraphIndex = 0,
                            snippet = ch.title.take(100),
                            matchType = MatchType.TITLE_AUTHOR,
                            rank = 1,
                        )
                    )
                }
                for ((pIdx, para) in ch.paragraphs.withIndex()) {
                    if (para.lowercase().contains(qLower)) {
                        val idx = para.lowercase().indexOf(qLower)
                        val start = (idx - 40).coerceAtLeast(0)
                        val end = (idx + q.length + 40).coerceAtMost(para.length)
                        val snippet = para.substring(start, end).replace("\n", " ").trim()
                        out.add(
                            SearchResult(
                                bookId = bookId,
                                bookTitle = entity.title,
                                author = entity.author,
                                chapterIndex = cIdx,
                                paragraphIndex = pIdx,
                                snippet = snippet,
                                matchType = MatchType.CONTENT,
                                rank = 2,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Skip full-text for this book if still processing / corrupt cache
        } catch (_: OutOfMemoryError) {
        } catch (_: Throwable) {
        }
        out.sortedWith(compareBy<SearchResult> { it.rank }.thenBy { it.chapterIndex }.thenBy { it.paragraphIndex })
            .take(MAX_RESULTS)
    }

    private suspend fun getChapters(entity: com.makemission.folio.data.db.entity.BookEntity, context: Context): List<EpubParser.EpubChapter> {
        chapterCache[entity.id]?.let { return it }
        // Try smart disk cache first (hash-validated) — avoids heavy re-parse on each keystroke/reopen.
        // Defensive: cache file may be partially written while BookProcessingWorker is still mid-processing
        // 50+ books concurrently; treat corrupt/partial JSON as miss, not crash.
        try {
            val cached = com.makemission.folio.data.cache.ParsedBookCache.load(context, entity.id, entity.fileHash)
            if (cached != null && cached.chapters.isNotEmpty()) {
                chapterCache[entity.id] = cached.chapters
                return cached.chapters
            }
        } catch (_: Exception) {
            // Partially-written cache -> skip, fall through to file parse or empty
        } catch (_: OutOfMemoryError) {
            return emptyList()
        }
        return try {
            val f = File(entity.filePath)
            // If file is huge and still being processed, parsing could OOM; catch Throwable.
            val epub = try {
                if (f.exists() && f.canRead()) EpubParser.parse(f) else null
            } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null } catch (_: Throwable) { null }
            val chapters = epub?.chapters ?: emptyList()
            if (chapters.isNotEmpty()) {
                chapterCache[entity.id] = chapters
                // Populate disk cache for next time (hash-validated) — best-effort, ignore if still processing
                try { entity.fileHash?.let { hash -> epub?.let { com.makemission.folio.data.cache.ParsedBookCache.save(context, entity.id, hash, it) } } } catch (_: Exception) {} catch (_: OutOfMemoryError) {}
            }
            chapters
        } catch (_: Exception) { emptyList() } catch (_: OutOfMemoryError) { emptyList() } catch (_: Throwable) { emptyList() }
    }

    fun clearCache() { chapterCache.clear() }
}
