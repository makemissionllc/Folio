package com.makemission.folio

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.navigation.FolioNavHost
import com.makemission.folio.ui.reader.ReaderPageTurnHandler
import com.makemission.folio.ui.theme.FolioPalette
import com.makemission.folio.ui.theme.FolioTheme

/**
 * Single-activity Compose entry point — navigation host lives here.
 *
 * Library is the start destination; tapping a cover navigates to the
 * Reader (§3 phone/tablet layouts, §6 Room). Volume keys are forwarded
 * to the Reader for one-handed page turns when it is visible.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette by SettingsRepository.get(this).darkPalette.collectAsState(initial = FolioPalette.DEFAULT)
            FolioTheme(palette = palette) {
                FolioNavHost()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ReaderPageTurnHandler.onVolumeKey?.let { handler ->
                handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
