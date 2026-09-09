package com.makemission.folio.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.model.Book
import com.makemission.folio.ui.library.components.BookGrid
import com.makemission.folio.ui.library.components.EmptyLibraryState

/**
 * Editorial library — curated visual grid (cover thumbnails) with a flat
 * illustration empty state, an Import FAB, and a discreet Vocabulary section.
 * Implantation is SAF-based: the system picker returns a URI, we copy into
 * private storage, parse with the existing EpubParser (title/author/cover),
 * and persist via Room. Vocabulary due count is surfaced non-intrusively.
 * Structure takes cues from the reference app's LibraryScaffold → LibraryGridLayout
 * but is Folio-specific and extends the existing Book/Grid rather than replacing.
 */
@Composable
fun LibraryScreen(
    onBookClick: (Book) -> Unit = {},
    onVocabularyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val books by viewModel.books.collectAsState()
    val dueCount by viewModel.dueVocabularyCount.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            viewModel.importEpub(uri, context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importError.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LibraryHeader(bookCount = books.size) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // EPUB mime + fallback; SAF filter is advisory — we validate after pick
                    launcher.launch(arrayOf("application/epub+zip", "application/octet-stream", "*/*"))
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            VocabularyTeaser(dueCount = dueCount, onClick = onVocabularyClick)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
}

/** Legacy overload kept for previews/tests — renders a static list. */
@Composable
fun LibraryScreen(
    books: List<Book>,
    onBookClick: (Book) -> Unit = {},
    onVocabularyClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LibraryHeader(bookCount = books.size) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            VocabularyTeaser(dueCount = 0, onClick = onVocabularyClick)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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

@Composable
private fun VocabularyTeaser(
    dueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Vocabulary",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (dueCount > 0) "$dueCount due for review" else "No words due — keep reading",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dueCount > 0) {
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$dueCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                TextButton(onClick = onClick) { Text("Open") }
            }
        }
    }
}
