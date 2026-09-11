package com.makemission.folio.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules background processing for a book after minimal import.
 *
 * - Enqueues an expedited one-time worker so the first chapters are ready quickly
 *   (features aren't broken for long), but also respects battery — retry with backoff.
 * - Uses constraints that prefer battery-not-low (WorkManager will batch when idle/charging
 *   if the device is in doze, but doesn't block indefinitely). The worker itself
 *   processes chapter-by-chapter, so even if only the first chapters finish early,
 *   X-Ray for the current chapter is already available.
 */
object BookProcessingScheduler {

    fun schedule(context: Context, bookId: String, fileHash: String?) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false) // don't strictly wait for charging; run soon
            .setRequiresStorageNotLow(false)
            .build()

        val request = OneTimeWorkRequestBuilder<BookProcessingWorker>()
            .setInputData(
                androidx.work.workDataOf(
                    BookProcessingWorker.KEY_BOOK_ID to bookId,
                    BookProcessingWorker.KEY_FILE_HASH to fileHash,
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("folio_book_$bookId")
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "folio_process_$bookId",
            ExistingWorkPolicy.KEEP, // don't re-enqueue if already processing that book
            request,
        )

        // Also enqueue a deferred "idle/charging" friendly worker as best-effort for remaining chapters
        // if the expedited one was throttled — here we reuse same worker; WorkManager will batch.
        // No separate constraint requiring idle, because that could delay for hours. The above
        // already allows WorkManager to defer to idle/charging via batching while still completing soon.

        // Log for debugging
        try {
            com.makemission.folio.data.logging.FolioLogger.i("BookWorker", "Scheduled processing for $bookId")
        } catch (_: Exception) {}
    }

    fun cancel(context: Context, bookId: String) {
        try { WorkManager.getInstance(context.applicationContext).cancelUniqueWork("folio_process_$bookId") } catch (_: Exception) {}
    }
}
