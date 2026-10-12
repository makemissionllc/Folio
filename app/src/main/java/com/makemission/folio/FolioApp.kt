package com.makemission.folio

import android.app.Application
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import com.makemission.folio.data.logging.FolioLogger

/**
 * Application entry — initializes [FolioLogger] early so crashes and
 * early import/scan errors are captured even before any Activity.
 */
class FolioApp : Application() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate() {
        // Text selection uses Folio's custom ActionMode (Highlight, Explain and Ask Folio).
        // Compose 1.10's new context-menu path bypasses LocalTextToolbar, so keep its legacy
        // toolbar path until the new API can carry Folio's selected-text actions.
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        super.onCreate()
        FolioLogger.init(this)
        FolioLogger.i("FolioApp", "App onCreate — logger ready")
    }
}
