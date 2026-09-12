package com.makemission.folio.ui.library

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import com.makemission.folio.ui.reader.components.BottomReadingFade
import com.makemission.folio.ui.reader.components.TopReadingFade
import com.makemission.folio.data.search.SearchRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.ui.library.components.BookGrid
import com.makemission.folio.ui.library.components.EmptyLibraryState

/**
 * Editorial library — curated visual grid (cover thumbnails) with a flat
 * illustration empty state, an Import FAB, and a discreet Vocabulary section.
 * Adds pull-down search (reveal on downward drag, not pull-to-refresh) that
 * queries local parsed EPUB text + Room highlights/bookmarks on-device.
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
    onSearchResultClick: (SearchRepository.SearchResult) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val books by viewModel.books.collectAsState()
    val dueCount by viewModel.dueVocabularyCount.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    var isSearchRevealed by remember { mutableStateOf(false) }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsRepo = remember { SettingsRepository.get(context) }
    val autoScanEnabled by settingsRepo.autoScanEnabled.collectAsState(initial = null)

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

    // Long-press bottom sheet state
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var showBookActions by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LibraryHeader(
                bookCount = books.size,
                onSettingsClick = onSettingsClick,
                onSearchClick = { isSearchRevealed = !isSearchRevealed },
                isSearchRevealed = isSearchRevealed,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        // Swipe-right opens Settings; pull-down reveals search (standard pull-to-reveal, not pull-to-refresh)
        // Combined gesture handling — single pointerInput to avoid two competing detectors blocking each other.
        // Fix: debounce navigation so one swipe = one push (launchSingleTop alone isn't enough if gesture fires multiple times per drag).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .pointerInput(onSettingsClick, isSearchRevealed) {
                    var totalDx = 0f
                    var totalDy = 0f
                    var hasNavigated = false
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull() ?: continue
                            // Only react when at least one finger is dragging; ignore hover
                            // Reset on finger lift so next swipe can trigger again
                            if (event.changes.all { !it.pressed }) {
                                totalDx = 0f
                                totalDy = 0f
                                pullOffset = 0f
                                hasNavigated = false
                                continue
                            }
                            if (!drag.pressed) {
                                totalDx = 0f
                                totalDy = 0f
                                continue
                            }
                            // If already navigated this gesture, ignore until lift
                            if (hasNavigated) continue
                            val dx = drag.position.x - drag.previousPosition.x
                            val dy = drag.position.y - drag.previousPosition.y
                            // Accumulate only when movement is significant to avoid jitter
                            if (kotlin.math.abs(dx) < 0.5f && kotlin.math.abs(dy) < 0.5f) continue
                            totalDx += dx
                            totalDy += dy
                            // Horizontal swipe prioritized when |dx| > |dy| — right to Settings, left to Insights (keep teaser too)
                            if (totalDx > 120f && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                                drag.consume()
                                hasNavigated = true
                                onSettingsClick()
                                totalDx = 0f
                                totalDy = 0f
                            } else if (totalDx < -120f && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                                drag.consume()
                                hasNavigated = true
                                onInsightsClick()
                                totalDx = 0f
                                totalDy = 0f
                            } else if (totalDy > 80f && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) && !isSearchRevealed) {
                                pullOffset = totalDy
                                // Reveal search when pulled down sufficiently at top of scroll
                                if (pullOffset > 80f) {
                                    isSearchRevealed = true
                                    pullOffset = 0f
                                    drag.consume()
                                    totalDy = 0f
                                }
                            }
                        }
                    }
                },
        ) {
            // Pull-to-reveal search bar (on-device, highlights/bookmarks prioritized)
            AnimatedVisibility(
                visible = isSearchRevealed,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                LibrarySearchBar(
                    query = searchQuery,
                    isSearching = isSearching,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = {
                        viewModel.clearSearch()
                        isSearchRevealed = false
                    },
                    onDismiss = { isSearchRevealed = false },
                )
            }
            if (searchQuery.isBlank()) {
                LibraryQuickRow(
                    dueCount = dueCount,
                    onVocabularyClick = onVocabularyClick,
                    onInsightsClick = onInsightsClick,
                )
            }
            val gridState = rememberLazyGridState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    searchQuery.isNotBlank() -> SearchResultsList(
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        onResultClick = { result ->
                            viewModel.clearSearch()
                            isSearchRevealed = false
                            onSearchResultClick(result)
                        },
                        onClear = viewModel::clearSearch,
                    )
                    books.isEmpty() -> EmptyLibraryState(modifier = Modifier.fillMaxSize())
                    else -> BookGrid(
                        books = books,
                        onBookClick = onBookClick,
                        onLongClick = { book ->
                            selectedBook = book
                            showBookActions = true
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                    )
                }
                // Reuse ReadingFadeOverlay — iOS-like top/bottom feather so grid doesn't hard-cutoff
                if (searchQuery.isBlank() && books.isNotEmpty()) {
                    val canScrollUp by remember { derivedStateOf { gridState.canScrollBackward } }
                    val canScrollDown by remember { derivedStateOf { gridState.canScrollForward } }
                    TopReadingFade(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        visible = canScrollUp,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    BottomReadingFade(
                        backgroundColor = MaterialTheme.colorScheme.background,
                        visible = canScrollDown,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        // Long-press actions sheet (reuse existing data, no new logic)
        if (showBookActions && selectedBook != null) {
            val book = selectedBook!!
            val isCurated = viewModel.isCuratedBook(book)
            com.makemission.folio.ui.library.components.LibraryBookActionsSheet(
                book = book,
                isCurated = isCurated,
                onDismiss = { showBookActions = false },
                onRemove = { viewModel.removeBook(book, context) },
                onResetProgress = { viewModel.resetProgress(book.id) },
                onBookInfo = { showInfoDialog = true },
            )
        }
        if (showInfoDialog && selectedBook != null) {
            com.makemission.folio.ui.library.components.BookInfoDialog(
                book = selectedBook!!,
                onDismiss = { showInfoDialog = false },
            )
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
            LibraryQuickRow(dueCount = 0, onVocabularyClick = onVocabularyClick, onInsightsClick = onInsightsClick)
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
    bookCount: Int = 0,
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    isSearchRevealed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LibrarySearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search your library…", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else if (query.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
        Text(
            text = "On-device — title, author, highlights, bookmarks, then text.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun SearchResultsList(
    query: String,
    results: List<SearchRepository.SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchRepository.SearchResult) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Searching…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (results.isEmpty()) {
            // Graceful no-results with on-brand messaging (amber sun + shelf echo)
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
                        val green = androidx.compose.ui.graphics.Color(0xFF004F39)
                        drawCircle(color = amber, radius = 10f, center = center.copy(y = center.y - 6f))
                        drawRoundRect(color = green.copy(alpha = 0.85f), topLeft = center.copy(x = center.x - 16f, y = center.y + 6f), size = androidx.compose.ui.geometry.Size(32f, 4f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "No passages found for “$query”",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Try a different phrase — Folio searches titles, authors, and the text you’ve saved, all on-device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClear) { Text("Clear search") }
            }
        } else {
            Text(
                "${results.size} ${if (results.size == 1) "passage" else "passages"} for “$query” — highlights & bookmarks first",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(results, key = { it.bookId + it.chapterIndex.toString() + it.paragraphIndex.toString() + it.snippet.hashCode() }) { r ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onResultClick(r) },
                        color = if (r.matchType == SearchRepository.MatchType.HIGHLIGHT || r.matchType == SearchRepository.MatchType.BOOKMARK)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    r.bookTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                val badge = when (r.matchType) {
                                    SearchRepository.MatchType.HIGHLIGHT -> "Highlight"
                                    SearchRepository.MatchType.BOOKMARK -> "Bookmark"
                                    SearchRepository.MatchType.TITLE_AUTHOR -> "Title"
                                    else -> "Text"
                                }
                                Surface(color = if (r.matchType == SearchRepository.MatchType.HIGHLIGHT || r.matchType == SearchRepository.MatchType.BOOKMARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                                    Text(badge, style = MaterialTheme.typography.labelSmall, color = if (r.matchType == SearchRepository.MatchType.HIGHLIGHT || r.matchType == SearchRepository.MatchType.BOOKMARK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            Text(
                                "Ch ${r.chapterIndex + 1} · ¶ ${r.paragraphIndex + 1} · ${r.author}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(r.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryQuickRow(
    dueCount: Int,
    onVocabularyClick: () -> Unit,
    onInsightsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onVocabularyClick)
                .sizeIn(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dueCount > 0) {
                val pulseTransition = rememberInfiniteTransition(label = "vocab_dot")
                val dotPulse by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot_scale"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { scaleX = dotPulse; scaleY = dotPulse }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
            }
            Text(
                text = if (dueCount > 0) "$dueCount due" else "Vocabulary",
                style = MaterialTheme.typography.labelSmall,
                color = if (dueCount > 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (dueCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$dueCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onInsightsClick)
                .sizeIn(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Insights",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                "›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
