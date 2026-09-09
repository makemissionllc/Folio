package com.makemission.folio.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.ui.library.components.BookGrid
import com.makemission.folio.ui.library.components.EmptyLibraryState

/**
 * Editorial library — curated visual grid (cover thumbnails) with a flat
 * illustration empty state, an Import FAB, and a discreet Vocabulary section.
 * Implantation is SAF-based: the system picker returns a URI, we copy into
 * private storage, parse with the existing EpubParser (title/author/cover),
 * and persist via Room. Vocabulary due count is surfaced non-intrusively.
 * Also hosts automatic device scanning for EPUBs (Downloads/Documents/external)
 * via [com.makemission.folio.data.scan.EpubScanner] — reuses the same import
 * pipeline (private copy + EpubParser + Room) with content-hash/path dedup.
 * Shows a subtle non-blocking scan progress indicator; auto-scans on launch
 * when Settings toggle is on and storage permission was granted during onboarding.
 * Structure takes cues from the reference app's LibraryScaffold → LibraryGridLayout
 * but is Folio-specific and extends the existing Book/Grid rather than replacing.
 */
@Composable
fun LibraryScreen(
    onBookClick: (Book) -> Unit = {},
    onVocabularyClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onInsightsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val books by viewModel.books.collectAsState()
    val dueCount by viewModel.dueVocabularyCount.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsRepo = remember { SettingsRepository.get(context) }
    val autoScanEnabled by settingsRepo.autoScanEnabled.collectAsState(initial = null)

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
    LaunchedEffect(Unit) {
        viewModel.scanResult.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    // Auto-scan on launch when toggle is enabled and storage permission was granted
    // (spec: if permission denied, feature is unavailable/disabled — no crash, no repeated prompt)
    LaunchedEffect(autoScanEnabled) {
        if (autoScanEnabled == true && viewModel.hasStoragePermission(context)) {
            // Small delay so Library content settles; non-blocking
            kotlinx.coroutines.delay(600)
            if (!viewModel.isScanning.value) {
                viewModel.scanDevice(context)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LibraryHeader(
                bookCount = books.size,
                onSettingsClick = onSettingsClick,
            )
        },
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
        // Swipe-right from Library opens Settings (global gesture)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .pointerInput(onSettingsClick) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDx += dragAmount
                            // Only trigger on a clear right swipe, consume
                            if (totalDx > 120f) {
                                change.consume()
                                onSettingsClick()
                                totalDx = 0f
                            }
                        },
                        onDragEnd = { totalDx = 0f },
                        onDragCancel = { totalDx = 0f },
                    )
                },
        ) {
            VocabularyTeaser(dueCount = dueCount, onClick = onVocabularyClick)
            InsightsTeaser(onClick = onInsightsClick)
            // Subtle non-blocking scan progress (spec: don't block UI)
            if (isScanning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = scanProgress ?: "Scanning device for EPUBs…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
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
    onSettingsClick: () -> Unit = {},
    onInsightsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { LibraryHeader(bookCount = books.size, onSettingsClick = onSettingsClick) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
        ) {
            VocabularyTeaser(dueCount = 0, onClick = onVocabularyClick)
            InsightsTeaser(onClick = onInsightsClick)
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
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        TextButton(onClick = onSettingsClick) {
            Text("⚙ Settings", style = MaterialTheme.typography.labelMedium)
        }
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

@Composable
private fun InsightsTeaser(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Insights",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "A quiet ledger of your reading",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text("Open") }
        }
    }
}
