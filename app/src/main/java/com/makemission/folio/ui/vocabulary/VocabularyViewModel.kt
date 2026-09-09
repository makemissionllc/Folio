package com.makemission.folio.ui.vocabulary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.VocabularyCard
import com.makemission.folio.data.vocabulary.Sm2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VocabularyViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = FolioDatabase.get(application).vocabularyDao()

    val allCards: StateFlow<List<VocabularyCard>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCards: StateFlow<List<VocabularyCard>> =
        dao.observeDue().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueCount = dao.observeDueCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _currentReview = MutableStateFlow<VocabularyCard?>(null)
    val currentReview: StateFlow<VocabularyCard?> = _currentReview.asStateFlow()

    private val _showDefinition = MutableStateFlow(false)
    val showDefinition: StateFlow<Boolean> = _showDefinition.asStateFlow()

    fun startReview() {
        viewModelScope.launch {
            val due = dao.getDue()
            _currentReview.value = due.firstOrNull()
            _showDefinition.value = false
        }
    }

    fun revealDefinition() {
        _showDefinition.value = true
    }

    fun rateCurrent(quality: Int) {
        val card = _currentReview.value ?: return
        viewModelScope.launch {
            val updated = Sm2.schedule(card, quality)
            dao.upsert(updated)
            val nextDue = dao.getDue().firstOrNull { it.word != card.word }
            _currentReview.value = nextDue
            _showDefinition.value = false
        }
    }

    fun dismissReview() {
        _currentReview.value = null
        _showDefinition.value = false
    }
}
