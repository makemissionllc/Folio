package com.makemission.folio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.makemission.folio.data.model.curatedSampleBooks
import com.makemission.folio.ui.library.LibraryScreen
import com.makemission.folio.ui.theme.FolioTheme

/**
 * Single-activity Compose entry point.
 *
 * The library screen is the app's home. Real storage (Room) lands later;
 * for now a curated in-memory seed shows the editorial grid, with the
 * flat empty state ready when the list is empty.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioTheme {
                val books = remember { curatedSampleBooks() }
                LibraryScreen(books = books)
            }
        }
    }
}
