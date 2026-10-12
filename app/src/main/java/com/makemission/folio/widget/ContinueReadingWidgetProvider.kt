package com.makemission.folio.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.makemission.folio.R
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.db.entity.ReadingProgress
import com.makemission.folio.data.model.ShelfStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** A small, on-device Continue Reading widget. */
class ContinueReadingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetIds, force = true, completion = goAsync())
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val refreshLock = Any()
        @Volatile private var lastRefreshMillis = 0L

        /** Refresh after a meaningful library/progress change, throttled while the reader is scrolling. */
        fun refresh(context: Context, force: Boolean = false) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, ContinueReadingWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            updateWidgets(appContext, ids, force)
        }

        private fun updateWidgets(
            context: Context,
            ids: IntArray,
            force: Boolean,
            completion: BroadcastReceiver.PendingResult? = null,
        ) {
            if (ids.isEmpty()) {
                completion?.finish()
                return
            }
            synchronized(refreshLock) {
                val now = System.currentTimeMillis()
                if (!force && now - lastRefreshMillis < 45_000L) {
                    completion?.finish()
                    return
                }
                lastRefreshMillis = now
            }
            val appContext = context.applicationContext
            scope.launch {
                try {
                    val content = runCatching {
                        val db = FolioDatabase.get(appContext)
                        val books = db.bookDao().getAll().associateBy(BookEntity::id)
                        val progressRows = db.readingProgressDao().getAll()
                        val inProgress = progressRows.firstNotNullOfOrNull { progress ->
                            val book = books[progress.bookId] ?: return@firstNotNullOfOrNull null
                            if (book.shelfStatus == ShelfStatus.FINISHED.name || progress.progressFraction >= 1f) null
                            else book to progress
                        }
                        inProgress ?: books.values
                            .asSequence()
                            .filter { it.shelfStatus != ShelfStatus.FINISHED.name }
                            .sortedByDescending(BookEntity::addedAt)
                            .firstOrNull()
                            ?.let { it to null }
                    }.getOrNull()
                    val cover = content?.first?.let { decodeCover(it.coverImagePath) }

                    ids.forEach { id ->
                        val views = RemoteViews(appContext.packageName, R.layout.widget_continue_reading)
                        val (book, progress) = content ?: (null to null)
                        if (book != null) {
                            val fraction = progress?.progressFraction?.coerceIn(0f, 1f) ?: 0f
                            val percent = (fraction * 100f).toInt()
                            views.setTextViewText(R.id.widgetEyebrow, if (percent > 0) "CONTINUE READING" else "READY WHEN YOU ARE")
                            views.setTextViewText(R.id.widgetTitle, book.title)
                            views.setTextViewText(R.id.widgetAuthor, book.author.ifBlank { "Your next chapter is waiting" })
                            views.setProgressBar(R.id.widgetProgress, 100, percent, false)
                            views.setTextViewText(R.id.widgetProgressLabel, if (percent > 0) "$percent% read" else "Start this book")
                            views.setOnClickPendingIntent(
                                R.id.widgetCard,
                                WidgetNavigation.readerPendingIntent(appContext, id, book.id, book.title),
                            )
                            views.setOnClickPendingIntent(R.id.widgetLibraryButton, WidgetNavigation.libraryPendingIntent(appContext, id))
                            views.setContentDescription(R.id.widgetCard, "Continue reading ${book.title}")
                            if (cover != null) {
                                views.setImageViewBitmap(R.id.widgetCover, cover)
                                views.setViewVisibility(R.id.widgetCover, android.view.View.VISIBLE)
                                views.setViewVisibility(R.id.widgetCoverGlyph, android.view.View.GONE)
                                views.setContentDescription(R.id.widgetCoverFrame, "Cover of ${book.title}")
                            } else {
                                views.setViewVisibility(R.id.widgetCover, android.view.View.GONE)
                                views.setViewVisibility(R.id.widgetCoverGlyph, android.view.View.VISIBLE)
                            }
                        } else {
                            views.setTextViewText(R.id.widgetEyebrow, "YOUR READING SPACE")
                            views.setTextViewText(R.id.widgetTitle, "A good book is close")
                            views.setTextViewText(R.id.widgetAuthor, "Add an EPUB or MOBI to your Folio library")
                            views.setProgressBar(R.id.widgetProgress, 100, 0, false)
                            views.setTextViewText(R.id.widgetProgressLabel, "Open library")
                            views.setOnClickPendingIntent(R.id.widgetCard, WidgetNavigation.libraryPendingIntent(appContext, id))
                            views.setOnClickPendingIntent(R.id.widgetLibraryButton, WidgetNavigation.libraryPendingIntent(appContext, id))
                            views.setViewVisibility(R.id.widgetCover, android.view.View.GONE)
                            views.setViewVisibility(R.id.widgetCoverGlyph, android.view.View.VISIBLE)
                            views.setContentDescription(R.id.widgetCard, "Open your Folio library")
                        }
                        AppWidgetManager.getInstance(appContext).updateAppWidget(id, views)
                    }
                } finally {
                    completion?.finish()
                }
            }
        }

        private fun decodeCover(path: String?): Bitmap? {
            val file = path?.let(::File)?.takeIf { it.isFile } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= 128 && bounds.outHeight / (sampleSize * 2) >= 192) {
                sampleSize *= 2
            }
            return BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }
    }
}
