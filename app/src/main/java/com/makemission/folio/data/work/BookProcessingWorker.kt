package com.makemission.folio.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.xray.XRayCache
import com.makemission.folio.data.xray.XRayExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker for heavy book processing — scheduled after import/scan.
 *
 * Does NOT run on import thread. Instead, WorkManager runs this when the
 * device can (battery not low, soon after import). Heavy work (full parse,
 * X-Ray TF-IDF per-chapter) runs here, chapter-by-chapter, with caching.
 *
 * - Minimum import (copy + title/author/cover) already done; this does the rest.
 * - Chapter-level progressive: each chapter's X-Ray is computed and cached individually
 *   via [XRayCache.saveChapter], so reading the current chapter can be prioritized.
 * - Smart caching: parsed chapters + X-Ray per chapter are cached to disk with
 *   fileHash validation — reopening doesn't reparse if hash unchanged (reuses dedup).
 * - Reuses existing algorithms (TF-IDF unchanged) — only when/order changes.
 *
 * Constraints: scheduled with battery-not-low so it completes soon, but also
 * benefits from idle/charging when available (WorkManager batches). An expedited
 * flag ensures the first chapters are ready quickly even if device not idle.
 */
class BookProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Concurrency limit: throttle many books selected via SAF (50+). Without this,
        // WorkManager would start dozens of BookProcessingWorkers within ms (log: "Start processing"
        // back-to-back), each holding full EPUB bytes + X-Ray TF-IDF in memory -> OOM / file contention.
        // Semaphore with 2 permits ensures at most 2 books parse+X-Ray concurrently, rest queue.
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

            // 1) Parsed text cache — full parse only if not already cached for this hash
            var book = ParsedBookCache.load(applicationContext, bookId, entity.fileHash ?: fileHash)
            if (book == null) {
                FolioLogger.i("BookWorker", "Parsing full book for cache bookId=$bookId")
                val parsed = EpubParser.parse(file)
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

            // 2) X-Ray chapter-by-chapter — skip already cached chapters
            // Precompute global stats once (lightweight df scan), then per chapter
            val globalStats = XRayExtractor.precomputeGlobalStats(chapters)
            var processed = 0
            for (chIdx in chapters.indices) {
                // Cooperative cancellation
                if (isStopped) {
                    FolioLogger.i("BookWorker", "Stopped mid-loop at $chIdx for $bookId")
                    return@withContext Result.success()
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

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_FILE_HASH = "fileHash"
        const val KEY_PROGRESS = "progress"
        // Throttle concurrent heavy parsing: at most 2 books parse+X-Ray at once.
        // Prevents OOM / file-race when user picks 50+ books via SAF.
        private val processingSemaphore = Semaphore(2)
    }
}
