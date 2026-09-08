package com.makemission.folio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.makemission.folio.navigation.FolioNavHost
import com.makemission.folio.ui.theme.FolioTheme

/**
 * Single-activity Compose entry point — navigation host lives here.
 *
 * Library is the start destination; tapping a cover navigates to the
 * Reader (§3 phone/tablet layouts). Storage/Room is initialized lazily
 * on first read (§6).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioTheme {
                FolioNavHost()
            }
        }
    }
}
