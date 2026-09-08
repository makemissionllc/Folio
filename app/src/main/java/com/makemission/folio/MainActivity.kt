package com.makemission.folio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.makemission.folio.ui.theme.FolioTheme

/**
 * Single-activity Compose entry point.
 *
 * The app content is wrapped in [FolioTheme] so the Folio design system
 * (deep-green / burgundy / amber + editorial typography) is the default.
 * Screens (library grid, reader) land here in later tasks.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Design-system task: no screens yet.
                }
            }
        }
    }
}
