package com.makemission.folio.ui.library

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.epub.BookFileParser
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.cache.TruePageCache
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.model.ShelfStatus
import com.makemission.folio.data.model.FolioCoverPalette
import com.makemission.folio.data.model.curatedSampleBooks
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.data.search.SearchRepository
import com.makemission.folio.data.work.BookProcessingScheduler
import com.makemission.folio.data.work.DeviceScanScheduler
import com.makemission.folio.data.xray.XRayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import com.makemission.folio.widget.ContinueReadingWidgetProvider

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FolioDatabase.get(application)
    private val bookDao = db.bookDao()
    private val vocabularyDao = db.vocabularyDao()

    private val _importError = MutableSharedFlow<String>(replay = 0)
    val importError = _importError.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    // WorkManager scan state for the Settings progress indicator.
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress.asStateFlow()

    private val _scanResult = MutableSharedFlow<String>(replay = 0)
    val scanResult = _scanResult.asSharedFlow()

    private val scanWork = WorkManager.getInstance(application).getWorkInfosByTagLiveData(DeviceScanScheduler.TAG)
    private val scanObserver = Observer<List<WorkInfo>> { work ->
        val active = work.firstOrNull { it.state == WorkInfo.State.RUNNING }
        _isScanning.value = active != null
        _scanProgress.value = active?.let {
            val total = it.progress.getInt("found", 0)
            val checked = it.progress.getInt("checked", 0)
            val added = it.progress.getInt("added", 0)
            if (total > 0) "Checking $checked/$total · $added added" else "Finding books…"
        }
    }

    val dueVocabularyCount = vocabularyDao.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // --- Pull-down Library search (on-device, highlights/bookmarks priority) ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchRepository.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchRepository.SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        observeLibrarySearch()
        scanWork.observeForever(scanObserver)
    }

    override fun onCleared() {
        scanWork.removeObserver(scanObserver)
        super.onCleared()
    }

    @OptIn(FlowPreview::class)
    private fun observeLibrarySearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(280)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _searchResults.value = emptyList()
                        _isSearching.value = false
                    } else {
                        _isSearching.value = true
                        try {
                            val results = SearchRepository.searchLibrary(q, getApplication<Application>().applicationContext)
                            _searchResults.value = results
                        } catch (e: Exception) {
                            FolioLogger.w("Search", "Library search failed q='${q.take(60)}' ${e.message}", e)
                            _searchResults.value = emptyList()
                        } finally {
                            _isSearching.value = false
                        }
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    /** Imported books + curated samples so the grid is never empty before first import.
     * Sorted recently-read first: most recently opened (ReadingProgress.lastReadMillis) at top,
     * then the rest by addedAt DESC (current default). Never-opened after any recently-read.
     * Adapted from reference app's COALESCE(NULLIF(last_open_date,0),group_added_date) DESC pattern.
     */
    private val readingProgressDao = db.readingProgressDao()
    val readBookIds: StateFlow<Set<String>> = readingProgressDao.observeAll()
        .map { progress -> progress.map { it.bookId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val continueBookId: StateFlow<String?> = readingProgressDao.observeAll()
        .map { progress -> progress.maxByOrNull { it.lastReadMillis }?.bookId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Small local-only return cue for the current Monday–Sunday reading week. */
    val readingWeekDays: StateFlow<Set<DayOfWeek>> = readingProgressDao.observeAll()
        .map { progress ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            progress.mapNotNull { item ->
                runCatching { Instant.ofEpochMilli(item.lastReadMillis).atZone(zone).toLocalDate() }.getOrNull()
            }.filter { !it.isBefore(monday) && !it.isAfter(today) }
                .map { it.dayOfWeek }
                .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val books: StateFlow<List<Book>> = combine(
        bookDao.observeAll(),
        readingProgressDao.observeAll()
    ) { stored, progresses ->
        val progressById = progresses.associateBy { it.bookId }
        val imported = stored.map { e ->
            Book(
                id = e.id,
                title = e.title,
                author = e.author,
                coverColor = pickCoverColor(e.id),
                filePath = e.filePath,
                coverImagePath = e.coverImagePath,
                shelfStatus = ShelfStatus.fromStored(e.shelfStatus),
                progressFraction = progressById[e.id]?.progressFraction ?: 0f,
                lastReadMillis = progressById[e.id]?.lastReadMillis ?: 0L,
            )
        }.sortedWith(
            compareByDescending<Book> { progressById[it.id]?.lastReadMillis ?: 0L }
                .thenByDescending { stored.find { s -> s.id == it.id }?.addedAt ?: 0L }
        )
        // Keep curated sample at end — it has no progress and should not displace real books
        val curated = curatedSampleBooks()
        // If user has no real books, show curated; otherwise curated after imported
        if (imported.isEmpty()) curated else imported + curated
    }.stateIn(viewModelScope, SharingStarted.Eagerly, curatedSampleBooks())

    private fun pickCoverColor(id: String): androidx.compose.ui.graphics.Color {
        val idx = (id.hashCode() and Int.MAX_VALUE) % FolioCoverPalette.size
        return FolioCoverPalette[idx]
    }

    // --- Library grid scroll: fresh-launch reset ---
    // Recently-read sorting changes order on every launch, so a restored scroll position
    // (via rememberSaveable / SavedStateHandle) from a previous session would point at a
    // different offset and often open the grid at the bottom. The flag lives in the
    // ViewModel (survives config change and in-session navigation via backstack, but is
    // reset on process death), so the Library grid scrolls to top exactly once per
    // fresh app launch and otherwise preserves position when navigating to Settings/Insights and back.
    var hasHandledInitialGridScroll: Boolean = false
        private set

    fun markInitialGridScrollHandled() {
        hasHandledInitialGridScroll = true
    }

    // ---- Manual SAF import ----

    fun importEpub(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isImporting.value = true
            FolioLogger.i("Import", "Import started uri=${uri.toString().take(80)}")
            try {
                val result = withContext(Dispatchers.IO) { importInternal(uri, context) }
                if (result == null) {
                    FolioLogger.w("Import", "Import parse failed: uri=${uri.toString().take(80)}")
                    _importError.emit("Couldn't read this book. Check that it is a supported EPUB, MOBI, FB2 or TXT file.")
                } else {
                    FolioLogger.i("Import", "Import success id=${result.id} title='${result.title.take(60)}'")
                }
            } catch (e: Exception) {
                FolioLogger.e("Import", "Import exception: ${e.message}", e)
                _importError.emit(e.message ?: "Failed to import book.")
            } finally {
                _isImporting.value = false
            }
        }
    }

    /** Imports a multi-selection in order and reports one concise result when complete. */
    fun importEpubs(uris: List<Uri>, context: Context) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            var imported = 0
            var failed = 0
            try {
                uris.forEachIndexed { index, uri ->
                    try {
                        val result = withContext(Dispatchers.IO) { importInternal(uri, context) }
                        if (result == null) failed++ else imported++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        failed++
                        FolioLogger.w("Import", "Batch item ${index + 1}/${uris.size} failed: ${e.message}", e)
                    }
                }
                val message = when {
                    imported > 0 && failed > 0 -> "Added $imported books. $failed file${if (failed == 1) "" else "s"} couldn't be read."
                    imported > 0 -> "Added $imported book${if (imported == 1) "" else "s"}."
                    else -> "Couldn't read any of the selected books."
                }
                _scanResult.emit(message)
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

        val cleanName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        val extension = when {
            cleanName.endsWith(".mobi", true) -> ".mobi"
            cleanName.endsWith(".epub", true) -> ".epub"
            cleanName.endsWith(".fb2", true) -> ".fb2"
            cleanName.endsWith(".txt", true) -> ".txt"
            mimeType.contains("mobipocket") -> ".mobi"
            mimeType.contains("fictionbook") -> ".fb2"
            mimeType == "text/plain" -> ".txt"
            else -> ".epub"
        }
        val baseName = if (cleanName.endsWith(extension, true)) cleanName.dropLast(extension.length) else cleanName
        val safeName = baseName.take(90).ifBlank { "book" } + extension
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }

        // Copy to a temp file first so we can parse without yet committing to DB
        val tmpFile = File(booksDir, "tmp_${UUID.randomUUID()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            tmpFile.outputStream().use { out -> input.copyTo(out) }
        } ?: return null

        // Compute hash for deduplication tracking even for SAF imports
        val tmpHash = computeSha256(tmpFile)
        return persistParsedEpub(tmpFile, safeName, context, originalPathOrName = displayName, sourceHash = tmpHash)
    }

    /**
     * Shared import pipeline — MINIMAL at import time (copy + title/author/cover for grid).
     * Does NOT run full X-Ray extraction or heavy indexing here. Those are scheduled
     * via WorkManager to run in the background (chapter-by-chapter) so import feels instant.
     * EPUB title/author and cover are read directly from ZIP entries without parsing chapters.
     */
    private suspend fun persistParsedEpub(
        tmpFile: File,
        safeName: String,
        context: Context,
        originalPathOrName: String,
        sourceHash: String?,
    ): BookEntity? {
        // Quick validation: ensure file looks like a ZIP/EPUB (has OPF) via lightweight metadata
        // We still need a title — use fast metadata extraction, not full chapter parse
        val metadata = BookFileParser.metadata(tmpFile)
        // Fallback: if metadata extraction fails (no OPF), try quick full parse as validation
        // but only to check emptiness; heavy X-Ray etc is still deferred.
        val resolvedMetadata = metadata ?: run {
            // Last resort: attempt full parse to get title/author, but this is still lighter than X-Ray
            val epub = BookFileParser.parse(tmpFile)
            if (epub == null || epub.chapters.isEmpty()) {
                tmpFile.delete()
                return null
            }
            EpubParser.EpubMetadata(title = epub.title, author = epub.author)
        }
        if (tmpFile.extension.lowercase() in setOf("mobi", "fb2", "txt") && BookFileParser.parse(tmpFile) == null) {
            tmpFile.delete()
            return null
        }
        val title = resolvedMetadata.title.ifBlank { safeName.substringBeforeLast('.') }
        val author = resolvedMetadata.author

        // Check for re-import: same title already exists → reuse its id so highlights can be LCS-reanchored
        val existing = try {
            bookDao.getAll().firstOrNull { it.title.equals(title, ignoreCase = true) }
        } catch (_: Exception) { null }

        val id = existing?.id ?: UUID.randomUUID().toString()
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val destFile = File(booksDir, "${id}_$safeName")

        // Move temp to final (or copy if reimport keeps same id but different name)
        if (tmpFile.absolutePath != destFile.absolutePath) {
            if (destFile.exists()) destFile.delete()
            tmpFile.renameTo(destFile)
            if (!destFile.exists()) {
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
        } else {
            // tmp is already at dest? Ensure parent
        }
        // If reimport, clean up old cover to force regeneration
        if (existing?.coverImagePath != null) {
            try { File(existing.coverImagePath).delete() } catch (_: Exception) {}
        }

        // Compute final hash from destFile if not already computed from tmp (e.g., after move)
        val finalHash = sourceHash ?: computeSha256(destFile)

        // Extract cover image if present (saved under covers/<id>.jpg) — lightweight, needed for grid
        val coverPath = if (destFile.extension.equals("epub", true)) EpubParser.extractCoverToFile(destFile, context, id) else null

        // If this was a reimport, delete old file if path changed (we already moved)
        if (existing != null && existing.filePath != destFile.absolutePath) {
            try { File(existing.filePath).delete() } catch (_: Exception) {}
        }

        val entity = BookEntity(
            id = id,
            title = title,
            author = author,
            filePath = destFile.absolutePath,
            coverImagePath = coverPath,
            fileHash = finalHash,
            importedFromPath = originalPathOrName,
            shelfStatus = existing?.shelfStatus ?: ShelfStatus.AUTO.name,
        )
        withContext(Dispatchers.IO) {
            bookDao.insert(entity)
            // Invalidate caches (X-Ray, parsed, true-page) so they recompute for new/updated file — off main thread
            try { XRayCache.invalidate(context, id) } catch (_: Exception) {}
            try { ParsedBookCache.invalidate(context, id) } catch (_: Exception) {}
            try { TruePageCache.invalidate(context, id) } catch (_: Exception) {}
        }
        ContinueReadingWidgetProvider.refresh(context)
        // Schedule background heavy processing (X-Ray chapter-by-chapter, full parse caching) — soon but not blocking
        try { BookProcessingScheduler.schedule(context, id, finalHash) } catch (_: Exception) {}
        return entity
    }

    // ---- Device auto-scan ----

    fun hasStoragePermission(context: Context): Boolean = EpubScanner.hasStoragePermission(context)

    /** Queues a shared-storage scan for the next battery-safe idle period. */
    fun scanDevice(context: Context) {
        viewModelScope.launch {
            if (!EpubScanner.hasStoragePermission(context)) {
                _scanResult.emit("Allow all-files access, or choose a books folder, to scan books.")
                return@launch
            }
            DeviceScanScheduler.requestScan(context, manual = true)
            _scanResult.emit("EPUB, MOBI and FB2 scan queued for when your device is idle.")
        }
    }

    /** Library long-press actions — sensible given existing data, no new feature logic. */
    fun isCuratedBook(book: Book): Boolean =
        curatedSampleBooks().any { it.id == book.id }

    fun removeBook(book: Book, context: Context) {
        if (isCuratedBook(book)) {
            viewModelScope.launch { _scanResult.emit("Curated sample — can't be removed") }
            return
        }
        viewModelScope.launch {
            try {
                val entity = withContext(Dispatchers.IO) { try { bookDao.getById(book.id) } catch (_: Exception) { null } }
                // Delete private files (book file + cover) if present
                entity?.filePath?.let { try { File(it).delete() } catch (_: Exception) {} }
                entity?.coverImagePath?.let { try { File(it).delete() } catch (_: Exception) {} }
                withContext(Dispatchers.IO) {
                    try { bookDao.delete(book.id) } catch (_: Exception) {}
                    try { db.readingProgressDao().clear(book.id) } catch (_: Exception) {}
                    try { db.highlightDao().clearForBook(book.id) } catch (_: Exception) {}
                    try { db.bookmarkDao().clearForBook(book.id) } catch (_: Exception) {}
                    try { XRayCache.invalidate(context, book.id) } catch (_: Exception) {}
                    try { ParsedBookCache.invalidate(context, book.id) } catch (_: Exception) {}
                    try { TruePageCache.invalidate(context, book.id) } catch (_: Exception) {}
                    try { BookProcessingScheduler.cancel(context, book.id) } catch (_: Exception) {}
                }
                ContinueReadingWidgetProvider.refresh(context, force = true)
                FolioLogger.i("Library", "Removed book ${book.id} ${book.title.take(60)}")
                _scanResult.emit("Removed \"${book.title}\"")
            } catch (e: Exception) {
                FolioLogger.w("Library", "Remove failed ${book.id}: ${e.message}", e)
                _scanResult.emit("Could not remove book")
            }
        }
    }

    fun resetProgress(bookId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { db.readingProgressDao().clear(bookId) }
                FolioLogger.i("Library", "Reset progress $bookId")
                _scanResult.emit("Progress reset")
            } catch (e: Exception) {
                FolioLogger.w("Library", "Reset progress failed $bookId: ${e.message}", e)
                _scanResult.emit("Could not reset progress")
            }
        }
    }

    fun setShelfStatus(book: Book, status: ShelfStatus) {
        if (isCuratedBook(book)) return
        viewModelScope.launch {
            if (updateShelfStatus(book, status)) {
                _scanResult.emit(when (status) {
                    ShelfStatus.WANT -> "Moved to Want to Read"
                    ShelfStatus.FINISHED -> "Marked Finished"
                    ShelfStatus.AUTO -> "Moved back to All Books"
                })
            } else {
                _scanResult.emit("Could not update book status")
            }
        }
    }

    /** Apply a shelf change without a transient message so the screen can offer Undo. */
    suspend fun updateShelfStatus(book: Book, status: ShelfStatus): Boolean {
        if (isCuratedBook(book)) return false
        return try {
            withContext(Dispatchers.IO) { bookDao.setShelfStatus(book.id, status.name) }
            ContinueReadingWidgetProvider.refresh(getApplication<Application>(), force = true)
            true
        } catch (e: Exception) {
            FolioLogger.w("Library", "Could not update shelf for ${book.id}: ${e.message}", e)
            false
        }
    }

    private fun computeSha256(file: File): String? {
        return try {
            if (!file.exists() || !file.isFile) return null
            file.inputStream().use { computeSha256FromStream(it) }
        } catch (_: Exception) { null }
    }

    private fun computeSha256(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { computeSha256FromStream(it) }
        } catch (_: Exception) { null }
    }

    private fun computeSha256FromStream(input: InputStream): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }
}
