package com.makemission.folio

import android.app.Application
import com.makemission.folio.data.logging.FolioLogger

/**
 * Application entry — initializes [FolioLogger] early so crashes and
 * early import/scan errors are captured even before any Activity.
 */
class FolioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FolioLogger.init(this)
        FolioLogger.i("FolioApp", "App onCreate — logger ready")
    }
}
