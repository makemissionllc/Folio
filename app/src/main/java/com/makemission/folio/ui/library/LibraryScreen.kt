package com.makemission.folio.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import com.makemission.folio.ui.reader.components.BottomReadingFade
import com.makemission.folio.ui.reader.components.TopReadingFade
import com.makemission.folio.ui.reader.ReaderPageTurnHandler
import com.makemission.folio.data.search.SearchRepository
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.epub.BookFileParser
import com.makemission.folio.data.model.ShelfStatus
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.data.work.DeviceScanScheduler
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.ui.library.components.BookGrid
import com.makemission.folio.ui.library.components.BookCoverCard
import com.makemission.folio.ui.library.components.EmptyLibraryState
import com.makemission.folio.ui.theme.FolioDoodle
import com.makemission.folio.ui.theme.FolioDoodleKind

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
@OptIn(ExperimentalMaterial3Api::class)
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
    var isSearchRevealed by remember { mutableStateOf(true) }
    var showAddBooks by remember { mutableStateOf(false) }
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val isWideTablet = LocalConfiguration.current.screenWidthDp >= 840
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.get(context) }
    val settingsScope = rememberCoroutineScope()
    val selectedShelfKey by settingsRepo.libraryShelf.collectAsState(initial = LibraryFilter.ALL.name)
    val selectedSortKey by settingsRepo.librarySort.collectAsState(initial = LibrarySort.RECENT.name)
    val selectedFilter = LibraryFilter.values().firstOrNull { it.name == selectedShelfKey } ?: LibraryFilter.ALL
    val sortOrder = LibrarySort.values().firstOrNull { it.name == selectedSortKey } ?: LibrarySort.RECENT
    val selectFilter: (LibraryFilter) -> Unit = { filter ->
        settingsScope.launch { settingsRepo.setLibraryShelf(filter.name) }
    }
    val toggleSort: () -> Unit = {
        val next = if (sortOrder == LibrarySort.RECENT) LibrarySort.TITLE else LibrarySort.RECENT
        settingsScope.launch { settingsRepo.setLibrarySort(next.name) }
    }
    val readBookIds by viewModel.readBookIds.collectAsState()
    val continueBookId by viewModel.continueBookId.collectAsState()
    val filteredBooks = books.filter { book ->
        when (selectedFilter) {
            LibraryFilter.ALL -> true
            LibraryFilter.READING -> book.shelfStatus == ShelfStatus.AUTO && book.id in readBookIds
            LibraryFilter.WANT -> book.shelfStatus == ShelfStatus.WANT
            LibraryFilter.FINISHED -> book.shelfStatus == ShelfStatus.FINISHED
        }
    }
    val visibleBooks = if (sortOrder == LibrarySort.TITLE) filteredBooks.sortedBy { it.title.lowercase() } else filteredBooks
    val continueBook = books.firstOrNull { it.id == continueBookId && it.shelfStatus != ShelfStatus.FINISHED }
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val bookPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importEpubs(uris, context)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val autoScanEnabled by settingsRepo.autoScanEnabled.collectAsState(initial = null)
    val compactTabletGrid by settingsRepo.tabletCompactLibraryGrid.collectAsState(initial = false)
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled by settingsRepo.hapticsEnabled.collectAsState(initial = true)
    DisposableEffect(isTablet) {
        if (isTablet) {
            ReaderPageTurnHandler.onKeyboardLibrarySearch = {
                isSearchRevealed = true
                searchFocusRequest++
            }
            ReaderPageTurnHandler.onKeyboardLibraryEscape = {
                viewModel.clearSearch()
                isSearchRevealed = false
            }
        }
        onDispose {
            if (ReaderPageTurnHandler.onKeyboardLibrarySearch != null) {
                ReaderPageTurnHandler.onKeyboardLibrarySearch = null
            }
            if (ReaderPageTurnHandler.onKeyboardLibraryEscape != null) {
                ReaderPageTurnHandler.onKeyboardLibraryEscape = null
            }
        }
    }
    val changeShelfStatus: (Book, ShelfStatus) -> Unit = { book, newStatus ->
        val previousStatus = book.shelfStatus
        if (previousStatus != newStatus) settingsScope.launch {
            val updated = viewModel.updateShelfStatus(book, newStatus)
            if (updated) {
                val message = when (newStatus) {
                    ShelfStatus.WANT -> "Moved to Want to Read"
                    ShelfStatus.FINISHED -> "Marked as finished"
                    ShelfStatus.AUTO -> "Moved back to All Books"
                }
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val undone = viewModel.updateShelfStatus(book, previousStatus)
                    snackbarHostState.showSnackbar(
                        if (undone) "Change undone" else "Could not undo that change",
                        duration = SnackbarDuration.Short,
                    )
                }
            } else {
                snackbarHostState.showSnackbar("Could not update this book", duration = SnackbarDuration.Short)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importError.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.scanResult.collect { msg ->
            if (hapticsEnabled && ("new book" in msg.lowercase() || "added" in msg.lowercase())) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }
    // Ensure durable idle work is scheduled; discovery and indexing never run on this screen.
    LaunchedEffect(autoScanEnabled) {
        if (autoScanEnabled == true) DeviceScanScheduler.enable(context)
        else if (autoScanEnabled == false) DeviceScanScheduler.disable(context)
    }

    // Long-press bottom sheet state
    var selectedBook by remember { mutableStateOf<Book?>(null) }
    var showBookActions by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var tabletPreviewBookId by remember { mutableStateOf<String?>(null) }
    var tabletPreviewFraction by rememberSaveable { mutableFloatStateOf(0.38f) }
    var tabletSplitWidthPx by remember { mutableFloatStateOf(0f) }
    val tabletPreviewBook = books.firstOrNull { it.id == tabletPreviewBookId }

    // For pull-to-reveal search intent detection — hoisted so gesture handler can check if at top
    // rememberLazyGridState is Saveable (restores scroll position via SavedStateHandle). Because
    // recently-read sorting changes order on every launch, restoring yesterday's index would
    // often land at the bottom with a stale offset. We keep the Saveable state for in-session
    // navigation (Settings → back preserves position) but force a one-time scroll-to-top on
    // fresh process launch via the ViewModel flag (which survives config change, not process death).
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(Unit) {
        if (!viewModel.hasHandledInitialGridScroll) {
            // Ensure grid starts at top on fresh launch — post frame so the restored
            // SavedState position is overridden only once per process.
            try {
                gridState.scrollToItem(0, 0)
            } catch (_: Exception) {}
            viewModel.markInitialGridScrollHandled()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LibraryHeader(
                bookCount = books.size,
                onSettingsClick = onSettingsClick,
                onSearchClick = { isSearchRevealed = true; searchFocusRequest++ },
                isSearchRevealed = isSearchRevealed,
                onAddBooks = { showAddBooks = true },
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
                            } else if (totalDy > 180f && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx) && !isSearchRevealed) {
                                // Deliberate intent: only reveal when already at top of grid (iOS-like pull-to-refresh)
                                // and with a noticeably larger drag than a normal scroll flick
                                val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                                if (!isAtTop) {
                                    // Not at top — treat as normal scroll, don't reveal
                                    totalDy = 0f
                                    pullOffset = 0f
                                } else {
                                    pullOffset = totalDy
                                    // Reveal search when pulled down sufficiently past top
                                    if (pullOffset > 180f) {
                                        isSearchRevealed = true
                                        pullOffset = 0f
                                        drag.consume()
                                        totalDy = 0f
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            // Pull-to-reveal search bar (on-device, highlights/bookmarks prioritized)
            AnimatedVisibility(
                visible = isSearchRevealed && !isTablet,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                LibrarySearchBar(
                    query = searchQuery,
                    isSearching = isSearching,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::clearSearch,
                    onDismiss = { isSearchRevealed = false },
                    focusRequest = searchFocusRequest,
                )
            }
            // Quick row removed — Vocabulary/Insights now only via Insights screen + swipe gestures (per task 4)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (isTablet) {
                    LibraryFilterRail(
                        selectedFilter = selectedFilter,
                        onSelect = selectFilter,
                        onInsightsClick = onInsightsClick,
                        onSettingsClick = onSettingsClick,
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                    if (isTablet && isSearchRevealed) {
                        LibrarySearchBar(
                            query = searchQuery,
                            isSearching = isSearching,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onClear = viewModel::clearSearch,
                            onDismiss = { isSearchRevealed = false },
                            focusRequest = searchFocusRequest,
                        )
                    }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { tabletSplitWidthPx = it.width.toFloat() }
                        .animateContentSize(animationSpec = tween(260)),
                    horizontalArrangement = Arrangement.spacedBy(if (isWideTablet && tabletPreviewBook != null) 14.dp else 0.dp),
                ) {
                val hasTabletPreview = isWideTablet && tabletPreviewBook != null
                Box(modifier = Modifier.weight(if (hasTabletPreview) 1f - tabletPreviewFraction else 1f).fillMaxHeight()) {
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
                        books = visibleBooks,
                        onBookClick = { book ->
                            if (isWideTablet) tabletPreviewBookId = book.id
                            else { selectedBook = book; showInfoDialog = true }
                        },
                        onLongClick = { book ->
                            selectedBook = book
                            showBookActions = true
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        readBookIds = readBookIds,
                        compactTabletGrid = isTablet && compactTabletGrid,
                        header = {
                            LibraryShelfHeader(
                                continueBook = if (isTablet) null else continueBook,
                                onContinue = { continueBook?.let(onBookClick) },
                                selectedFilter = selectedFilter,
                                onSelectFilter = selectFilter,
                                isTablet = isTablet,
                                hasVisibleBooks = visibleBooks.isNotEmpty(),
                                sortOrder = sortOrder,
                                onToggleSort = toggleSort,
                                compactTabletGrid = compactTabletGrid,
                                onToggleGridDensity = {
                                    settingsScope.launch { settingsRepo.setTabletCompactLibraryGrid(!compactTabletGrid) }
                                },
                            )
                        },
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
                if (isWideTablet && tabletPreviewBook != null) {
                    TabletPreviewResizeHandle(
                        modifier = Modifier.width(24.dp).fillMaxHeight(),
                        onResize = { deltaPx ->
                            if (tabletSplitWidthPx > 0f) {
                                tabletPreviewFraction = (tabletPreviewFraction + deltaPx / tabletSplitWidthPx)
                                    .coerceIn(0.28f, 0.52f)
                            }
                        },
                    )
                    AnimatedVisibility(
                        visible = true,
                        modifier = Modifier.weight(tabletPreviewFraction).fillMaxHeight(),
                        enter = slideInHorizontally(tween(240)) { it / 7 } + fadeIn(tween(220)),
                        exit = slideOutHorizontally(tween(180)) { it / 10 } + fadeOut(tween(160)),
                    ) {
                        TabletBookPreviewPane(
                            book = tabletPreviewBook,
                            onDismiss = { tabletPreviewBookId = null },
                            onRead = {
                                if (tabletPreviewBook.shelfStatus == ShelfStatus.WANT) viewModel.setShelfStatus(tabletPreviewBook, ShelfStatus.AUTO)
                                onBookClick(tabletPreviewBook)
                                tabletPreviewBookId = null
                            },
                            onStatusChange = { status -> changeShelfStatus(tabletPreviewBook, status) },
                        )
                    }
                }
                }
                }
                }
            if (!isTablet) {
                LibraryBottomBar(
                    onLibraryClick = { selectFilter(LibraryFilter.ALL); viewModel.clearSearch() },
                    onSearchClick = { isSearchRevealed = true; searchFocusRequest++ },
                    onInsightsClick = onInsightsClick,
                    onSettingsClick = onSettingsClick,
                )
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
                onRemove = { if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.removeBook(book, context) },
                onResetProgress = { if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.resetProgress(book.id) },
                onBookInfo = { showInfoDialog = true },
            )
        }
        if (showInfoDialog && selectedBook != null) {
            com.makemission.folio.ui.library.components.BookInfoDialog(
                book = selectedBook!!,
                onDismiss = { showInfoDialog = false },
                onRead = {
                    selectedBook?.let { book ->
                        if (book.shelfStatus == ShelfStatus.WANT) viewModel.setShelfStatus(book, ShelfStatus.AUTO)
                        onBookClick(book)
                    }
                    showInfoDialog = false
                },
                onStatusChange = { status -> selectedBook?.let { changeShelfStatus(it, status) } },
            )
        }
    }
    if (showAddBooks) {
        ModalBottomSheet(
            onDismissRequest = { showAddBooks = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Books", style = MaterialTheme.typography.headlineSmall)
                Text("Bring EPUB, MOBI, FB2 or TXT books into Folio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AddBookAction("Choose books", "Select one or more supported book files") {
                    showAddBooks = false
                    bookPicker.launch(BookFileParser.pickerMimeTypes)
                }
                AddBookAction("Scan this device", "Find books in shared storage") {
                    showAddBooks = false
                    if (EpubScanner.hasWholeDeviceAccess()) viewModel.scanDevice(context) else onSettingsClick()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TabletPreviewResizeHandle(
    modifier: Modifier = Modifier,
    onResize: (Float) -> Unit,
) {
    val currentOnResize by rememberUpdatedState(onResize)
    Box(
        modifier = modifier
            .semantics { contentDescription = "Drag to resize book details panel" }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnResize(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(4.dp).height(48.dp),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) { }
    }
}

private enum class LibraryFilter { ALL, READING, WANT, FINISHED }
private enum class LibrarySort { RECENT, TITLE }

@Composable
private fun TabletBookPreviewPane(
    book: Book,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onStatusChange: (ShelfStatus) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("QUICK LOOK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Book details", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            BookCoverCard(
                book = book,
                status = null,
                modifier = Modifier.width(132.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author.ifBlank { "Unknown author" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Reading progress", style = MaterialTheme.typography.labelMedium)
                    Text("${(book.progressFraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = { book.progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
                )
            }
            Button(onClick = onRead, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                Text(if (book.lastReadMillis > 0L) "Continue reading" else "Start reading")
            }
            if (book.filePath != null) {
                OutlinedButton(
                    onClick = { onStatusChange(if (book.shelfStatus == ShelfStatus.WANT) ShelfStatus.AUTO else ShelfStatus.WANT) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (book.shelfStatus == ShelfStatus.WANT) "Remove from Want to Read" else "Want to Read")
                }
                TextButton(onClick = { onStatusChange(if (book.shelfStatus == ShelfStatus.FINISHED) ShelfStatus.AUTO else ShelfStatus.FINISHED) }) {
                    Text(if (book.shelfStatus == ShelfStatus.FINISHED) "Mark as unread" else "Mark finished")
                }
            }
        }
    }
}

@Composable
private fun LibraryShelfHeader(
    continueBook: Book?,
    onContinue: () -> Unit,
    selectedFilter: LibraryFilter,
    onSelectFilter: (LibraryFilter) -> Unit,
    isTablet: Boolean,
    hasVisibleBooks: Boolean,
    sortOrder: LibrarySort,
    onToggleSort: () -> Unit,
    compactTabletGrid: Boolean,
    onToggleGridDensity: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        if (!isTablet) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    LibraryFilter.ALL to "All Books",
                    LibraryFilter.READING to "Reading",
                    LibraryFilter.WANT to "Want to Read",
                    LibraryFilter.FINISHED to "Finished",
                ).forEach { (filter, label) ->
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)).clickable { onSelectFilter(filter) },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedFilter == filter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            continueBook?.let { book ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Continue Reading", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onContinue),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(width = 58.dp, height = 82.dp).clip(RoundedCornerShape(5.dp)).background(book.coverColor).padding(7.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Text(book.title, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif), color = androidx.compose.ui.graphics.Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                LinearProgressIndicator(
                                    progress = { book.progressFraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text("${(book.progressFraction * 100).toInt().coerceAtLeast(1)}% read · Continue  ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isTablet) "Your Books" else "All Books", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggleSort) {
                    Text("Sort: ${if (sortOrder == LibrarySort.RECENT) "Recent" else "Title"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (isTablet) {
                    TextButton(onClick = onToggleGridDensity) {
                        Text("Grid: ${if (compactTabletGrid) "Compact" else "Roomy"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (!hasVisibleBooks && selectedFilter != LibraryFilter.ALL) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(16.dp)) {
                Text(
                    when (selectedFilter) {
                        LibraryFilter.READING -> "Books you open will appear here."
                        LibraryFilter.WANT -> "Save a book for later to see it here."
                        LibraryFilter.FINISHED -> "Books you finish will appear here."
                        LibraryFilter.ALL -> ""
                    },
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LibraryFilterRail(
    selectedFilter: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    onInsightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        listOf(
            LibraryFilter.ALL to "All Books",
            LibraryFilter.READING to "Reading",
            LibraryFilter.WANT to "Want to Read",
            LibraryFilter.FINISHED to "Finished",
        ).forEach { (filter, label) ->
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSelect(filter) },
                color = if (selectedFilter == filter) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(label, Modifier.padding(horizontal = 16.dp, vertical = 13.dp), color = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("MORE", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onInsightsClick) { Text("Reading Insights") }
        TextButton(onClick = onSettingsClick) { Text("Settings") }
    }
}

@Composable
private fun LibraryBottomBar(
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("Library" to onLibraryClick, "Search" to onSearchClick, "Insights" to onInsightsClick, "Settings" to onSettingsClick).forEach { (label, action) ->
            TextButton(onClick = action, modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (label == "Library") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddBookAction(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(15.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
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
    onAddBooks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FolioDoodle(
                    kind = FolioDoodleKind.SPARK,
                    modifier = Modifier.size(width = 22.dp, height = 22.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(if (bookCount == 1) "1 book" else "$bookCount books", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryHeaderButton(label = "Add books", onClick = onAddBooks) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            LibraryHeaderButton(label = "Search", onClick = onSearchClick) {
                val iconColor = MaterialTheme.colorScheme.onSurface
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 1.8.dp.toPx()
                    val radius = size.minDimension * 0.29f
                    drawCircle(
                        color = iconColor,
                        radius = radius,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.43f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                    )
                    drawLine(
                        color = iconColor,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.65f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.91f, size.height * 0.91f),
                        strokeWidth = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            LibraryHeaderButton(label = "Settings", onClick = onSettingsClick) {
                val color = MaterialTheme.colorScheme.onSurface
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 1.7.dp.toPx()
                    listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, y ->
                        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * y), androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * y), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        val x = if (index == 1) 0.65f else 0.4f
                        drawCircle(color = color, radius = stroke * 1.65f, center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y))
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeaderButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun LibrarySearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    focusRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) focusRequester.requestFocus()
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                placeholder = { Text("Search title or author", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            } else if (query.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
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
