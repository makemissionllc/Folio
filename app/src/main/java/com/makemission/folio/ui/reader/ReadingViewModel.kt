package com.makemission.folio.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.makemission.folio.data.anchor.LcsAnchor
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.Bookmark
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.db.entity.ReadingProgress
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.vocabulary.Sm2
import com.makemission.folio.data.xray.XRayCache
import com.makemission.folio.data.xray.XRayExtractor
import com.makemission.folio.data.xray.XRayTerm
import com.makemission.folio.data.search.SearchRepository
import com.makemission.folio.ui.reader.components.encodeFloats
import com.makemission.folio.ui.reader.components.encodePoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReadingUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val chapters: List<EpubParser.EpubChapter> = emptyList(),
    val isLoading: Boolean = true,
    val restoredChapterIndex: Int = 0,
    val restoredParagraphIndex: Int = 0,
    val fileHash: String? = null,
)

class ReadingViewModel(
    application: Application,
    private val bookId: String,
    private val bookTitle: String,
) : AndroidViewModel(application) {

    private val db = FolioDatabase.get(application)
    private val dao = db.readingProgressDao()
    private val highlightDao = db.highlightDao()
    private val bookmarkDao = db.bookmarkDao()
    private val vocabularyDao = db.vocabularyDao()

    private val _uiState = MutableStateFlow(
        ReadingUiState(bookId = bookId, bookTitle = bookTitle, isLoading = true),
    )
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    val highlights: StateFlow<List<Highlight>> =
        highlightDao.observeForBook(bookId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val bookmarks: StateFlow<List<Bookmark>> =
        bookmarkDao.observeForBook(bookId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // In-book search (on-device, highlights/bookmarks priority, same SearchRepository)
    private val _inBookQuery = MutableStateFlow("")
    val inBookQuery: StateFlow<String> = _inBookQuery

    private val _inBookResults = MutableStateFlow<List<SearchRepository.SearchResult>>(emptyList())
    val inBookResults: StateFlow<List<SearchRepository.SearchResult>> = _inBookResults

    private val _isInBookSearching = MutableStateFlow(false)
    val isInBookSearching: StateFlow<Boolean> = _isInBookSearching

    private val _xrayIndex = MutableStateFlow<Map<Int, List<XRayTerm>>>(emptyMap())
    val xrayIndex: StateFlow<Map<Int, List<XRayTerm>>> = _xrayIndex.asStateFlow()

    private val _isXRayLoading = MutableStateFlow(false)
    val isXRayLoading: StateFlow<Boolean> = _isXRayLoading.asStateFlow()

    init {
        viewModelScope.launch {
            FolioLogger.i("Reading", "Opening bookId=$bookId")
            val saved = try { dao.observe(bookId).firstOrNull() } catch (e: Exception) {
                FolioLogger.w("Reading", "Failed to load progress for $bookId: ${e.message}", e)
                null
            }
            val app = getApplication<Application>()
            val stored = try { db.bookDao().getById(bookId) } catch (e: Exception) {
                FolioLogger.w("Reading", "Failed to lookup book $bookId: ${e.message}", e)
                null
            }
            val fileHash = stored?.fileHash
            // Priority order: stored file FIRST for real books, assets only for curated samples (no filePath)
            // Performance: disk I/O (ParsedBookCache + file parse) on Dispatchers.IO to avoid main-thread jank.
            var epub: EpubParser.EpubBook? = null
            if (epub == null && stored?.filePath != null && fileHash != null) {
                epub = withContext(Dispatchers.IO) { ParsedBookCache.load(app, bookId, fileHash) }
                if (epub != null) {
                    FolioLogger.i("Reading", "Parsed cache hit for $bookId")
                }
            }
            // 2. Stored file (real books) — MUST be tried before assets
            if (epub == null && stored?.filePath != null) {
                val f = java.io.File(stored.filePath)
                if (f.exists() && f.canRead()) {
                    FolioLogger.i("Reading", "Parsing stored file for $bookId: ${stored.filePath?.take(80)}")
                    val parsed = withContext(Dispatchers.IO) { EpubParser.parse(f) }
                    if (parsed != null && fileHash != null) {
                        withContext(Dispatchers.IO) {
                            try { ParsedBookCache.save(app, bookId, fileHash, parsed) } catch (_: Exception) {}
                        }
                    }
                    epub = parsed
                    if (parsed == null) {
                        FolioLogger.w("Reading", "Parse failed for stored file $bookId, will not fall back to sample.epub (real book)")
                    }
                } else {
                    FolioLogger.w("Reading", "Stored file missing/unreadable for $bookId: ${stored.filePath?.take(80)}")
                }
            }
            // 3. Only for curated sample books (no stored filePath) try assets/sample.epub
            if (epub == null && stored?.filePath == null) {
                FolioLogger.i("Reading", "No stored file for $bookId — trying assets/sample.epub (curated sample)")
                epub = withContext(Dispatchers.IO) { EpubParser.loadFromAssetsOrNull(app) }
            }
            if (epub == null) FolioLogger.w("Reading", "EPUB parse null for $bookId, using fallback")
            val chapters = epub?.chapters ?: EpubParser.sampleFallbackChapters(bookTitle)

            // Prefer stored title/author when available (keeps library grid consistent)
            val displayTitle = stored?.title?.takeIf { it.isNotBlank() }
                ?: if (epub?.title?.isNotBlank() == true) epub.title else bookTitle

            // Use stored progress if valid
            val chapterIdx = saved?.chapterIndex?.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) ?: 0
            val paraIdx = saved?.paragraphIndex ?: 0

            _uiState.value = ReadingUiState(
                bookId = bookId,
                bookTitle = displayTitle,
                chapters = chapters,
                isLoading = false,
                restoredChapterIndex = chapterIdx,
                restoredParagraphIndex = paraIdx,
                fileHash = fileHash,
            )

            // X-Ray (§5): chapter-level progressive — prioritize current chapter, then next, then rest
            launchProgressiveXRay(app, bookId, chapters, fileHash, chapterIdx)

            // LCS re-anchoring (§5): if the EPUB file changed, relocate highlights
            reanchorHighlightsIfNeeded(chapters)
            observeInBookSearch()
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeInBookSearch() {
        viewModelScope.launch {
            _inBookQuery
                .debounce(260)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _inBookResults.value = emptyList()
                        _isInBookSearching.value = false
                    } else {
                        _isInBookSearching.value = true
                        try {
                            val results = SearchRepository.searchInBook(bookId, q, getApplication<Application>().applicationContext)
                            _inBookResults.value = results
                        } catch (e: Exception) {
                            FolioLogger.w("Search", "In-book search failed q='${q.take(60)}' ${e.message}", e)
                            _inBookResults.value = emptyList()
                        } finally {
                            _isInBookSearching.value = false
                        }
                    }
                }
        }
    }

    fun onInBookQueryChange(query: String) { _inBookQuery.value = query }
    fun clearInBookSearch() {
        _inBookQuery.value = ""
        _inBookResults.value = emptyList()
    }

    /**
     * Progressive X-Ray — chapter-by-chapter, prioritizing the chapter the user is
     * currently reading. Uses precomputed global TF-IDF stats so only the scored
     * terms for one chapter are computed at a time. Each chapter is cached via
     * [XRayCache.saveChapter] (hash-validated) so reopening doesn't redo work.
     * Next chapter is prefetched while the user reads the current one.
     */
    private fun launchProgressiveXRay(
        app: Application,
        bookId: String,
        chapters: List<EpubParser.EpubChapter>,
        fileHash: String?,
        startChapter: Int,
    ) {
        viewModelScope.launch {
            if (chapters.isEmpty()) {
                _isXRayLoading.value = false
                return@launch
            }
            // Performance: disk I/O for XRayCache on IO dispatcher, heavy TF-IDF on Default.
            // If cache already complete for this hash, load and done
            try {
                val isComplete = withContext(Dispatchers.IO) { XRayCache.isComplete(app, bookId, chapters.size, fileHash) }
                if (isComplete) {
                    val full = withContext(Dispatchers.IO) { XRayCache.load(app, bookId, fileHash) }
                    if (full != null) {
                        _xrayIndex.value = full
                        _isXRayLoading.value = false
                        return@launch
                    }
                }
            } catch (_: Exception) {}
            // Seed with whatever partial cache exists
            try {
                val partial = withContext(Dispatchers.IO) { XRayCache.load(app, bookId, fileHash) }
                if (partial != null && partial.isNotEmpty()) {
                    _xrayIndex.value = partial
                }
            } catch (_: Exception) {}
            // Quick check: if start chapter already cached, don't block UI
            val hasStart = try { withContext(Dispatchers.IO) { XRayCache.loadChapter(app, bookId, startChapter, fileHash) } != null } catch (_: Exception) { false }
            _isXRayLoading.value = !hasStart
            try {
                val stats = try { withContext(Dispatchers.Default) { XRayExtractor.precomputeGlobalStats(chapters) } } catch (e: Exception) {
                    FolioLogger.w("XRay", "Global stats failed for $bookId: ${e.message}", e)
                    null
                } ?: return@launch
                // Order: current, next, then remaining in index order — so reading feels instant
                val order = mutableListOf<Int>()
                order.add(startChapter.coerceIn(0, chapters.size - 1))
                if (startChapter + 1 < chapters.size) order.add(startChapter + 1)
                for (i in chapters.indices) if (i !in order) order.add(i)

                for (chIdx in order) {
                    if (!isActive) return@launch
                    val cached = try { withContext(Dispatchers.IO) { XRayCache.loadChapter(app, bookId, chIdx, fileHash) } } catch (_: Exception) { null }
                    if (cached != null) {
                        val cur = _xrayIndex.value.toMutableMap()
                        cur[chIdx] = cached
                        _xrayIndex.value = cur
                        if (chIdx == startChapter) _isXRayLoading.value = false
                        continue
                    }
                    val terms = try { withContext(Dispatchers.Default) { XRayExtractor.extractChapter(chapters, chIdx, stats, topK = 8) } } catch (e: Exception) {
                        FolioLogger.w("XRay", "Extract ch $chIdx failed for $bookId: ${e.message}", e)
                        emptyList()
                    }
                    withContext(Dispatchers.IO) {
                        try { XRayCache.saveChapter(app, bookId, chIdx, terms, fileHash) } catch (e: Exception) {
                            FolioLogger.w("XRay", "Cache save ch $chIdx failed: ${e.message}", e)
                        }
                    }
                    val cur = _xrayIndex.value.toMutableMap()
                    cur[chIdx] = terms
                    _xrayIndex.value = cur
                    if (chIdx == startChapter) _isXRayLoading.value = false
                    // Yield to keep UI responsive while still prefetching next chapter
                    kotlinx.coroutines.yield()
                }
            } finally {
                _isXRayLoading.value = false
            }
        }
    }

    /** Public priority helper — if user jumps to a new chapter whose X-Ray isn't cached, compute it first. */
    fun prioritizeXRayChapter(chapterIndex: Int) {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty() || chapterIndex !in chapters.indices) return
        val app = getApplication<Application>()
        val fileHash = _uiState.value.fileHash
        viewModelScope.launch {
            // Check cache on IO
            val alreadyCached = try { withContext(Dispatchers.IO) { XRayCache.loadChapter(app, bookId, chapterIndex, fileHash) } != null } catch (_: Exception) { false }
            if (alreadyCached) return@launch
            _isXRayLoading.value = true
            try {
                val stats = withContext(Dispatchers.Default) { XRayExtractor.precomputeGlobalStats(chapters) }
                val terms = withContext(Dispatchers.Default) { XRayExtractor.extractChapter(chapters, chapterIndex, stats, topK = 8) }
                withContext(Dispatchers.IO) { try { XRayCache.saveChapter(app, bookId, chapterIndex, terms, fileHash) } catch (_: Exception) {} }
                val cur = _xrayIndex.value.toMutableMap()
                cur[chapterIndex] = terms
                _xrayIndex.value = cur
            } catch (e: Exception) {
                FolioLogger.w("XRay", "prioritize failed ch $chapterIndex: ${e.message}", e)
            } finally {
                _isXRayLoading.value = false
            }
        }
    }

    /** Pure on-device LCS scan — relocates anchors if the EPUB text shifted. */
    private suspend fun reanchorHighlightsIfNeeded(chapters: List<EpubParser.EpubChapter>) {
        try {
            val highlights = highlightDao.getForBook(bookId)
            if (highlights.isEmpty()) return
            for (hl in highlights) {
                if (hl.anchorText.isBlank()) continue
                val match = LcsAnchor.findBestMatch(hl.anchorText, chapters)
                if (match != null) {
                    if (match.chapterIndex != hl.chapterIndex || hl.isOrphaned) {
                        highlightDao.update(hl.copy(chapterIndex = match.chapterIndex, isOrphaned = false))
                    }
                } else {
                    // No reasonable match — leave orphaned rather than guessing
                    if (!hl.isOrphaned) {
                        highlightDao.update(hl.copy(isOrphaned = true))
                    }
                }
            }
        } catch (e: Exception) {
            FolioLogger.w("LCS", "Re-anchor failed for $bookId: ${e.message}", e)
            // Never crash on anchoring — orphaned highlights are acceptable
        }
    }

    fun saveProgress(chapterIndex: Int, paragraphIndex: Int) {
        viewModelScope.launch {
            try {
                // Use NonCancellable so the save completes even if the ViewModel is being cleared
                // (e.g., user navigates away quickly) — prevents JobCancellationException
                withContext(NonCancellable + Dispatchers.IO) {
                    dao.upsert(
                        ReadingProgress(
                            bookId = bookId,
                            chapterIndex = chapterIndex,
                            paragraphIndex = paragraphIndex,
                            lastReadMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                // ViewModel is being cleared — re-throw to allow cancellation, but DAO already completed due to NonCancellable
                throw e
            } catch (e: Exception) {
                FolioLogger.w("Progress", "saveProgress failed $bookId $chapterIndex/$paragraphIndex: ${e.message}", e)
            }
        }
    }

    fun addHighlight(
        normalizedPoints: List<Offset>,
        pressures: List<Float> = emptyList(),
        tilts: List<Float> = emptyList(),
        chapterIndex: Int = 0,
        anchorText: String = "",
        color: Color = Color(0xFFF7B538),
    ) {
        if (normalizedPoints.size < 2) return
        val anchor = if (anchorText.isNotBlank()) anchorText
        else LcsAnchor.snippetForHighlight(_uiState.value.chapters, chapterIndex)
        viewModelScope.launch {
            try {
                highlightDao.insert(
                    Highlight(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        pointsData = encodePoints(normalizedPoints),
                        pressuresData = if (pressures.size == normalizedPoints.size) encodeFloats(pressures) else "",
                        tiltsData = if (tilts.size == normalizedPoints.size) encodeFloats(tilts) else "",
                        anchorText = anchor.take(LcsAnchor.ANCHOR_SNIPPET_LEN),
                        isOrphaned = false,
                        color = color.toArgb(),
                    ),
                )
            } catch (e: Exception) {
                FolioLogger.w("Highlight", "addHighlight failed $bookId ch=$chapterIndex: ${e.message}", e)
            }
        }
    }

    fun addHighlight(
        normalizedPoints: List<Offset>,
        chapterIndex: Int = 0,
        color: Color = Color(0xFFF7B538),
    ) = addHighlight(normalizedPoints, emptyList(), emptyList(), chapterIndex, "", color)

    /** Also store anchor when available — preferred overload for §5. */
    fun addHighlightWithAnchor(
        normalizedPoints: List<Offset>,
        pressures: List<Float>,
        tilts: List<Float>,
        chapterIndex: Int,
        anchorText: String,
        color: Color = Color(0xFFF7B538),
    ) = addHighlight(normalizedPoints, pressures, tilts, chapterIndex, anchorText, color)

    fun clearHighlights() {
        viewModelScope.launch {
            try { highlightDao.clearForBook(bookId) } catch (e: Exception) {
                FolioLogger.w("Highlight", "clearHighlights failed $bookId: ${e.message}", e)
            }
        }
    }

    // ---- Bookmarks (distinct from highlights) ----

    /**
     * Bookmark just marks a spot — chapter + paragraph position — no text selection
     * or Multiply rendering. Separate entity from Highlight (build on top, don't rewrite).
     */
    fun addBookmark(chapterIndex: Int, paragraphIndex: Int, preview: String = "") {
        val safePreview = preview.take(120)
        viewModelScope.launch {
            try {
                // Avoid duplicates at exact position (idempotent)
                val existing = bookmarkDao.findExact(bookId, chapterIndex, paragraphIndex)
                if (existing != null) return@launch
                val chapters = _uiState.value.chapters
                val autoPreview = if (safePreview.isNotBlank()) safePreview
                else chapters.getOrNull(chapterIndex)?.paragraphs?.getOrNull(paragraphIndex)?.take(120) ?: ""
                bookmarkDao.insert(
                    Bookmark(
                        bookId = bookId,
                        chapterIndex = chapterIndex.coerceAtLeast(0),
                        paragraphIndex = paragraphIndex.coerceAtLeast(0),
                        previewText = autoPreview,
                    ),
                )
            } catch (e: Exception) {
                FolioLogger.w("Bookmark", "addBookmark failed $bookId $chapterIndex/$paragraphIndex: ${e.message}", e)
            }
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            try { bookmarkDao.delete(bookmark) } catch (e: Exception) {
                FolioLogger.w("Bookmark", "removeBookmark failed: ${e.message}", e)
            }
        }
    }

    fun removeBookmark(chapterIndex: Int, paragraphIndex: Int) {
        viewModelScope.launch {
            try {
                val exact = bookmarkDao.findExact(bookId, chapterIndex, paragraphIndex) ?: return@launch
                bookmarkDao.delete(exact)
            } catch (e: Exception) {
                FolioLogger.w("Bookmark", "removeBookmark pos failed: ${e.message}", e)
            }
        }
    }

    fun toggleBookmark(chapterIndex: Int, paragraphIndex: Int) {
        viewModelScope.launch {
            try {
                val exact = bookmarkDao.findExact(bookId, chapterIndex, paragraphIndex)
                if (exact != null) {
                    bookmarkDao.delete(exact)
                } else {
                    val chapters = _uiState.value.chapters
                    val preview = chapters.getOrNull(chapterIndex)?.paragraphs?.getOrNull(paragraphIndex)?.take(120) ?: ""
                    bookmarkDao.insert(
                        Bookmark(
                            bookId = bookId,
                            chapterIndex = chapterIndex.coerceAtLeast(0),
                            paragraphIndex = paragraphIndex.coerceAtLeast(0),
                            previewText = preview,
                        ),
                    )
                }
            } catch (e: Exception) {
                FolioLogger.w("Bookmark", "toggleBookmark failed: ${e.message}", e)
            }
        }
    }

    fun isBookmarked(chapterIndex: Int, paragraphIndex: Int, bookmarks: List<Bookmark>): Boolean =
        bookmarks.any { it.chapterIndex == chapterIndex && it.paragraphIndex == paragraphIndex }

    /** Called when a dictionary lookup succeeds — saves word for SM-2 review (§5). */
    fun trackVocabulary(word: String, definition: String?) {
        if (word.isBlank() || definition.isNullOrBlank()) return
        val key = word.lowercase().trim()
        viewModelScope.launch {
            try {
                val existing = vocabularyDao.getByWord(key)
                if (existing == null) {
                    val card = Sm2.newCard(key, definition)
                    // Keep original casing for display as first-seen word
                    vocabularyDao.upsert(card.copy(word = key, definition = definition))
                } else {
                    // Update definition if improved, but keep SM-2 scheduling
                    if (existing.definition != definition) {
                        vocabularyDao.upsert(existing.copy(definition = definition))
                    }
                }
            } catch (e: Exception) {
                FolioLogger.w("Vocabulary", "trackVocabulary failed '${key.take(30)}': ${e.message}", e)
            }
        }
    }
}
