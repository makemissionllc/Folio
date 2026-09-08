package com.makemission.folio.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.ReadingProgress
import com.makemission.folio.data.epub.EpubParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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

    private val _uiState = MutableStateFlow(
        ReadingUiState(bookId = bookId, bookTitle = bookTitle, isLoading = true),
    )
    val uiState: StateFlow<ReadingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = dao.observe(bookId).firstOrNull()
            val epub = EpubParser.loadFromAssetsOrNull(getApplication())
            val chapters = epub?.chapters ?: EpubParser.sampleFallbackChapters(bookTitle)

            // Use stored progress if valid
            val chapterIdx = saved?.chapterIndex?.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)) ?: 0
            val paraIdx = saved?.paragraphIndex ?: 0

            _uiState.value = ReadingUiState(
                bookId = bookId,
                bookTitle = if (epub?.title?.isNotBlank() == true) epub.title else bookTitle,
                chapters = chapters,
                isLoading = false,
                restoredChapterIndex = chapterIdx,
                restoredParagraphIndex = paraIdx,
            )
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
}
