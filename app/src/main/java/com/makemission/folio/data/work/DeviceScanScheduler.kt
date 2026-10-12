package com.makemission.folio.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.makemission.folio.data.scan.EpubScanner
import java.util.concurrent.TimeUnit

/** Batches discovery and import for periods when the device reports itself idle. */
object DeviceScanScheduler {
    const val TAG = "folio_device_scan"
    private const val PERIODIC = "folio_device_scan_periodic"
    private const val ONCE = "folio_device_scan_once"
    private const val PREFS = "folio_device_scan_state"
    const val LAST_SCAN = "last_scan_millis"

    private fun constraints() = Constraints.Builder()
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    fun enable(context: Context) {
        if (!EpubScanner.hasStoragePermission(context)) return
        val manager = WorkManager.getInstance(context.applicationContext)
        val periodic = PeriodicWorkRequestBuilder<DeviceScanWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints())
            .setInitialDelay(24, TimeUnit.HOURS)
            .addTag(TAG)
            .build()
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_SCAN, 0L)
        if (System.currentTimeMillis() - last >= TimeUnit.HOURS.toMillis(24)) requestScan(context)
    }

    fun requestScan(context: Context, manual: Boolean = false) {
        if (!EpubScanner.hasStoragePermission(context)) return
        val request = OneTimeWorkRequestBuilder<DeviceScanWorker>()
            .setConstraints(constraints())
            .setInputData(androidx.work.workDataOf("manual" to manual))
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONCE, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(PERIODIC)
        }
    }

    internal fun recordCompleted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(LAST_SCAN, System.currentTimeMillis()).apply()
    }

    internal fun wasScannedRecently(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_SCAN, 0L)
        return last > 0 && System.currentTimeMillis() - last < TimeUnit.HOURS.toMillis(12)
    }
}
