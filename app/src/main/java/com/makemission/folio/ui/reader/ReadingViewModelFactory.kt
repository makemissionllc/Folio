package com.makemission.folio.ui.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ReadingViewModelFactory(
    private val application: Application,
    private val bookId: String,
    private val bookTitle: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReadingViewModel::class.java)) {
            return ReadingViewModel(application, bookId, bookTitle) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
