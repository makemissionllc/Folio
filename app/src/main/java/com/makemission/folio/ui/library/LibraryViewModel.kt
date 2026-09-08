package com.makemission.folio.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.model.FolioCoverPalette
import com.makemission.folio.data.model.curatedSampleBooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FolioDatabase.get(application)
    private val bookDao = db.bookDao()

    private val _importError = MutableSharedFlow<String>(replay = 0)
    val importError = _importError.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    /** Imported books + curated samples so the grid is never empty before first import. */
    val books: StateFlow<List<Book>> = bookDao.observeAll()
        .map { stored ->
            val imported = stored.map { e ->
                Book(
                    id = e.id,
                    title = e.title,
                    author = e.author,
                    coverColor = pickCoverColor(e.id),
                    filePath = e.filePath,
                    coverImagePath = e.coverImagePath,
                )
            }
            imported + curatedSampleBooks()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun pickCoverColor(id: String): androidx.compose.ui.graphics.Color {
        val idx = (id.hashCode() and Int.MAX_VALUE) % FolioCoverPalette.size
        return FolioCoverPalette[idx]
    }

    fun importEpub(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val result = withContext(Dispatchers.IO) { importInternal(uri, context) }
                if (result == null) {
                    _importError.emit("Could not parse EPUB — file may be corrupted or not an EPUB.")
                }
            } catch (e: Exception) {
                _importError.emit(e.message ?: "Failed to import EPUB.")
            } finally {
                _isImporting.value = false
            }
        }
    }

    private suspend fun importInternal(uri: Uri, context: Context): BookEntity? {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        } ?: "book_${System.currentTimeMillis()}.epub"

        val safeName = displayName.takeIf { it.endsWith(".epub", ignoreCase = true) } ?: "$displayName.epub"
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val destFile = File(booksDir, "${id}_$safeName")

        resolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { out -> input.copyTo(out) }
        } ?: return null

        // Validate by parsing — also gives us title/author
        val epub = EpubParser.parse(destFile)
        if (epub == null || epub.chapters.isEmpty()) {
            destFile.delete()
            return null
        }
        val title = epub.title.ifBlank { safeName.removeSuffix(".epub") }
        val author = epub.author

        // Extract cover image if present (saved under covers/<id>.jpg)
        val coverPath = EpubParser.extractCoverToFile(destFile, context, id)

        val entity = BookEntity(
            id = id,
            title = title,
            author = author,
            filePath = destFile.absolutePath,
            coverImagePath = coverPath,
        )
        bookDao.insert(entity)
        return entity
    }
}
