package com.makemission.folio.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.makemission.folio.MainActivity

data class WidgetNavigationRequest(
    val bookId: String? = null,
    val bookTitle: String = "",
    val requestToken: Long = System.nanoTime(),
)

object WidgetNavigation {
    const val ACTION_OPEN_READER = "com.makemission.folio.action.OPEN_READER_FROM_WIDGET"
    const val EXTRA_BOOK_ID = "com.makemission.folio.extra.WIDGET_BOOK_ID"
    const val EXTRA_BOOK_TITLE = "com.makemission.folio.extra.WIDGET_BOOK_TITLE"
    const val ACTION_OPEN_LIBRARY = "com.makemission.folio.action.OPEN_LIBRARY_FROM_WIDGET"

    fun readerPendingIntent(context: Context, appWidgetId: Int, bookId: String, title: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_READER
            data = Uri.parse("folio://widget/book/${Uri.encode(bookId)}")
            putExtra(EXTRA_BOOK_ID, bookId)
            putExtra(EXTRA_BOOK_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun libraryPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_LIBRARY
            data = Uri.parse("folio://widget/library/$appWidgetId")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            10_000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
