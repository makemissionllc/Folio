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
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.cache.TruePageCache
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.model.FolioCoverPalette
import com.makemission.folio.data.model.curatedSampleBooks
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.data.search.SearchRepository
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.data.work.BookProcessingScheduler
import com.makemission.folio.data.xray.XRayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FolioDatabase.get(application)
    private val bookDao = db.bookDao()
    private val vocabularyDao = db.vocabularyDao()
    private val settingsRepo = SettingsRepository.get(application)

    private val _importError = MutableSharedFlow<String>(replay = 0)
    val importError = _importError.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    // --- Auto-scan state (subtle loading on Library screen, non-blocking) ---
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress.asStateFlow()

    private val _scanResult = MutableSharedFlow<String>(replay = 0)
    val scanResult = _scanResult.asSharedFlow()

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, curatedSampleBooks())

    private fun pickCoverColor(id: String): androidx.compose.ui.graphics.Color {
        val idx = (id.hashCode() and Int.MAX_VALUE) % FolioCoverPalette.size
        return FolioCoverPalette[idx]
    }

    // ---- Manual SAF import (unchanged flow, now with hash tracking) ----

    fun importEpub(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isImporting.value = true
            FolioLogger.i("Import", "Import started uri=${uri.toString().take(80)}")
            try {
                val result = withContext(Dispatchers.IO) { importInternal(uri, context) }
                if (result == null) {
                    FolioLogger.w("Import", "Import parse failed — not an EPUB or corrupted: uri=${uri.toString().take(80)}")
                    _importError.emit("Could not parse EPUB — file may be corrupted or not an EPUB.")
                } else {
                    FolioLogger.i("Import", "Import success id=${result.id} title='${result.title.take(60)}'")
                }
            } catch (e: Exception) {
                FolioLogger.e("Import", "Import exception: ${e.message}", e)
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
     * Title/author are extracted via lightweight OPF metadata parse (no full chapter HTMl parse).
     * Reused by both manual SAF import and device auto-scan.
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
        val metadata = EpubParser.extractMetadata(tmpFile)
        // Fallback: if metadata extraction fails (no OPF), try quick full parse as validation
        // but only to check emptiness; heavy X-Ray etc is still deferred.
        val resolvedMetadata = metadata ?: run {
            // Last resort: attempt full parse to get title/author, but this is still lighter than X-Ray
            val epub = EpubParser.parse(tmpFile)
            if (epub == null || epub.chapters.isEmpty()) {
                tmpFile.delete()
                return null
            }
            EpubParser.EpubMetadata(title = epub.title, author = epub.author)
        }
        val title = resolvedMetadata.title.ifBlank { safeName.removeSuffix(".epub") }
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
        val coverPath = EpubParser.extractCoverToFile(destFile, context, id)

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
        )
        withContext(Dispatchers.IO) {
            bookDao.insert(entity)
            // Invalidate caches (X-Ray, parsed, true-page) so they recompute for new/updated file — off main thread
            try { XRayCache.invalidate(context, id) } catch (_: Exception) {}
            try { ParsedBookCache.invalidate(context, id) } catch (_: Exception) {}
            try { TruePageCache.invalidate(context, id) } catch (_: Exception) {}
        }
        // Schedule background heavy processing (X-Ray chapter-by-chapter, full parse caching) — soon but not blocking
        try { BookProcessingScheduler.schedule(context, id, finalHash) } catch (_: Exception) {}
        return entity
    }

    // ---- Device auto-scan ----

    fun hasStoragePermission(context: Context): Boolean = EpubScanner.hasStoragePermission(context)

    /**
     * Scan device storage for EPUBs (Downloads, Documents, general external storage, and SAF folder grant)
     * and import any not already in the library. Uses [EpubScanner.scan] and reuses [persistParsedEpub] so
     * the private-storage copy + EpubParser + Room path is identical to manual import (don't duplicate logic).
     * Skips duplicates by file content hash or filename/path tracking. Runs off the UI thread and updates
     * [isScanning]/[scanProgress]. If permission denied and no SAF grant, does nothing.
     */
    fun scanDevice(context: Context) {
        if (_isScanning.value) return
        viewModelScope.launch {
            FolioLogger.i("Scan", "scanDevice started")
            if (!EpubScanner.hasStoragePermission(context)) {
                FolioLogger.w("Scan", "scanDevice permission denied")
                _scanResult.emit("Storage permission or books folder grant required — auto-scan unavailable.")
                return@launch
            }
            _isScanning.value = true
            _scanProgress.value = "Scanning..."
            try {
                val configuredFolder = settingsRepo.booksFolderUri.firstOrNull()?.let {
                    try { Uri.parse(it) } catch (_: Exception) { null }
                }
                val scanResult = withContext(Dispatchers.IO) {
                    EpubScanner.scan(context, configuredFolder)
                }
                val found = scanResult.items
                val deepFoldersSkipped = scanResult.deepFoldersSkipped
                val tooDeepSuffix = if (deepFoldersSkipped > 0) {
                    val folderWord = if (deepFoldersSkipped == 1) "1 folder was" else "$deepFoldersSkipped folders were"
                    " ($folderWord too deep to search)"
                } else ""

                if (found.isEmpty()) {
                    _scanProgress.value = null
                    _scanResult.emit("No EPUB files found on device$tooDeepSuffix.")
                    return@launch
                }
                // Load existing for deduplication (hash + path)
                val existing = withContext(Dispatchers.IO) { try { bookDao.getAll() } catch (_: Exception) { emptyList() } }
                // Build existing hash set (compute missing legacy hashes lazily)
                val existingHashes = mutableSetOf<String>()
                val existingPaths = mutableSetOf<String>()
                for (e in existing) {
                    e.fileHash?.let { existingHashes.add(it) }
                    e.importedFromPath?.let { existingPaths.add(it) }
                    // Also add filename fallback
                    try { existingPaths.add(File(e.filePath).name) } catch (_: Exception) {}
                    // Legacy rows with null hash: compute from private file for accurate dedup
                    if (e.fileHash == null) {
                        try {
                            val pf = File(e.filePath)
                            if (pf.exists() && pf.isFile) {
                                computeSha256(pf)?.let { existingHashes.add(it) }
                            }
                        } catch (_: Exception) {}
                    }
                }

                var imported = 0
                var skipped = 0
                for ((idx, item) in found.withIndex()) {
                    _scanProgress.value = "Scanning ${idx + 1}/${found.size} · ${imported} new"
                    // --- Deduplication: path / name / hash ---
                    val isPathDuplicate = existingPaths.contains(item.originalPathOrUri) ||
                            existingPaths.contains(item.displayName)

                    val foundHash = if (!isPathDuplicate) {
                        withContext(Dispatchers.IO) {
                            item.file?.let { computeSha256(it) } ?: item.uri?.let { computeSha256(context, it) }
                        }
                    } else null

                    val isDuplicate = when {
                        isPathDuplicate -> true
                        foundHash != null && existingHashes.contains(foundHash) -> true
                        else -> false
                    }
                    if (isDuplicate) {
                        skipped++
                        continue
                    }
                    // Copy to private storage temp and reuse import pipeline
                    val safeName = item.displayName.takeIf { it.endsWith(".epub", ignoreCase = true) } ?: "${item.displayName}.epub"
                    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                    val tmpScan = File(booksDir, "scan_tmp_${UUID.randomUUID()}_$safeName")
                    try {
                        withContext(Dispatchers.IO) {
                            if (item.file != null) {
                                item.file.copyTo(tmpScan, overwrite = true)
                            } else if (item.uri != null) {
                                context.contentResolver.openInputStream(item.uri)?.use { input ->
                                    tmpScan.outputStream().use { outStream -> input.copyTo(outStream) }
                                } ?: throw IllegalStateException("Cannot read stream for ${item.uri}")
                            } else {
                                throw IllegalStateException("Both file and uri are null for ${item.displayName}")
                            }
                        }
                    } catch (e: Exception) {
                        FolioLogger.w("Scan", "Failed to copy ${item.displayName}: ${e.message}", e)
                        try { tmpScan.delete() } catch (_: Exception) {}
                        continue
                    }
                    // Reuse shared pipeline (same private copy + EpubParser + Room as manual import)
                    val result = withContext(Dispatchers.IO) {
                        persistParsedEpub(tmpScan, safeName, context, originalPathOrName = item.originalPathOrUri, sourceHash = foundHash)
                    }
                    if (result != null) {
                        foundHash?.let { existingHashes.add(it) }
                        existingPaths.add(item.originalPathOrUri)
                        existingPaths.add(item.displayName)
                        imported++
                    } else {
                        // Parse failed or empty
                        skipped++
                    }
                    // Cooperative yield to keep UI responsive
                    if (imported + skipped >= 80) break
                }
                _scanProgress.value = null
                FolioLogger.i("Scan", "Scan done found=${found.size} imported=$imported skipped=$skipped deepFoldersSkipped=$deepFoldersSkipped")
                when {
                    imported > 0 -> _scanResult.emit("Scan complete: $imported new book(s) added${if (skipped > 0) ", $skipped already in library" else ""}$tooDeepSuffix.")
                    else -> _scanResult.emit("Scan complete: no new books (${found.size} found, all already imported)$tooDeepSuffix.")
                }
            } catch (e: Exception) {
                FolioLogger.e("Scan", "Scan failed: ${e.message}", e)
                _scanProgress.value = null
                _scanResult.emit(e.message ?: "Scan failed.")
            } finally {
                _isScanning.value = false
                _scanProgress.value = null
            }
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
