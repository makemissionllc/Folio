package com.makemission.folio.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.makemission.folio.data.anchor.LcsAnchor
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.db.entity.ReadingProgress
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.ui.reader.components.encodeFloats
import com.makemission.folio.ui.reader.components.encodePoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        viewModelScope.launch {
            val saved = dao.observe(bookId).firstOrNull()
            // Try to load the imported book's private file first (SAF copy), then assets, then fallback
            val app = getApplication<Application>()
            val stored = try { db.bookDao().getById(bookId) } catch (_: Exception) { null }
            val epub = when {
                stored?.filePath != null -> {
                    val f = java.io.File(stored.filePath)
                    if (f.exists() && f.canRead()) EpubParser.parse(f) else null
                }
                else -> null
            } ?: EpubParser.loadFromAssetsOrNull(app) 
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

            // LCS re-anchoring (§5): if the EPUB file changed, relocate highlights
            reanchorHighlightsIfNeeded(chapters)
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
        } catch (_: Exception) {
            // Never crash on anchoring — orphaned highlights are acceptable
        }
    }

    fun saveProgress(chapterIndex: Int, paragraphIndex: Int) {
        viewModelScope.launch {
            dao.upsert(
                ReadingProgress(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    paragraphIndex = paragraphIndex,
                    lastReadMillis = System.currentTimeMillis(),
                ),
            )
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
        viewModelScope.launch { highlightDao.clearForBook(bookId) }
    }
}
