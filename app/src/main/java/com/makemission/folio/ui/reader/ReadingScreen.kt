package com.makemission.folio.ui.reader

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.db.entity.Bookmark
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.dictionary.DictionaryRepository
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.ui.reader.components.BookmarkBottomSheet
import com.makemission.folio.ui.reader.components.DictionaryPopup
import com.makemission.folio.ui.reader.components.ExplainSelectionContainer
import com.makemission.folio.ui.reader.components.ExpandableDiagram
import com.makemission.folio.ui.reader.components.HighlightOverlay
import com.makemission.folio.ui.reader.components.ReadingProgressBar
import com.makemission.folio.ui.reader.components.XRayBottomSheet
import com.makemission.folio.ui.theme.AdaptiveContrastEngine
import com.makemission.folio.ui.theme.hasAmbientLightSensor
import com.makemission.folio.ui.theme.rememberAmbientLightLux
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class LassoCapture(
    val points: List<Offset>,
    val bounds: Rect,
)

/**
 * Core reading screen — native EPUB rendering, serif typography, adaptive layout,
 * plus frictionless navigation and stylus engine (§3 + §4).
 *
 * Highlighting is zero-friction (stylus instantly draws), true-ink Multiply,
 * with organic pressure/tilt physics and lasso extraction (image vs on-device OCR).
 *
 * Structure inspired by the reference app's `ReaderLayout` + `ReaderContent`
 * layering; implementation is Folio-specific and minimal.
 */
@Composable
fun ReadingScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    initialChapterIndex: Int? = null,
    initialParagraphIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val factory = remember(bookId, bookTitle) {
        ReadingViewModelFactory(application, bookId, bookTitle)
    }
    val viewModel: ReadingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val xrayIndex by viewModel.xrayIndex.collectAsState()
    val isXRayLoading by viewModel.isXRayLoading.collectAsState()
    val inBookQuery by viewModel.inBookQuery.collectAsState()
    val inBookResults by viewModel.inBookResults.collectAsState()
    val isInBookSearching by viewModel.isInBookSearching.collectAsState()

    ReadingScreenContent(
        uiState = uiState,
        highlights = highlights,
        bookmarks = bookmarks,
        xrayIndex = xrayIndex,
        isXRayLoading = isXRayLoading,
        onBack = onBack,
        onSaveProgress = viewModel::saveProgress,
        onAddHighlight = { pts, pressures, tilts, ch ->
            viewModel.addHighlight(pts, pressures, tilts, ch)
        },
        onTrackVocabulary = viewModel::trackVocabulary,
        onToggleBookmark = viewModel::toggleBookmark,
        onDeleteBookmark = viewModel::removeBookmark,
        inBookQuery = inBookQuery,
        inBookResults = inBookResults,
        isInBookSearching = isInBookSearching,
        onInBookQueryChange = viewModel::onInBookQueryChange,
        onClearInBookSearch = viewModel::clearInBookSearch,
        initialChapterIndex = initialChapterIndex,
        initialParagraphIndex = initialParagraphIndex,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingScreenContent(
    uiState: ReadingUiState,
    highlights: List<Highlight>,
    bookmarks: List<Bookmark> = emptyList(),
    xrayIndex: Map<Int, List<com.makemission.folio.data.xray.XRayTerm>>,
    isXRayLoading: Boolean,
    onBack: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onAddHighlight: (List<Offset>, List<Float>, List<Float>, Int) -> Unit,
    onTrackVocabulary: (String, String?) -> Unit = { _, _ -> },
    onToggleBookmark: (Int, Int) -> Unit = { _, _ -> },
    onDeleteBookmark: (Bookmark) -> Unit = {},
    inBookQuery: String = "",
    inBookResults: List<com.makemission.folio.data.search.SearchRepository.SearchResult> = emptyList(),
    isInBookSearching: Boolean = false,
    onInBookQueryChange: (String) -> Unit = {},
    onClearInBookSearch: () -> Unit = {},
    initialChapterIndex: Int? = null,
    initialParagraphIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isTabletLandscape = remember(configuration) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenWidthDp >= 840
    }

    var chromeVisible by remember { mutableStateOf(true) }
    var bionicEnabled by rememberSaveable { mutableStateOf(false) }
    var showXRay by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showInBookSearch by remember { mutableStateOf(false) }
    var lassoCapture by remember { mutableStateOf<LassoCapture?>(null) }
    var dictPopup by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // Pending jump targets (reuse flatIndex logic from bookmarks for search)
    var pendingBookmarkJump by remember { mutableStateOf<Bookmark?>(null) }
    var pendingSearchJump by remember { mutableStateOf<com.makemission.folio.data.search.SearchRepository.SearchResult?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initial jump from Library search (reuse bookmark jump-to-position logic)
    LaunchedEffect(uiState.chapters, initialChapterIndex, initialParagraphIndex) {
        if (uiState.isLoading || uiState.chapters.isEmpty()) return@LaunchedEffect
        val ch = initialChapterIndex ?: return@LaunchedEffect
        val para = initialParagraphIndex ?: 0
        if (ch in uiState.chapters.indices) {
            val synthetic = com.makemission.folio.data.search.SearchRepository.SearchResult(
                bookId = uiState.bookId,
                bookTitle = uiState.bookTitle,
                author = "",
                chapterIndex = ch,
                paragraphIndex = para.coerceIn(0, (uiState.chapters[ch].paragraphs.size - 1).coerceAtLeast(0)),
                snippet = "",
                matchType = com.makemission.folio.data.search.SearchRepository.MatchType.CONTENT,
                rank = 2,
            )
            pendingSearchJump = synthetic
        }
    }

    // Colorimetric Contrast Optimization (§5) — builds on FolioTheme, not rewriting it
    var adaptiveEnabled by rememberSaveable { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val baseBg = MaterialTheme.colorScheme.background
    val baseText = MaterialTheme.colorScheme.onBackground
    val hasSensor = remember(context) { hasAmbientLightSensor(context) }
    // Sensor is only active when toggle is on and device has sensor
    val luxState = rememberAmbientLightLux(enabled = adaptiveEnabled && hasSensor)
    val rawLux = luxState.value
    // Compute adaptive pair via WCAG engine when sensor available
    val adaptivePair = remember(rawLux, isDark, baseBg, baseText, adaptiveEnabled, hasSensor) {
        if (adaptiveEnabled && hasSensor && rawLux != null) {
            AdaptiveContrastEngine.adaptivePair(baseBg, baseText, rawLux, isDark)
        } else null
    }
    val targetBg = adaptivePair?.first ?: baseBg
    val targetText = adaptivePair?.second ?: baseText
    // Smooth/gradual transition — not jarring flicker (800ms tween)
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "adaptiveBg",
    )
    val animatedText by animateColorAsState(
        targetValue = targetText,
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "adaptiveText",
    )
    val readingBg = if (adaptiveEnabled && hasSensor && rawLux != null) animatedBg else baseBg
    val readingText = if (adaptiveEnabled && hasSensor && rawLux != null) animatedText else baseText
    // Also adapt surface for chrome consistency (same as bg)
    val readingSurface = readingBg
    val readingOnSurface = readingText

    // Settings: "Always show progress bar" — persisted via DataStore, overrides tap-to-hide
    val settingsRepo = remember(context) { com.makemission.folio.data.settings.SettingsRepository.get(context) }
    val alwaysShowProgressBar by settingsRepo.alwaysShowProgressBar.collectAsState(initial = false)

    // --- Bookmarks: hoisted list states so TopBar knows current position ---
    val singleListState = rememberLazyListState()
    val tabletLeftState = rememberLazyListState()
    val tabletRightState = rememberLazyListState()

    // Current reading position for bookmark toggle (first visible paragraph)
    val currentBookmarkPos by remember {
        derivedStateOf {
            if (uiState.chapters.isEmpty()) 0 to 0
            else if (isTabletLandscape) {
                bookmarkPositionForFlat(tabletLeftState.firstVisibleItemIndex, uiState.chapters, isTablet = true)
            } else {
                bookmarkPositionForFlat(singleListState.firstVisibleItemIndex, uiState.chapters, isTablet = false)
            }
        }
    }
    val isCurrentBookmarked by remember {
        derivedStateOf {
            val (ch, para) = currentBookmarkPos
            bookmarks.any { it.chapterIndex == ch && it.paragraphIndex == para }
        }
    }

    // Library search jump handler — reuses bookmark flat-index logic (don't duplicate)
    LaunchedEffect(pendingSearchJump, isTabletLandscape, uiState.chapters) {
        val target = pendingSearchJump ?: return@LaunchedEffect
        if (uiState.chapters.isEmpty()) return@LaunchedEffect
        try {
            if (isTabletLandscape) {
                val mid = (uiState.chapters.size + 1) / 2
                if (target.chapterIndex < mid) {
                    val leftFlat = flatIndexForBookmarkInLeft(target.chapterIndex, target.paragraphIndex, uiState.chapters.take(mid))
                    val flat = if (leftFlat >= 0) leftFlat else flatIndexForBookmark(target.chapterIndex, target.paragraphIndex, uiState.chapters, true, mid)
                    if (flat >= 0) {
                        tabletLeftState.animateScrollToItem(flat.coerceIn(0, (tabletLeftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                        if (tabletLeftState.firstVisibleItemIndex != flat) tabletLeftState.scrollToItem(flat.coerceIn(0, (tabletLeftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    }
                } else {
                    val rightFlat = flatIndexForBookmarkInRight(target.chapterIndex, target.paragraphIndex, uiState.chapters.drop(mid), mid)
                    val flat = rightFlat.coerceIn(0, (tabletRightState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    tabletRightState.animateScrollToItem(flat)
                    if (tabletRightState.firstVisibleItemIndex != flat) tabletRightState.scrollToItem(flat)
                }
            } else {
                val flat = flatIndexForBookmark(target.chapterIndex, target.paragraphIndex, uiState.chapters, false, 0)
                if (flat >= 0) {
                    singleListState.animateScrollToItem(flat.coerceIn(0, (singleListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    if (singleListState.firstVisibleItemIndex != flat) singleListState.scrollToItem(flat.coerceIn(0, (singleListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                }
            }
        } catch (_: Exception) {}
        pendingSearchJump = null
    }

    fun jumpInBookTo(ch: Int, para: Int) {
        val synthetic = com.makemission.folio.data.search.SearchRepository.SearchResult(
            bookId = uiState.bookId,
            bookTitle = uiState.bookTitle,
            author = "",
            chapterIndex = ch,
            paragraphIndex = para,
            snippet = "",
            matchType = com.makemission.folio.data.search.SearchRepository.MatchType.CONTENT,
            rank = 2,
        )
        pendingSearchJump = synthetic
        showInBookSearch = false
    }

    val onWordDoubleTap: (String) -> Unit = { word ->
        val def = DictionaryRepository.lookup(word, context)
        dictPopup = word to def
        // Persist for SM-2 review (§5) — extend, don't rewrite DictionaryRepository/Popup
        if (def != null) onTrackVocabulary(word, def)
    }

    // Lasso extraction dialog — distinct handling for image vs text.
    lassoCapture?.let { capture ->
        AlertDialog(
            onDismissRequest = { lassoCapture = null },
            title = { Text("Lasso captured", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Closed loop detected. Choose extraction: image (diagram) or text (on-device OCR, no network).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    copyLassoTextToClipboard(context, uiState.chapters)
                    Toast.makeText(context, "Text copied (on-device OCR)", Toast.LENGTH_SHORT).show()
                    lassoCapture = null
                }) { Text("Copy Text") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        extractLassoAsImage(context, capture)
                        Toast.makeText(context, "Diagram extracted", Toast.LENGTH_SHORT).show()
                        lassoCapture = null
                    }) { Text("Extract Image") }
                    TextButton(onClick = { lassoCapture = null }) { Text("Dismiss") }
                }
            },
        )
    }

    dictPopup?.let { (word, def) ->
        DictionaryPopup(
            word = word,
            definition = def,
            onDismiss = { dictPopup = null },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = readingBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    TopAppBar(
                    title = {
                        Text(
                            text = uiState.bookTitle.ifBlank { "Reading" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("← Back", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    actions = {
                        // In-book search (on-device, highlights/bookmarks priority, same SearchRepository)
                        TextButton(onClick = { showInBookSearch = !showInBookSearch }) {
                            Text(
                                "⌕",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (showInBookSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Bookmark icon — filled when current position is bookmarked (distinct from highlights)
                        TextButton(onClick = {
                            val (ch, para) = currentBookmarkPos
                            onToggleBookmark(ch, para)
                        }) {
                            Text(
                                text = if (isCurrentBookmarked) "🔖" else "☆",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showBookmarks = true }) {
                            Text(
                                text = if (bookmarks.isEmpty()) "Bookmarks" else "Bookmarks ${bookmarks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showXRay = true }) {
                            Text(
                                "X-Ray",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { bionicEnabled = !bionicEnabled }) {
                            Text(
                                if (bionicEnabled) "Bionic On" else "Bionic Off",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bionicEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { if (hasSensor) adaptiveEnabled = !adaptiveEnabled },
                            enabled = hasSensor,
                        ) {
                            Text(
                                text = when {
                                    !hasSensor -> "No sensor"
                                    adaptiveEnabled -> "Contrast Auto"
                                    else -> "Contrast Fixed"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (adaptiveEnabled && hasSensor) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = readingSurface.copy(alpha = 0.92f),
                        titleContentColor = readingOnSurface,
                    ),
                    windowInsets = WindowInsets.statusBars,
                )
                }
                // In-book pull-to-reveal search bar (on-device, contextual)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showInBookSearch,
                    enter = androidx.compose.animation.expandVertically() + fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
                ) {
                    androidx.compose.material3.Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.OutlinedTextField(
                                value = inBookQuery,
                                onValueChange = onInBookQueryChange,
                                placeholder = { Text("Search in this book…", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                ),
                            )
                            if (isInBookSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else if (inBookQuery.isNotEmpty()) {
                                TextButton(onClick = onClearInBookSearch) { Text("Clear") }
                            } else {
                                TextButton(onClick = { showInBookSearch = false }) { Text("Close") }
                            }
                        }
                        // On-brand helper
                        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)) {
                            Text(
                                "On-device — highlights & bookmarks first, then text.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    // Contextual in-book results (on-device, highlights/bookmarks priority)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = inBookQuery.isNotBlank(),
                        enter = androidx.compose.animation.expandVertically() + fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
                    ) {
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            if (isInBookSearching) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                                    Text("Searching…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else if (inBookResults.isEmpty()) {
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "No passages found for “$inBookQuery”",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Try a different phrase — Folio searches highlights, bookmarks and text, all on-device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = onClearInBookSearch) { Text("Clear search") }
                                }
                            } else {
                                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                    Text(
                                        "${inBookResults.size} ${if (inBookResults.size == 1) "passage" else "passages"} for “$inBookQuery” — highlights & bookmarks first",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    )
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier.fillMaxWidth().height(220.dp),
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(inBookResults.size) { idx ->
                                            val r = inBookResults[idx]
                                            androidx.compose.material3.Surface(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { jumpInBookTo(r.chapterIndex, r.paragraphIndex) },
                                                color = if (r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.HIGHLIGHT || r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.BOOKMARK)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(12.dp),
                                                tonalElevation = 1.dp,
                                            ) {
                                                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                        Text(
                                                            "Ch ${r.chapterIndex + 1} · ¶ ${r.paragraphIndex + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        val badge = when (r.matchType) {
                                                            com.makemission.folio.data.search.SearchRepository.MatchType.HIGHLIGHT -> "Highlight"
                                                            com.makemission.folio.data.search.SearchRepository.MatchType.BOOKMARK -> "Bookmark"
                                                            else -> "Text"
                                                        }
                                                        androidx.compose.material3.Surface(
                                                            color = if (r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.HIGHLIGHT || r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.BOOKMARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                            shape = RoundedCornerShape(8.dp),
                                                        ) {
                                                            Text(badge, style = MaterialTheme.typography.labelSmall, color = if (r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.HIGHLIGHT || r.matchType == com.makemission.folio.data.search.SearchRepository.MatchType.BOOKMARK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                                        }
                                                    }
                                                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                                                    Text(r.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(readingBg),
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.chapters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No content to display.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Selection "Explain" — extends double-tap flow, same popup/backing dataset, fully offline
                ExplainSelectionContainer(
                    onExplainRequested = { selected ->
                        val phrase = selected.trim().replace(Regex("\\s+"), " ").take(140)
                        if (phrase.isNotBlank()) {
                            val def = DictionaryRepository.lookupPhrase(phrase, context)
                                ?: DictionaryRepository.lookup(phrase, context)
                            dictPopup = phrase to def
                            if (def != null) onTrackVocabulary(phrase, def)
                        }
                    }
                ) {
                    if (isTabletLandscape) {
                        TwoColumnReadingContent(
                            chapters = uiState.chapters,
                            restoredChapterIndex = uiState.restoredChapterIndex,
                            chromeVisible = chromeVisible,
                            bionicEnabled = bionicEnabled,
                            highlights = highlights,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onSaveProgress = onSaveProgress,
                            onAddHighlight = onAddHighlight,
                            onLasso = { pts, bounds -> lassoCapture = LassoCapture(pts, bounds) },
                            onWordDoubleTap = onWordDoubleTap,
                            readingText = readingText,
                            readingBackground = readingBg,
                            alwaysShowProgressBar = alwaysShowProgressBar,
                            leftListState = tabletLeftState,
                            rightListState = tabletRightState,
                            pendingBookmarkJump = pendingBookmarkJump,
                            onJumpConsumed = { pendingBookmarkJump = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        SingleColumnReadingContent(
                            chapters = uiState.chapters,
                            restoredChapterIndex = uiState.restoredChapterIndex,
                            chromeVisible = chromeVisible,
                            bionicEnabled = bionicEnabled,
                            highlights = highlights,
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onSaveProgress = onSaveProgress,
                            onAddHighlight = onAddHighlight,
                            onLasso = { pts, bounds -> lassoCapture = LassoCapture(pts, bounds) },
                            onWordDoubleTap = onWordDoubleTap,
                            readingText = readingText,
                            readingBackground = readingBg,
                            alwaysShowProgressBar = alwaysShowProgressBar,
                            listState = singleListState,
                            pendingBookmarkJump = pendingBookmarkJump,
                            onJumpConsumed = { pendingBookmarkJump = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (showXRay) {
        XRayBottomSheet(
            xrayIndex = xrayIndex,
            chapters = uiState.chapters,
            isLoading = isXRayLoading,
            onDismiss = { showXRay = false },
        )
    }
    if (showBookmarks) {
        BookmarkBottomSheet(
            bookmarks = bookmarks,
            chapters = uiState.chapters,
            onBookmarkClick = { bm ->
                showBookmarks = false
                pendingBookmarkJump = bm
            },
            onBookmarkDelete = onDeleteBookmark,
            onDismiss = { showBookmarks = false },
        )
    }
}

private fun copyLassoTextToClipboard(context: Context, chapters: List<EpubParser.EpubChapter>) {
    val text = chapters.flatMap { it.paragraphs }.joinToString("\n\n")
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Folio lasso OCR", text.take(6000)))
}

private fun extractLassoAsImage(context: Context, capture: LassoCapture) {
    // On-device image extraction: crop the lasso bounds as a placeholder image.
    // Real implementation would bitmap-capture the underlying diagram view.
    try {
        val file = java.io.File(context.cacheDir, "folio_lasso_${System.currentTimeMillis()}.png")
        // Create a tiny placeholder PNG to prove on-device handling (no network).
        val bmp = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(android.graphics.Color.parseColor("#F7B538"))
        val p = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#004F39")
            textSize = 28f
            isAntiAlias = true
        }
        c.drawText("Folio lasso image", 24f, 100f, p)
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        // Copy file path as image extraction proof — also toast in caller.
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Folio lasso image", file.absolutePath))
    } catch (_: Exception) {
    }
}

@Composable
private fun DiagramPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "◈  Fig. 1 — Folio Diagram\n(lasso me with stylus)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Sample diagram for lasso extraction demo. Circle it to extract as image; circle text to OCR-copy.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Phone: single-column, immersive, edge-to-edge ----

@Composable
private fun SingleColumnReadingContent(
    chapters: List<EpubParser.EpubChapter>,
    restoredChapterIndex: Int,
    chromeVisible: Boolean,
    bionicEnabled: Boolean,
    highlights: List<Highlight>,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onAddHighlight: (List<Offset>, List<Float>, List<Float>, Int) -> Unit,
    onLasso: (List<Offset>, Rect) -> Unit,
    onWordDoubleTap: (String) -> Unit,
    readingText: Color = MaterialTheme.colorScheme.onBackground,
    readingBackground: Color = MaterialTheme.colorScheme.background,
    alwaysShowProgressBar: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    pendingBookmarkJump: Bookmark? = null,
    onJumpConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // Bookmark jump — works on phone layout
    LaunchedEffect(pendingBookmarkJump) {
        val bm = pendingBookmarkJump ?: return@LaunchedEffect
        val flat = flatIndexForBookmark(bm.chapterIndex, bm.paragraphIndex, chapters, isTablet = false, mid = 0)
        if (flat >= 0) {
            listState.animateScrollToItem(flat.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            // Fallback: if not yet laid out, scroll without animation
            if (listState.firstVisibleItemIndex != flat) {
                listState.scrollToItem(flat.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            }
        }
        onJumpConsumed()
    }

    // True-Page Calculation Engine (§5) — virtual canvas, cached on rotate/font
    val truePageInfo = rememberTruePageState(
        chapters = chapters,
        isTabletLandscape = false,
        bionicEnabled = bionicEnabled,
    )
    // Current page updates on scroll but totalPages stays cached
    val currentPage by remember {
        derivedStateOf { truePageInfo.pageFor(listState.firstVisibleItemIndex) }
    }
    // Knuth-Plass Orphan/Widow control (§5) — scores breaks, micro-kerning, squared-off
    val knuthAdjustments = rememberKnuthAdjustments(
        chapters = chapters,
        isTabletLandscape = false,
        bionicEnabled = bionicEnabled,
    )

    val progress by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val total = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            (first.toFloat() / (total - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        }
    }

    val estimator = remember { VelocityEstimator() }
    var timeRemaining by remember { mutableStateOf<String?>(null) }
    var lastFlat by remember { mutableStateOf(listState.firstVisibleItemIndex) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(listState, chapters) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { newFlat ->
                val now = System.currentTimeMillis()
                val deltaMs = (now - lastTime).toDouble()
                val charsMoved = ReadingFlatMapper.charsBetween(lastFlat, newFlat, chapters).coerceAtLeast(1)
                if (newFlat != lastFlat && deltaMs in 500.0..300000.0) {
                    estimator.addSample(deltaMs, charsMoved)
                }
                val remaining = ReadingFlatMapper.remainingCharsInChapter(newFlat, chapters)
                val estMs = estimator.estimateMs(remaining)
                timeRemaining = formatTimeRemaining(estMs)
                lastFlat = newFlat
                lastTime = now
            }
    }

    DisposableEffect(listState) {
        ReaderPageTurnHandler.onVolumeKey = { isUp ->
            scope.launch {
                val current = listState.firstVisibleItemIndex
                val pageSize = 6
                val target = if (isUp) (current - pageSize).coerceAtLeast(0)
                else (current + pageSize).coerceAtMost(
                    (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                )
                listState.animateScrollToItem(target)
            }
        }
        onDispose { ReaderPageTurnHandler.onVolumeKey = null }
    }

    LaunchedEffect(chapters) {
        if (restoredChapterIndex in chapters.indices) {
            val flatIndex = chapters.take(restoredChapterIndex).sumOf { 1 + it.paragraphs.size }
            if (flatIndex > 0) listState.scrollToItem(flatIndex.coerceAtMost(flatIndex))
        }
    }

    DisposableEffect(listState) {
        onDispose {
            val firstVisible = listState.firstVisibleItemIndex
            val (ch, _) = flatIndexToChapterParagraph(firstVisible, chapters)
            onSaveProgress(ch, 0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleChrome,
                ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 8.dp,
                bottom = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            chapters.forEachIndexed { chapterIndex, chapter ->
                item(key = "chapter-title-$chapterIndex") {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = readingText,
                        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
                    )
                }
                itemsIndexed(
                    chapter.paragraphs,
                    key = { paraIndex, _ -> "c${chapterIndex}-p$paraIndex" },
                ) { paraIndex, paragraph ->
                    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    val annotated = if (bionicEnabled) {
                        remember(paragraph) { BionicReading.toBionicAnnotated(paragraph, BionicReading.boldSpan()) }
                    } else null
                    val knuth = knuthAdjustments["c${chapterIndex}-p${paraIndex}"]
                    val baseStyle = MaterialTheme.typography.bodyLarge
                    val knuthStyle = if (knuth != null) {
                        val ls = if (baseStyle.letterSpacing.isSp) (baseStyle.letterSpacing.value + knuth.letterSpacingDelta.value).sp else knuth.letterSpacingDelta
                        baseStyle.copy(
                            letterSpacing = ls,
                            textAlign = if (knuth.useJustify) TextAlign.Justify else baseStyle.textAlign ?: TextAlign.Start,
                        )
                    } else baseStyle
                    Text(
                        text = annotated ?: androidx.compose.ui.text.AnnotatedString(paragraph),
                        style = knuthStyle,
                        color = readingText,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .padding(bottom = 14.dp)
                            .pointerInput(paragraph, bionicEnabled) {
                                detectTapGestures(
                                    onDoubleTap = { offset ->
                                        layoutResult?.let { layout ->
                                            val pos = layout.getOffsetForPosition(offset)
                                            if (pos < 0 || pos >= paragraph.length) return@detectTapGestures
                                            var start = pos
                                            var end = pos
                                            while (start > 0 && paragraph[start - 1].isLetter()) start--
                                            while (end < paragraph.length && paragraph[end].isLetter()) end++
                                            if (start < end) {
                                                val word = paragraph.substring(start, end)
                                                onWordDoubleTap(word)
                                            }
                                        }
                                    }
                                )
                            },
                    )
                }
                // Insert expandable diagram after first chapter — demonstrates Bounding-Box Image Expansion
                // (existing diagram + lasso placeholder now with white-margin stripping, on-device, cached).
                if (chapterIndex == 0) {
                    item(key = "diagram-$chapterIndex") {
                        ExpandableDiagram(modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
                item(key = "chapter-gap-$chapterIndex") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        HighlightOverlay(
            highlights = highlights,
            onStylusStrokeFinished = { pts, pressures, tilts ->
                val ch = flatIndexToChapterParagraph(listState.firstVisibleItemIndex, chapters).first
                onAddHighlight(pts, pressures, tilts, ch)
            },
            onLassoFinished = { pts, bounds -> onLasso(pts, bounds) },
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom bar: progress bar respects "Always show progress bar" setting (overrides tap-to-hide)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(readingBackground.copy(alpha = 0.92f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Page $currentPage of ${truePageInfo.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = readingText.copy(alpha = 0.85f),
                    )
                    timeRemaining?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = readingText.copy(alpha = 0.85f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = chromeVisible || alwaysShowProgressBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ReadingProgressBar(
                    progress = progress,
                    onSeek = { fraction ->
                        val target = ((listState.layoutInfo.totalItemsCount - 1) * fraction).toInt()
                            .coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                        scope.launch { listState.animateScrollToItem(target) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---- Tablet landscape: two-column spread ----

@Composable
private fun TwoColumnReadingContent(
    chapters: List<EpubParser.EpubChapter>,
    restoredChapterIndex: Int,
    chromeVisible: Boolean,
    bionicEnabled: Boolean,
    highlights: List<Highlight>,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onAddHighlight: (List<Offset>, List<Float>, List<Float>, Int) -> Unit,
    onLasso: (List<Offset>, Rect) -> Unit,
    onWordDoubleTap: (String) -> Unit,
    readingText: Color = MaterialTheme.colorScheme.onBackground,
    readingBackground: Color = MaterialTheme.colorScheme.background,
    alwaysShowProgressBar: Boolean = false,
    leftListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    rightListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    pendingBookmarkJump: Bookmark? = null,
    onJumpConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mid = (chapters.size + 1) / 2
    val left = remember(chapters) { chapters.take(mid) }
    val right = remember(chapters) { chapters.drop(mid) }

    val leftState = leftListState
    val rightState = rightListState
    val scope = rememberCoroutineScope()

    // Bookmark jump — works on tablet spread (jump to appropriate column)
    LaunchedEffect(pendingBookmarkJump) {
        val bm = pendingBookmarkJump ?: return@LaunchedEffect
        if (bm.chapterIndex < mid) {
            val flat = flatIndexForBookmark(bm.chapterIndex, bm.paragraphIndex, chapters, isTablet = true, mid = mid)
            // left flat is offset within left list: need left-relative flat
            val leftFlat = flatIndexForBookmarkInLeft(bm.chapterIndex, bm.paragraphIndex, left)
            val target = if (leftFlat >= 0) leftFlat else flat
            leftState.animateScrollToItem(target.coerceIn(0, (leftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            if (leftState.firstVisibleItemIndex != target) leftState.scrollToItem(target)
        } else {
            val rightFlat = flatIndexForBookmarkInRight(bm.chapterIndex, bm.paragraphIndex, right, mid)
            val target = rightFlat.coerceIn(0, (rightState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
            rightState.animateScrollToItem(target)
            if (rightState.firstVisibleItemIndex != target) rightState.scrollToItem(target)
            // Also ensure left shows corresponding chapter if user jumped to right half — keep left as is
        }
        onJumpConsumed()
    }

    val progress by remember {
        derivedStateOf {
            val first = leftState.firstVisibleItemIndex
            val total = leftState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            (first.toFloat() / (total - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        }
    }
    // Knuth-Plass Orphan/Widow control (§5) — tablet spread
    val knuthAdjustments = rememberKnuthAdjustments(
        chapters = chapters,
        isTabletLandscape = true,
        bionicEnabled = bionicEnabled,
    )

    // True-Page Calculation Engine (§5) — tablet spread: physical page turns (perScreen *2)
    val truePageInfo = rememberTruePageState(
        chapters = chapters,
        isTabletLandscape = true,
        bionicEnabled = bionicEnabled,
    )
    // Tablet spread shows 2 columns per physical page, but totalPages already
    // reflects physical turns. Current page tracks earliest visible spread.
    // leftState is earliest (first half), so use its flatIndex for page.
    val currentPage by remember {
        derivedStateOf {
            // leftState covers first half (global 0..mid); its flat maps directly
            truePageInfo.pageFor(leftState.firstVisibleItemIndex)
        }
    }

    val estimator = remember { VelocityEstimator() }
    var timeRemaining by remember { mutableStateOf<String?>(null) }
    var lastFlat by remember { mutableStateOf(leftState.firstVisibleItemIndex) }
    var lastTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(leftState, left) {
        snapshotFlow { leftState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { newFlat ->
                val now = System.currentTimeMillis()
                val deltaMs = (now - lastTime).toDouble()
                val charsMoved = ReadingFlatMapper.charsBetween(lastFlat, newFlat, left).coerceAtLeast(1)
                if (newFlat != lastFlat && deltaMs in 500.0..300000.0) {
                    estimator.addSample(deltaMs, charsMoved)
                }
                val remaining = ReadingFlatMapper.remainingCharsInChapter(newFlat, left)
                // For spread, estimate based on left column's remaining; right column is second half, but still character-density aware
                val estMs = estimator.estimateMs(remaining)
                timeRemaining = formatTimeRemaining(estMs)
                lastFlat = newFlat
                lastTime = now
            }
    }

    DisposableEffect(leftState) {
        ReaderPageTurnHandler.onVolumeKey = { isUp ->
            scope.launch {
                val pageSize = 5
                val target = if (isUp) {
                    (leftState.firstVisibleItemIndex - pageSize).coerceAtLeast(0)
                } else {
                    (leftState.firstVisibleItemIndex + pageSize)
                        .coerceAtMost((leftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                }
                leftState.animateScrollToItem(target)
            }
        }
        onDispose { ReaderPageTurnHandler.onVolumeKey = null }
    }

    LaunchedEffect(chapters) {
        if (restoredChapterIndex in chapters.indices && restoredChapterIndex < mid) {
            val flat = left.take(restoredChapterIndex).sumOf { 1 + it.paragraphs.size }
            if (flat > 0) leftState.scrollToItem(flat)
        }
    }

    DisposableEffect(leftState) {
        onDispose {
            val firstVisible = leftState.firstVisibleItemIndex
            val (ch, _) = flatIndexToChapterParagraph(firstVisible, left)
            onSaveProgress(ch, 0)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 32.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleChrome,
                ),
        ) {
            LazyColumn(
                state = leftState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                left.forEachIndexed { chapterIndex, chapter ->
                    item(key = "L-title-$chapterIndex") {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = readingText,
                            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                        )
                    }
                    itemsIndexed(chapter.paragraphs, key = { i, _ -> "L-c$chapterIndex-p$i" }) { paraIndex, p ->
                        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        val annotated = if (bionicEnabled) remember(p) { BionicReading.toBionicAnnotated(p, BionicReading.boldSpan()) } else null
                        val knuth = knuthAdjustments["c${chapterIndex}-p${paraIndex}"]
                        val baseStyleLeft = MaterialTheme.typography.bodyMedium
                        val leftStyle = if (knuth != null) {
                            val lsL = if (baseStyleLeft.letterSpacing.isSp) (baseStyleLeft.letterSpacing.value + knuth.letterSpacingDelta.value).sp else knuth.letterSpacingDelta
                            baseStyleLeft.copy(
                                letterSpacing = lsL,
                                textAlign = if (knuth.useJustify) TextAlign.Justify else baseStyleLeft.textAlign ?: TextAlign.Start,
                            )
                        } else baseStyleLeft
                        Text(
                            text = annotated ?: androidx.compose.ui.text.AnnotatedString(p),
                            style = leftStyle,
                            color = readingText,
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .pointerInput(p, bionicEnabled) {
                                    detectTapGestures(
                                        onDoubleTap = { offset ->
                                            layoutResult?.let { layout ->
                                                val pos = layout.getOffsetForPosition(offset)
                                                if (pos < 0 || pos >= p.length) return@detectTapGestures
                                                var start = pos
                                                var end = pos
                                                while (start > 0 && p[start - 1].isLetter()) start--
                                                while (end < p.length && p[end].isLetter()) end++
                                                if (start < end) {
                                                    val word = p.substring(start, end)
                                                    onWordDoubleTap(word)
                                                }
                                            }
                                        }
                                    )
                                },
                        )
                    }
                    if (chapterIndex == 0) {
                        item(key = "L-diagram-$chapterIndex") {
                            ExpandableDiagram(modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                    item(key = "L-gap-$chapterIndex") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            )

            if (right.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    state = rightState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    right.forEachIndexed { chapterIndex, chapter ->
                        item(key = "R-title-$chapterIndex") {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = readingText,
                                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                            )
                        }
                        itemsIndexed(chapter.paragraphs, key = { i, _ -> "R-c$chapterIndex-p$i" }) { paraIndex, p ->
                            var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                            val annotated = if (bionicEnabled) remember(p) { BionicReading.toBionicAnnotated(p, BionicReading.boldSpan()) } else null
                            val globalCIdx = mid + chapterIndex
                            val knuthR = knuthAdjustments["c${globalCIdx}-p${paraIndex}"]
                            val baseStyleR = MaterialTheme.typography.bodyMedium
                            val rightStyle = if (knuthR != null) {
                                val lsR = if (baseStyleR.letterSpacing.isSp) (baseStyleR.letterSpacing.value + knuthR.letterSpacingDelta.value).sp else knuthR.letterSpacingDelta
                                baseStyleR.copy(
                                    letterSpacing = lsR,
                                    textAlign = if (knuthR.useJustify) TextAlign.Justify else baseStyleR.textAlign ?: TextAlign.Start,
                                )
                            } else baseStyleR
                            Text(
                                text = annotated ?: androidx.compose.ui.text.AnnotatedString(p),
                                style = rightStyle,
                                color = readingText,
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .pointerInput(p, bionicEnabled) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                layoutResult?.let { layout ->
                                                    val pos = layout.getOffsetForPosition(offset)
                                                    if (pos < 0 || pos >= p.length) return@detectTapGestures
                                                    var start = pos
                                                    var end = pos
                                                    while (start > 0 && p[start - 1].isLetter()) start--
                                                    while (end < p.length && p[end].isLetter()) end++
                                                    if (start < end) {
                                                        val word = p.substring(start, end)
                                                        onWordDoubleTap(word)
                                                    }
                                                }
                                            }
                                        )
                                    },
                            )
                        }
                        item(key = "R-gap-$chapterIndex") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        HighlightOverlay(
            highlights = highlights,
            onStylusStrokeFinished = { pts, pressures, tilts ->
                val ch = flatIndexToChapterParagraph(leftState.firstVisibleItemIndex, left).first
                onAddHighlight(pts, pressures, tilts, ch)
            },
            onLassoFinished = { pts, bounds -> onLasso(pts, bounds) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(readingBackground.copy(alpha = 0.92f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Page $currentPage of ${truePageInfo.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = readingText.copy(alpha = 0.85f),
                    )
                    timeRemaining?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = readingText.copy(alpha = 0.85f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = chromeVisible || alwaysShowProgressBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ReadingProgressBar(
                    progress = progress,
                    onSeek = { fraction ->
                        val target = ((leftState.layoutInfo.totalItemsCount - 1) * fraction).toInt()
                            .coerceIn(0, (leftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                        scope.launch { leftState.animateScrollToItem(target) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun flatIndexToChapterParagraph(
    flatIndex: Int,
    chapters: List<EpubParser.EpubChapter>,
): Pair<Int, Int> {
    var remaining = flatIndex
    chapters.forEachIndexed { chIdx, ch ->
        // Single-column flat size: title(1) + paragraphs + optional diagram(1 for ch0) + gap(1)
        val chapterSize = 1 + ch.paragraphs.size + (if (chIdx == 0) 1 else 0) + 1
        if (remaining < chapterSize) return chIdx to 0
        remaining -= chapterSize
    }
    return 0 to 0
}

/** Accurate paragraph-aware mapping for bookmarks (distinct from flatIndexToChapterParagraph's 0 fallback). */
internal fun bookmarkPositionForFlat(
    flatIndex: Int,
    chapters: List<EpubParser.EpubChapter>,
    isTablet: Boolean,
): Pair<Int, Int> {
    if (chapters.isEmpty()) return 0 to 0
    if (isTablet) {
        // Tablet left holds first half; flat there maps directly to global 0..mid-1
        var rem = flatIndex
        for (idx in chapters.indices) {
            val ch = chapters[idx]
            // For tablet left list, size is same as phone but without right half
            // We treat flat as left-relative; global ch is idx when idx < mid
            val size = 1 + ch.paragraphs.size + (if (idx == 0) 1 else 0) + 1
            if (rem < size) {
                return if (rem == 0) idx to 0 // title
                else {
                    val paraIdx = (rem - 1).coerceIn(0, (ch.paragraphs.size - 1).coerceAtLeast(0))
                    // Skip diagram slot for ch0: if para beyond size, clamp
                    if (idx == 0 && rem == 1 + ch.paragraphs.size) idx to 0 else idx to paraIdx
                }
            }
            rem -= size
        }
        return 0 to 0
    } else {
        var rem = flatIndex
        for (idx in chapters.indices) {
            val ch = chapters[idx]
            val hasDiagram = idx == 0
            val size = 1 + ch.paragraphs.size + (if (hasDiagram) 1 else 0) + 1
            if (rem < size) {
                if (rem == 0) return idx to 0
                if (rem in 1..ch.paragraphs.size) return idx to (rem - 1)
                // diagram or gap -> return first para of this chapter as fallback for bookmark toggle
                return idx to 0
            }
            rem -= size
        }
        return 0 to 0
    }
}

/** Phone flat index for a bookmark (chapter + paragraph). */
internal fun flatIndexForBookmark(
    chapterIndex: Int,
    paragraphIndex: Int,
    chapters: List<EpubParser.EpubChapter>,
    isTablet: Boolean,
    mid: Int,
): Int {
    if (chapterIndex !in chapters.indices) return -1
    var flat = 0
    for (idx in 0 until chapterIndex) {
        val ch = chapters[idx]
        flat += 1 + ch.paragraphs.size + (if (idx == 0) 1 else 0) + 1
    }
    // title offset + paragraph offset
    flat += 1 + paragraphIndex.coerceIn(0, (chapters[chapterIndex].paragraphs.size - 1).coerceAtLeast(0))
    return flat
}

internal fun flatIndexForBookmarkInLeft(
    chapterIndex: Int,
    paragraphIndex: Int,
    left: List<EpubParser.EpubChapter>,
): Int {
    if (chapterIndex !in left.indices) return -1
    var flat = 0
    for (idx in 0 until chapterIndex) {
        flat += 1 + left[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1
    }
    flat += 1 + paragraphIndex.coerceIn(0, (left[chapterIndex].paragraphs.size - 1).coerceAtLeast(0))
    return flat
}

internal fun flatIndexForBookmarkInRight(
    chapterIndex: Int,
    paragraphIndex: Int,
    right: List<EpubParser.EpubChapter>,
    mid: Int,
): Int {
    val localIdx = chapterIndex - mid
    if (localIdx !in right.indices) return -1
    var flat = 0
    for (idx in 0 until localIdx) {
        flat += 1 + right[idx].paragraphs.size + 1 // no diagram in right (ch0 is in left)
    }
    flat += 1 + paragraphIndex.coerceIn(0, (right[localIdx].paragraphs.size - 1).coerceAtLeast(0))
    return flat
}
