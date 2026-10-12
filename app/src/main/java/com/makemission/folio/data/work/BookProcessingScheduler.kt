package com.makemission.folio.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules background processing for a book after minimal import.
 *
 * - Keeps heavy parsing and chapter enrichment out of an active reading session.
 * - WorkManager runs it only while the device is idle, the battery is healthy and storage is available.
 * - The worker pauses when Android reports moderate-or-higher thermal pressure and resumes later.
 */
object BookProcessingScheduler {

    private fun quietDeviceConstraints() = Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    fun schedule(context: Context, bookId: String, fileHash: String?) {
        val request = OneTimeWorkRequestBuilder<BookProcessingWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    BookProcessingWorker.KEY_BOOK_ID to bookId,
                    BookProcessingWorker.KEY_FILE_HASH to fileHash,
                )
            )
            .setConstraints(quietDeviceConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag("folio_book_$bookId")
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "folio_process_$bookId",
            ExistingWorkPolicy.KEEP, // don't re-enqueue if already processing that book
            request,
        )

        try {
            com.makemission.folio.data.logging.FolioLogger.i("BookWorker", "Scheduled processing for $bookId")
        } catch (_: Exception) {}
    }

    /** Scanned books get their heavy chapter/X-Ray pass only during another idle window. */
    fun scheduleDeferred(context: Context, bookId: String, fileHash: String?) {
        val request = OneTimeWorkRequestBuilder<BookProcessingWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    BookProcessingWorker.KEY_BOOK_ID to bookId,
                    BookProcessingWorker.KEY_FILE_HASH to fileHash,
                )
            )
            .setConstraints(quietDeviceConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .addTag("folio_book_$bookId")
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "folio_process_$bookId", ExistingWorkPolicy.REPLACE, request
        )
    }

    fun cancel(context: Context, bookId: String) {
        try { WorkManager.getInstance(context.applicationContext).cancelUniqueWork("folio_process_$bookId") } catch (_: Exception) {}
    }
}
