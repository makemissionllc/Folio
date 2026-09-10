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
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.vocabulary.Sm2
import com.makemission.folio.data.xray.XRayCache
import com.makemission.folio.data.xray.XRayExtractor
import com.makemission.folio.data.xray.XRayTerm
import com.makemission.folio.data.search.SearchRepository
import com.makemission.folio.ui.reader.components.encodeFloats
import com.makemission.folio.ui.reader.components.encodePoints
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReadingUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val chapters: List<EpubParser.EpubChapter> = emptyList(),
    val isLoading: Boolean = true,
    val restoredChapterIndex: Int = 0,
    val restoredParagraphIndex: Int = 0,
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
            // Try to load the imported book's private file first (SAF copy), then assets, then fallback
            val app = getApplication<Application>()
            val stored = try { db.bookDao().getById(bookId) } catch (e: Exception) {
                FolioLogger.w("Reading", "Failed to lookup book $bookId: ${e.message}", e)
                null
            }
            val epub = when {
                stored?.filePath != null -> {
                    val f = java.io.File(stored.filePath)
                    if (f.exists() && f.canRead()) EpubParser.parse(f) else {
                        FolioLogger.w("Reading", "Stored file missing/unreadable for $bookId: ${stored.filePath?.take(80)}")
                        null
                    }
                }
                else -> null
            } ?: EpubParser.loadFromAssetsOrNull(app)
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
            )

            // X-Ray TF-IDF (§5): compute once per book, cache
            launchXRayIfNeeded(app, bookId, chapters)

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

    private fun launchXRayIfNeeded(
        app: Application,
        bookId: String,
        chapters: List<EpubParser.EpubChapter>,
    ) {
        viewModelScope.launch {
            _isXRayLoading.value = true
            try {
                // Try disk cache first (computed on import/first open)
                val cached = try { XRayCache.load(app, bookId) } catch (e: Exception) {
                    FolioLogger.w("XRay", "Cache load failed for $bookId: ${e.message}", e)
                    null
                }
                if (cached != null && cached.isNotEmpty()) {
                    _xrayIndex.value = cached
                    return@launch
                }
                // Compute locally, deterministic, no network/dictionary
                val index = try { XRayExtractor.extract(chapters, topK = 8) } catch (e: Exception) {
                    FolioLogger.w("XRay", "Extract failed for $bookId: ${e.message}", e)
                    emptyMap()
                }
                _xrayIndex.value = index
                try { XRayCache.save(app, bookId, index) } catch (e: Exception) {
                    FolioLogger.w("XRay", "Cache save failed for $bookId: ${e.message}", e)
                }
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
                dao.upsert(
                    ReadingProgress(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        paragraphIndex = paragraphIndex,
                        lastReadMillis = System.currentTimeMillis(),
                    ),
                )
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
