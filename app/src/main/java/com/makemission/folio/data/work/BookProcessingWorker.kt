package com.makemission.folio.data.work

import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.epub.BookFileParser
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.xray.XRayCache
import com.makemission.folio.data.xray.XRayExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for heavy book processing — scheduled after import/scan for quiet periods.
 *
 * Does NOT run on the import thread. WorkManager waits until the device is idle,
 * battery is healthy and storage is available. Full parsing and chapter-by-chapter
 * X-Ray TF-IDF are cached so each book is prepared only when necessary.
 *
 * - Minimum import (copy + title/author/cover) already done; this does the rest.
 * - Chapter-level progressive: each chapter's X-Ray is computed and cached individually
 *   via [XRayCache.saveChapter], so reading the current chapter can be prioritized.
 * - Smart caching: parsed chapters + X-Ray per chapter are cached to disk with
 *   fileHash validation — reopening doesn't reparse if hash unchanged (reuses dedup).
 * - Reuses existing algorithms (TF-IDF unchanged) — only when/order changes.
 *
 * Thermal-aware: one worker processes one book at a time and pauses when Android
 * reports moderate-or-higher thermal pressure.
 */
class BookProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // WorkManager may enqueue several workers after a multi-file import; serialize the expensive
        // parse and X-Ray steps so only one book can consume substantial CPU at a time.
        processingSemaphore.withPermit {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return@withContext Result.failure()
        val fileHash = inputData.getString(KEY_FILE_HASH)
        FolioLogger.i("BookWorker", "Start processing bookId=$bookId hash=${fileHash?.take(8)}")
        try {
            val db = FolioDatabase.get(applicationContext)
            val entity = try { db.bookDao().getById(bookId) } catch (e: Exception) {
                FolioLogger.w("BookWorker", "Book not found $bookId: ${e.message}", e)
                return@withContext Result.failure()
            } ?: return@withContext Result.failure()

            val file = File(entity.filePath)
            if (!file.exists()) {
                FolioLogger.w("BookWorker", "File missing for $bookId: ${entity.filePath}")
                return@withContext Result.failure()
            }

            if (currentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE) return@withContext Result.retry()

            // 1) Parsed text cache — full parse only if not already cached for this hash
            var book = ParsedBookCache.load(applicationContext, bookId, entity.fileHash ?: fileHash)
            if (book == null) {
                FolioLogger.i("BookWorker", "Parsing full book for cache bookId=$bookId")
                val parsed = BookFileParser.parse(file)
                if (parsed != null && parsed.chapters.isNotEmpty()) {
                    ParsedBookCache.save(applicationContext, bookId, entity.fileHash ?: fileHash, parsed)
                    book = parsed
                } else {
                    FolioLogger.w("BookWorker", "Parse failed for $bookId")
                    return@withContext Result.failure()
                }
            } else {
                FolioLogger.i("BookWorker", "Parsed cache hit for $bookId")
            }

            val chapters = book.chapters
            if (chapters.isEmpty()) return@withContext Result.success()
            if (currentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE) {
                FolioLogger.i("BookWorker", "Pausing warm device before chapter indexing for $bookId")
                return@withContext Result.retry()
            }

            // 2) X-Ray chapter-by-chapter — skip already cached chapters
            // Precompute global stats once (lightweight df scan), then per chapter
            val globalStats = XRayExtractor.precomputeGlobalStats(chapters)
            if (currentThermalStatus() >= PowerManager.THERMAL_STATUS_MODERATE) {
                FolioLogger.i("BookWorker", "Pausing warm device after index preparation for $bookId")
                return@withContext Result.retry()
            }
            var processed = 0
            for (chIdx in chapters.indices) {
                // Cooperative cancellation
                if (isStopped) {
                    FolioLogger.i("BookWorker", "Stopped mid-loop at $chIdx for $bookId")
                    return@withContext Result.retry()
                }
                val thermal = currentThermalStatus()
                if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
                    FolioLogger.i("BookWorker", "Pausing warm device at chapter $chIdx for $bookId")
                    return@withContext Result.retry()
                }
                // Skip if already cached for this hash
                val existing = XRayCache.loadChapter(applicationContext, bookId, chIdx, entity.fileHash ?: fileHash)
                if (existing != null) continue
                val terms = XRayExtractor.extractChapter(chapters, chIdx, globalStats, topK = 8)
                XRayCache.saveChapter(applicationContext, bookId, chIdx, terms, fileHash = entity.fileHash ?: fileHash)
                processed++
                // Progress for WorkManager (optional)
                setProgress(workDataOf(KEY_PROGRESS to processed))
                FolioLogger.i("BookWorker", "Cached X-Ray ch $chIdx for $bookId (${terms.size} terms)")
            }

            FolioLogger.i("BookWorker", "Done bookId=$bookId processed=$processed total=${chapters.size}")
            Result.success()
        } catch (e: OutOfMemoryError) {
            FolioLogger.w("BookWorker", "OOM bookId=$bookId: ${e.message}", e)
            System.gc()
            Result.failure()
        } catch (e: Exception) {
            FolioLogger.w("BookWorker", "Failed bookId=$bookId: ${e.message}", e)
            Result.retry()
        } catch (e: Throwable) {
            FolioLogger.w("BookWorker", "Failed throwable bookId=$bookId: ${e.message}", e)
            Result.retry()
        }
        }
    }

    private fun currentThermalStatus(): Int =
        applicationContext.getSystemService(PowerManager::class.java)?.currentThermalStatus
            ?: PowerManager.THERMAL_STATUS_NONE

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_FILE_HASH = "fileHash"
        const val KEY_PROGRESS = "progress"
        // Throttle heavy parsing and X-Ray extraction to one book at a time.
        private val processingSemaphore = Semaphore(1)
    }
}
