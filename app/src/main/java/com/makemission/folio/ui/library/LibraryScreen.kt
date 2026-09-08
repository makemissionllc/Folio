package com.makemission.folio.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.model.Book
import com.makemission.folio.ui.library.components.BookGrid
import com.makemission.folio.ui.library.components.EmptyLibraryState

/**
 * Editorial library — curated visual grid (cover thumbnails) with a flat
 * illustration empty state. Structure takes cues from the reference app's
 * [LibraryScaffold] → [LibraryGridLayout] layering but is a minimal,
 * Folio-specific implementation.
 */
@Composable
fun LibraryScreen(
    books: List<Book>,
    onBookClick: (Book) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LibraryHeader(bookCount = books.size) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            if (books.isEmpty()) {
                EmptyLibraryState(modifier = Modifier.fillMaxSize())
            } else {
                BookGrid(
                    books = books,
                    onBookClick = onBookClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    bookCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (bookCount == 0) "Your curated collection"
            else "$bookCount titles · editorial grid",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
