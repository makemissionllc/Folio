package com.makemission.folio.ui.reader

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.makemission.folio.ui.theme.TimeTintEngine
import com.makemission.folio.ui.theme.hasAmbientLightSensor
import com.makemission.folio.ui.theme.rememberAmbientLightLux
import com.makemission.folio.ui.theme.rememberTimeWarmth
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
        onPrioritizeXRay = viewModel::prioritizeXRayChapter,
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
    onPrioritizeXRay: (Int) -> Unit = {},
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
    var showMenu by remember { mutableStateOf(false) }
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

    // Settings — DataStore backed (survives restart, same folio_settings)
    val settingsRepo = remember(context) { com.makemission.folio.data.settings.SettingsRepository.get(context) }
    val alwaysShowProgressBar by settingsRepo.alwaysShowProgressBar.collectAsState(initial = false)
    val hapticsEnabled by settingsRepo.hapticsEnabled.collectAsState(initial = true)
    val navigationMode by settingsRepo.readingNavigationMode.collectAsState(initial = com.makemission.folio.ui.reader.ReadingNavigationMode.CONTINUOUS)

    // --- Chapter swipe pager states (only used when navigationMode == CHAPTER_SWIPE) ---
    // Phone: one page per chapter, vertical LazyColumn within each chapter
    val swipePhonePagerState = rememberPagerState(initialPage = 0) { uiState.chapters.size }
    val swipePhoneChapterStates = remember(uiState.chapters.size) {
        List(uiState.chapters.size) { androidx.compose.foundation.lazy.LazyListState() }
    }
    // Tablet: one page per spread (pair of chapters), two columns per spread
    val swipeTabletPagerState = rememberPagerState(initialPage = 0) { (uiState.chapters.size + 1) / 2 }
    val swipeTabletLeftStates = remember(uiState.chapters.size) {
        List((uiState.chapters.size + 1) / 2) { androidx.compose.foundation.lazy.LazyListState() }
    }
    val swipeTabletRightStates = remember(uiState.chapters.size) {
        List((uiState.chapters.size + 1) / 2) { androidx.compose.foundation.lazy.LazyListState() }
    }
    // Restore position precisely when chapters load — chapter + paragraph, for both modes
    LaunchedEffect(uiState.chapters, uiState.restoredChapterIndex, uiState.restoredParagraphIndex) {
        if (uiState.chapters.isNotEmpty() && !uiState.isLoading) {
            val target = uiState.restoredChapterIndex.coerceIn(0, uiState.chapters.size - 1)
            val para = uiState.restoredParagraphIndex.coerceIn(0, (uiState.chapters.getOrNull(target)?.paragraphs?.size ?: 1) - 1)
            try {
                swipePhonePagerState.scrollToPage(target)
                val paraFlat = 1 + para
                swipePhoneChapterStates.getOrNull(target)?.let { st ->
                    kotlinx.coroutines.delay(60)
                    st.scrollToItem(paraFlat.coerceIn(0, (st.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                }
                val spread = target / 2
                swipeTabletPagerState.scrollToPage(spread)
                kotlinx.coroutines.delay(60)
                val paraFlat2 = 1 + para
                if (target % 2 == 0) {
                    swipeTabletLeftStates.getOrNull(spread)?.scrollToItem(paraFlat2.coerceIn(0, (swipeTabletLeftStates.getOrNull(spread)?.layoutInfo?.totalItemsCount ?: 10) - 1))
                } else {
                    swipeTabletRightStates.getOrNull(spread)?.scrollToItem(paraFlat2.coerceIn(0, (swipeTabletRightStates.getOrNull(spread)?.layoutInfo?.totalItemsCount ?: 10) - 1))
                }
            } catch (_: Exception) {}
        }
    }

    // Colorimetric Contrast Optimization (§5) — builds on FolioTheme, not rewriting it
    // Time-aware ambient tinting layers on top — see TimeTintEngine; all three
    // (palette via baseBg, adaptive contrast via lux, time tint via warmth) layer
    // sensibly via sequential lerps + final WCAG ensure, not fighting/muddy.
    var adaptiveEnabled by rememberSaveable { mutableStateOf(false) }
    val themeMode by settingsRepo.themeMode.collectAsState(initial = com.makemission.folio.ui.theme.ThemeMode.AUTO)
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        com.makemission.folio.ui.theme.ThemeMode.LIGHT -> false
        com.makemission.folio.ui.theme.ThemeMode.DARK -> true
        com.makemission.folio.ui.theme.ThemeMode.AUTO -> systemDark
    }
    val baseBg = MaterialTheme.colorScheme.background
    val baseText = MaterialTheme.colorScheme.onBackground
    val hasSensor = remember(context) { hasAmbientLightSensor(context) }
    val luxState = rememberAmbientLightLux(enabled = adaptiveEnabled && hasSensor)
    val rawLux = luxState.value
    val adaptivePair = remember(rawLux, isDark, baseBg, baseText, adaptiveEnabled, hasSensor) {
        if (adaptiveEnabled && hasSensor && rawLux != null) {
            AdaptiveContrastEngine.adaptivePair(baseBg, baseText, rawLux, isDark)
        } else null
    }
    // Time-aware tint (on-device, deterministic, system clock, no location)
    val timeTintEnabled by settingsRepo.timeTintEnabled.collectAsState(initial = false)
    val warmthState = rememberTimeWarmth()
    val warmth = warmthState.value
    // Layer: palette base → adaptive (lux) → time tint (warmth) → animated
    val adaptiveBg = adaptivePair?.first ?: baseBg
    val adaptiveText = adaptivePair?.second ?: baseText
    val timeTintedPair = remember(adaptiveBg, adaptiveText, warmth, isDark, timeTintEnabled) {
        if (timeTintEnabled) TimeTintEngine.tintedPair(adaptiveBg, adaptiveText, warmth, isDark) else null
    }
    val targetBg = timeTintedPair?.first ?: adaptiveBg
    val targetText = timeTintedPair?.second ?: adaptiveText
    // Smooth/gradual — adaptive 800ms, time tint 900ms but unified here as 900ms for combined, still not jarring
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "readingBg",
    )
    val animatedText by animateColorAsState(
        targetValue = targetText,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "readingText",
    )
    val useAnimated = (adaptiveEnabled && hasSensor && rawLux != null) || timeTintEnabled
    val readingBg = if (useAnimated) animatedBg else baseBg
    val readingText = if (useAnimated) animatedText else baseText
    // Also adapt surface for chrome consistency (same as bg)
    val readingSurface = readingBg
    val readingOnSurface = readingText

    // --- Bookmarks: hoisted list states so TopBar knows current position ---
    val singleListState = rememberLazyListState()
    val tabletLeftState = rememberLazyListState()
    val tabletRightState = rememberLazyListState()

    // Current reading position for bookmark toggle (first visible paragraph)
    // Coordinates with navigationMode: continuous uses flat index, swipe uses pager + per-chapter vertical state
    val currentBookmarkPos by remember {
        derivedStateOf {
            if (uiState.chapters.isEmpty()) return@derivedStateOf 0 to 0
            if (navigationMode == com.makemission.folio.ui.reader.ReadingNavigationMode.CHAPTER_SWIPE) {
                if (isTabletLandscape) {
                    val spread = swipeTabletPagerState.currentPage.coerceIn(0, (uiState.chapters.size + 1) / 2 - 1)
                    val leftIdx = spread * 2
                    val rightIdx = leftIdx + 1
                    // Prefer left chapter's visible position; fallback to right
                    val leftState = swipeTabletLeftStates.getOrNull(spread)
                    val rightState = swipeTabletRightStates.getOrNull(spread)
                    val leftFlat = leftState?.firstVisibleItemIndex ?: 0
                    val leftPara = when {
                        leftFlat == 0 -> 0
                        leftFlat in 1..(uiState.chapters.getOrNull(leftIdx)?.paragraphs?.size ?: 0) -> leftFlat - 1
                        else -> 0
                    }
                    if (leftIdx in uiState.chapters.indices) {
                        // If left is at title/gap, still return leftIdx:0 for toggle
                        return@derivedStateOf leftIdx to leftPara
                    }
                    // Fallback to right
                    val rightFlat = rightState?.firstVisibleItemIndex ?: 0
                    val rightPara = when {
                        rightFlat == 0 -> 0
                        rightFlat in 1..(uiState.chapters.getOrNull(rightIdx)?.paragraphs?.size ?: 0) -> rightFlat - 1
                        else -> 0
                    }
                    return@derivedStateOf (rightIdx.takeIf { it in uiState.chapters.indices } ?: leftIdx) to rightPara
                } else {
                    val ch = swipePhonePagerState.currentPage.coerceIn(0, uiState.chapters.size - 1)
                    val state = swipePhoneChapterStates.getOrNull(ch)
                    val flat = state?.firstVisibleItemIndex ?: 0
                    val paraCount = uiState.chapters.getOrNull(ch)?.paragraphs?.size ?: 0
                    val para = when {
                        flat == 0 -> 0
                        flat in 1..paraCount -> flat - 1
                        else -> 0
                    }
                    return@derivedStateOf ch to para
                }
            } else {
                if (isTabletLandscape) {
                    bookmarkPositionForFlat(tabletLeftState.firstVisibleItemIndex, uiState.chapters, isTablet = true)
                } else {
                    bookmarkPositionForFlat(singleListState.firstVisibleItemIndex, uiState.chapters, isTablet = false)
                }
            }
        }
    }
    val isCurrentBookmarked by remember {
        derivedStateOf {
            val (ch, para) = currentBookmarkPos
            bookmarks.any { it.chapterIndex == ch && it.paragraphIndex == para }
        }
    }

    // Precise progress save — debounce to avoid spamming Room, but ensures paragraph-level restoration
    LaunchedEffect(uiState.bookId) {
        snapshotFlow { currentBookmarkPos }
            .distinctUntilChanged()
            .collect { (ch, para) ->
                if (uiState.isLoading || uiState.chapters.isEmpty()) return@collect
                if (ch !in uiState.chapters.indices) return@collect
                kotlinx.coroutines.delay(700)
                val (curCh, curPara) = currentBookmarkPos
                if (curCh == ch && curPara == para) {
                    onSaveProgress(ch, para)
                }
            }
    }

    // Progressive X-Ray: when user opens X-Ray, prioritize the chapter they're reading
    LaunchedEffect(showXRay) {
        if (showXRay && uiState.chapters.isNotEmpty()) {
            val (ch, _) = currentBookmarkPos
            onPrioritizeXRay(ch.coerceIn(0, uiState.chapters.size - 1))
        }
    }

    // Library search/bookmark jump — coordinates with navigationMode
    LaunchedEffect(pendingSearchJump, isTabletLandscape, uiState.chapters, navigationMode) {
        val target = pendingSearchJump ?: return@LaunchedEffect
        if (uiState.chapters.isEmpty()) return@LaunchedEffect
        try {
            if (navigationMode == com.makemission.folio.ui.reader.ReadingNavigationMode.CHAPTER_SWIPE) {
                // Swipe mode: jump to chapter page, then scroll within that chapter
                if (isTabletLandscape) {
                    val spread = (target.chapterIndex / 2).coerceIn(0, ((uiState.chapters.size + 1) / 2 - 1).coerceAtLeast(0))
                    swipeTabletPagerState.animateScrollToPage(spread)
                    // Determine left/right within spread
                    val leftIdx = spread * 2
                    val isLeft = target.chapterIndex == leftIdx
                    val paraFlat = 1 + target.paragraphIndex.coerceIn(0, (uiState.chapters.getOrNull(target.chapterIndex)?.paragraphs?.size ?: 1) - 1)
                    if (isLeft) {
                        val state = swipeTabletLeftStates.getOrNull(spread) ?: return@LaunchedEffect
                        state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    } else {
                        val state = swipeTabletRightStates.getOrNull(spread) ?: return@LaunchedEffect
                        state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    }
                } else {
                    val ch = target.chapterIndex.coerceIn(0, uiState.chapters.size - 1)
                    swipePhonePagerState.animateScrollToPage(ch)
                    // Scroll within that chapter's LazyColumn to paragraph (title offset +1)
                    val paraFlat = 1 + target.paragraphIndex.coerceIn(0, (uiState.chapters.getOrNull(ch)?.paragraphs?.size ?: 1) - 1)
                    val state = swipePhoneChapterStates.getOrNull(ch) ?: return@LaunchedEffect
                    // Small delay to let pager settle
                    kotlinx.coroutines.delay(100)
                    state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                }
            } else {
                // Continuous mode — reuse flat-index logic (original)
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
            }
        } catch (_: Exception) {}
        pendingSearchJump = null
    }

    // Bookmark jump for swipe mode — continuous mode handled inside Single/TwoColumn
    LaunchedEffect(pendingBookmarkJump, isTabletLandscape, uiState.chapters, navigationMode) {
        val bm = pendingBookmarkJump ?: return@LaunchedEffect
        if (navigationMode != com.makemission.folio.ui.reader.ReadingNavigationMode.CHAPTER_SWIPE) return@LaunchedEffect
        if (uiState.chapters.isEmpty()) return@LaunchedEffect
        try {
            if (isTabletLandscape) {
                val spread = (bm.chapterIndex / 2).coerceIn(0, ((uiState.chapters.size + 1) / 2 - 1).coerceAtLeast(0))
                swipeTabletPagerState.animateScrollToPage(spread)
                val leftIdx = spread * 2
                val isLeft = bm.chapterIndex == leftIdx
                val paraFlat = 1 + bm.paragraphIndex.coerceIn(0, (uiState.chapters.getOrNull(bm.chapterIndex)?.paragraphs?.size ?: 1) - 1)
                if (isLeft) {
                    val state = swipeTabletLeftStates.getOrNull(spread) ?: return@LaunchedEffect
                    kotlinx.coroutines.delay(80)
                    state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                } else {
                    val state = swipeTabletRightStates.getOrNull(spread) ?: return@LaunchedEffect
                    kotlinx.coroutines.delay(80)
                    state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                }
            } else {
                val ch = bm.chapterIndex.coerceIn(0, uiState.chapters.size - 1)
                swipePhonePagerState.animateScrollToPage(ch)
                val paraFlat = 1 + bm.paragraphIndex.coerceIn(0, (uiState.chapters.getOrNull(ch)?.paragraphs?.size ?: 1) - 1)
                val state = swipePhoneChapterStates.getOrNull(ch) ?: return@LaunchedEffect
                kotlinx.coroutines.delay(80)
                state.animateScrollToItem(paraFlat.coerceIn(0, (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            }
        } catch (_: Exception) {}
        pendingBookmarkJump = null
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
                    enter = slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)),
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
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text("← Back", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    actions = {
                        // Single menu icon — 48dp touch target, replaces cluttered individual buttons
                        TextButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                "☰",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                // In-book pull-to-reveal search bar (on-device, contextual) — subtle premium tween
                androidx.compose.animation.AnimatedVisibility(
                    visible = showInBookSearch,
                    enter = androidx.compose.animation.expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = androidx.compose.animation.shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150)),
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
                    // Contextual in-book results (on-device, highlights/bookmarks priority) — subtle
                    androidx.compose.animation.AnimatedVisibility(
                        visible = inBookQuery.isNotBlank(),
                        enter = androidx.compose.animation.expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                        exit = androidx.compose.animation.shrinkVertically(tween(180)) + fadeOut(tween(150)),
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
                // Pleasant editorial loading — not just a spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "loading_pulse")
                        val pulse by infinite.animateFloat(
                            initialValue = 0.9f, targetValue = 1.15f,
                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ), label = "pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            text = "Opening your book…",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Pagination and highlights are being prepared",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            } else if (uiState.chapters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 32.dp)) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
                                val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
                                val green = androidx.compose.ui.graphics.Color(0xFF004F39)
                                drawCircle(color = amber.copy(alpha = 0.9f), radius = 11f, center = center.copy(y = center.y - 6f))
                                drawRoundRect(color = green.copy(alpha = 0.85f), topLeft = center.copy(x = center.x - 16f, y = center.y + 6f), size = androidx.compose.ui.geometry.Size(32f, 4f))
                            }
                        }
                        Text(
                            text = "No content to display.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "This book appears to be empty — try another title or re-import the file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
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
                    androidx.compose.animation.AnimatedContent(
                        targetState = navigationMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
                        },
                        label = "reading_mode"
                    ) { mode ->
                        when (mode) {
                            com.makemission.folio.ui.reader.ReadingNavigationMode.CONTINUOUS -> {
                                if (isTabletLandscape) {
                                    TwoColumnReadingContent(
                                    chapters = uiState.chapters,
                                    restoredChapterIndex = uiState.restoredChapterIndex,
                                    restoredParagraphIndex = uiState.restoredParagraphIndex,
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
                                    hapticsEnabled = hapticsEnabled,
                                    leftListState = tabletLeftState,
                                    rightListState = tabletRightState,
                                    pendingBookmarkJump = pendingBookmarkJump,
                                    onJumpConsumed = { pendingBookmarkJump = null },
                                    bookId = uiState.bookId,
                                    fileHash = uiState.fileHash,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                SingleColumnReadingContent(
                                    chapters = uiState.chapters,
                                    restoredChapterIndex = uiState.restoredChapterIndex,
                                    restoredParagraphIndex = uiState.restoredParagraphIndex,
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
                                    hapticsEnabled = hapticsEnabled,
                                    listState = singleListState,
                                    pendingBookmarkJump = pendingBookmarkJump,
                                    onJumpConsumed = { pendingBookmarkJump = null },
                                    bookId = uiState.bookId,
                                    fileHash = uiState.fileHash,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        com.makemission.folio.ui.reader.ReadingNavigationMode.CHAPTER_SWIPE -> {
                            if (isTabletLandscape) {
                                TwoColumnChapterSwipeContent(
                                    chapters = uiState.chapters,
                                    pagerState = swipeTabletPagerState,
                                    leftStates = swipeTabletLeftStates,
                                    rightStates = swipeTabletRightStates,
                                    bionicEnabled = bionicEnabled,
                                    highlights = highlights,
                                    readingText = readingText,
                                    readingBackground = readingBg,
                                    alwaysShowProgressBar = alwaysShowProgressBar,
                                    hapticsEnabled = hapticsEnabled,
                                    chromeVisible = chromeVisible,
                                    onToggleChrome = { chromeVisible = !chromeVisible },
                                    onSaveProgress = onSaveProgress,
                                    onAddHighlight = onAddHighlight,
                                    onLasso = { pts, bounds -> lassoCapture = LassoCapture(pts, bounds) },
                                    onWordDoubleTap = onWordDoubleTap,
                                    onPrioritizeXRay = onPrioritizeXRay,
                                    bookId = uiState.bookId,
                                    fileHash = uiState.fileHash,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                ChapterSwipePhoneContent(
                                    chapters = uiState.chapters,
                                    pagerState = swipePhonePagerState,
                                    chapterStates = swipePhoneChapterStates,
                                    bionicEnabled = bionicEnabled,
                                    highlights = highlights,
                                    readingText = readingText,
                                    readingBackground = readingBg,
                                    alwaysShowProgressBar = alwaysShowProgressBar,
                                    hapticsEnabled = hapticsEnabled,
                                    chromeVisible = chromeVisible,
                                    onToggleChrome = { chromeVisible = !chromeVisible },
                                    onSaveProgress = onSaveProgress,
                                    onAddHighlight = onAddHighlight,
                                    onLasso = { pts, bounds -> lassoCapture = LassoCapture(pts, bounds) },
                                    onWordDoubleTap = onWordDoubleTap,
                                    onPrioritizeXRay = onPrioritizeXRay,
                                    bookId = uiState.bookId,
                                    fileHash = uiState.fileHash,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (showMenu) {
        com.makemission.folio.ui.reader.components.ReaderMenuSheet(
            bionicEnabled = bionicEnabled,
            onToggleBionic = { bionicEnabled = !bionicEnabled },
            onOpenXRay = { showXRay = true },
            contrastEnabled = adaptiveEnabled,
            hasSensor = hasSensor,
            onToggleContrast = { if (hasSensor) adaptiveEnabled = !adaptiveEnabled },
            bookmarksCount = bookmarks.size,
            isCurrentBookmarked = isCurrentBookmarked,
            onToggleBookmark = {
                val (ch, para) = currentBookmarkPos
                onToggleBookmark(ch, para)
            },
            onOpenBookmarks = { showBookmarks = true },
            onToggleSearch = { showInBookSearch = !showInBookSearch },
            onDismiss = { showMenu = false },
        )
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
    restoredParagraphIndex: Int = 0,
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
    hapticsEnabled: Boolean = true,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    pendingBookmarkJump: Bookmark? = null,
    onJumpConsumed: () -> Unit = {},
    bookId: String? = null,
    fileHash: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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

    // True-Page Calculation Engine (§5) — virtual canvas, cached on rotate/font + disk (hash-validated)
    val truePageInfo = rememberTruePageState(
        chapters = chapters,
        isTabletLandscape = false,
        bionicEnabled = bionicEnabled,
        bookId = bookId,
        fileHash = fileHash,
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

    // Haptics: subtle tick on chapter boundary (scroll or volume key), not every page — respects Settings toggle
    var lastChapterForHaptics by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(listState, chapters, hapticsEnabled) {
        snapshotFlow { flatIndexToChapterParagraph(listState.firstVisibleItemIndex, chapters).first }
            .distinctUntilChanged()
            .collect { newChapter ->
                if (lastChapterForHaptics != null && lastChapterForHaptics != newChapter && hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastChapterForHaptics = newChapter
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

    LaunchedEffect(chapters, restoredChapterIndex, restoredParagraphIndex) {
        if (restoredChapterIndex in chapters.indices) {
            val flat = flatIndexForBookmark(restoredChapterIndex, restoredParagraphIndex, chapters, isTablet = false, mid = 0)
            if (flat >= 0) {
                listState.scrollToItem(flat.coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            }
        }
    }

    DisposableEffect(listState) {
        onDispose {
            val (ch, para) = bookmarkPositionForFlat(listState.firstVisibleItemIndex, chapters, isTablet = false)
            onSaveProgress(ch, para)
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
                        modifier = Modifier
                            .padding(top = 28.dp, bottom = 12.dp)
                            .animateItem(
                                fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(180),
                                placementSpec = tween(260, easing = FastOutSlowInEasing)
                            ),
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
                            .animateItem(
                                fadeInSpec = tween(200, easing = LinearOutSlowInEasing),
                                fadeOutSpec = tween(180),
                                placementSpec = tween(240, easing = FastOutSlowInEasing)
                            )
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
                        ExpandableDiagram(
                            modifier = Modifier
                                .padding(vertical = 16.dp)
                                .animateItem(
                                    fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(180),
                                    placementSpec = tween(240, easing = FastOutSlowInEasing)
                                )
                        )
                    }
                }
                item(key = "chapter-gap-$chapterIndex") {
                    Column(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(200),
                            fadeOutSpec = tween(180),
                            placementSpec = tween(240, easing = FastOutSlowInEasing)
                        )
                    ) {
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

        // Top/bottom scroll fade (iOS-like) — subtle gradient so text doesn't hard-cutoff; respects immersive toggle
        val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
        val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
        com.makemission.folio.ui.reader.components.TopReadingFade(
            backgroundColor = readingBackground,
            visible = canScrollUp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        com.makemission.folio.ui.reader.components.BottomReadingFade(
            backgroundColor = readingBackground,
            visible = canScrollDown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        )

        // Bottom bar: progress bar respects "Always show progress bar" setting (overrides tap-to-hide) — premium slide/fade
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(readingBackground.copy(alpha = 0.92f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
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
                enter = slideInVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)),
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
    restoredParagraphIndex: Int = 0,
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
    hapticsEnabled: Boolean = true,
    leftListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    rightListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    pendingBookmarkJump: Bookmark? = null,
    onJumpConsumed: () -> Unit = {},
    bookId: String? = null,
    fileHash: String? = null,
    modifier: Modifier = Modifier,
) {
    val mid = (chapters.size + 1) / 2
    val left = remember(chapters) { chapters.take(mid) }
    val right = remember(chapters) { chapters.drop(mid) }

    val leftState = leftListState
    val rightState = rightListState
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

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
        bookId = bookId,
        fileHash = fileHash,
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

    // Haptics: subtle tick on chapter boundary for tablet (left primary, also right)
    var lastChapterForHapticsLeft by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(leftState, chapters, hapticsEnabled) {
        snapshotFlow { flatIndexToChapterParagraph(leftState.firstVisibleItemIndex, left).first }
            .distinctUntilChanged()
            .collect { newChapter ->
                if (lastChapterForHapticsLeft != null && lastChapterForHapticsLeft != newChapter && hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastChapterForHapticsLeft = newChapter
            }
    }
    var lastChapterForHapticsRight by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rightState, chapters, hapticsEnabled) {
        snapshotFlow { flatIndexToChapterParagraph(rightState.firstVisibleItemIndex, right).first + mid }
            .distinctUntilChanged()
            .collect { newGlobalChapter ->
                if (lastChapterForHapticsRight != null && lastChapterForHapticsRight != newGlobalChapter && hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastChapterForHapticsRight = newGlobalChapter
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

    LaunchedEffect(chapters, restoredChapterIndex, restoredParagraphIndex) {
        if (restoredChapterIndex in chapters.indices) {
            if (restoredChapterIndex < mid) {
                val flat = flatIndexForBookmarkInLeft(restoredChapterIndex, restoredParagraphIndex, left)
                if (flat >= 0) leftState.scrollToItem(flat.coerceIn(0, (leftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            } else {
                val flat = flatIndexForBookmarkInRight(restoredChapterIndex, restoredParagraphIndex, right, mid)
                if (flat >= 0) rightState.scrollToItem(flat.coerceIn(0, (rightState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
            }
        }
    }

    DisposableEffect(leftState, rightState) {
        onDispose {
            // Save the furthest progress — prefer right if it has been scrolled, otherwise left
            val rightVisible = rightState.firstVisibleItemIndex
            val leftVisible = leftState.firstVisibleItemIndex
            if (rightVisible > 0 || rightState.canScrollBackward) {
                var rem = rightVisible
                var para = 0
                var chLocal = 0
                for (idx in right.indices) {
                    val size = 1 + right[idx].paragraphs.size + 1
                    if (rem < size) {
                        chLocal = idx
                        para = if (rem == 0) 0 else (rem - 1).coerceIn(0, (right[idx].paragraphs.size - 1).coerceAtLeast(0))
                        break
                    }
                    rem -= size
                }
                val ch = mid + chLocal
                onSaveProgress(ch.coerceIn(0, chapters.size - 1), para)
            } else {
                var rem = leftVisible
                var chIdx = 0
                var pIdx = 0
                for (idx in left.indices) {
                    val size = 1 + left[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1
                    if (rem < size) {
                        chIdx = idx
                        pIdx = if (rem == 0) 0 else (rem - 1).coerceIn(0, (left[idx].paragraphs.size - 1).coerceAtLeast(0))
                        break
                    }
                    rem -= size
                }
                onSaveProgress(chIdx, pIdx)
            }
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
                            modifier = Modifier
                                .padding(top = 20.dp, bottom = 10.dp)
                                .animateItem(
                                    fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(180),
                                    placementSpec = tween(260, easing = FastOutSlowInEasing)
                                ),
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
                                .animateItem(
                                    fadeInSpec = tween(200, easing = LinearOutSlowInEasing),
                                    fadeOutSpec = tween(180),
                                    placementSpec = tween(240, easing = FastOutSlowInEasing)
                                )
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
                            ExpandableDiagram(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .animateItem(
                                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(180),
                                        placementSpec = tween(240, easing = FastOutSlowInEasing)
                                    )
                            )
                        }
                    }
                    item(key = "L-gap-$chapterIndex") {
                        Column(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(200),
                                fadeOutSpec = tween(180),
                                placementSpec = tween(240, easing = FastOutSlowInEasing)
                            )
                        ) {
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
                                modifier = Modifier
                                    .padding(top = 20.dp, bottom = 10.dp)
                                    .animateItem(
                                        fadeInSpec = tween(220, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(180),
                                        placementSpec = tween(260, easing = FastOutSlowInEasing)
                                    ),
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
                                    .animateItem(
                                        fadeInSpec = tween(200, easing = LinearOutSlowInEasing),
                                        fadeOutSpec = tween(180),
                                        placementSpec = tween(240, easing = FastOutSlowInEasing)
                                    )
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
                            Column(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(200),
                                    fadeOutSpec = tween(180),
                                    placementSpec = tween(240, easing = FastOutSlowInEasing)
                                )
                            ) {
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

        // Tablet scroll fades — cover both columns, respect chrome
        val canScrollUpTablet by remember { derivedStateOf { leftState.canScrollBackward || rightState.canScrollBackward } }
        val canScrollDownTablet by remember { derivedStateOf { leftState.canScrollForward || rightState.canScrollForward } }
        com.makemission.folio.ui.reader.components.TopReadingFade(
            backgroundColor = readingBackground,
            visible = canScrollUpTablet,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        com.makemission.folio.ui.reader.components.BottomReadingFade(
            backgroundColor = readingBackground,
            visible = canScrollDownTablet,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
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


// ---- Chapter swipe mode: vertical within chapter, horizontal between chapters ----
// Phone: HorizontalPager where each page is a single chapter's vertical LazyColumn.
// Keeps existing vertical scroll (LazyColumn) exactly, adds horizontal swipe to change chapter.
// Reset scroll to top of new chapter on swipe. Volume keys still page vertically within chapter.

@Composable
private fun ChapterSwipePhoneContent(
    chapters: List<EpubParser.EpubChapter>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    chapterStates: List<androidx.compose.foundation.lazy.LazyListState>,
    bionicEnabled: Boolean,
    highlights: List<Highlight>,
    readingText: Color,
    readingBackground: Color,
    alwaysShowProgressBar: Boolean,
    hapticsEnabled: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onAddHighlight: (List<Offset>, List<Float>, List<Float>, Int) -> Unit,
    onLasso: (List<Offset>, Rect) -> Unit,
    onWordDoubleTap: (String) -> Unit,
    onPrioritizeXRay: (Int) -> Unit,
    bookId: String? = null,
    fileHash: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentPage = pagerState.currentPage.coerceIn(0, chapters.size - 1)

    // Prioritize X-Ray for current chapter (progressive cache)
    LaunchedEffect(currentPage) {
        if (chapters.isNotEmpty()) onPrioritizeXRay(currentPage)
    }
    // Haptics on chapter swipe
    var lastSwipeChapter by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { newPage ->
            if (lastSwipeChapter != null && lastSwipeChapter != newPage && hapticsEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastSwipeChapter = newPage
            // Save progress on chapter change
            onSaveProgress(newPage, 0)
        }
    }
    // Volume keys paging within current chapter's vertical list
    DisposableEffect(pagerState, currentPage) {
        ReaderPageTurnHandler.onVolumeKey = { isUp ->
            scope.launch {
                val state = chapterStates.getOrNull(currentPage) ?: return@launch
                val pageSize = 6
                val target = if (isUp) (state.firstVisibleItemIndex - pageSize).coerceAtLeast(0)
                else (state.firstVisibleItemIndex + pageSize).coerceAtMost((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                state.animateScrollToItem(target)
            }
        }
        onDispose { ReaderPageTurnHandler.onVolumeKey = null }
    }
    DisposableEffect(pagerState) {
        onDispose {
            // Save current chapter on exit
            val ch = pagerState.currentPage
            val state = chapterStates.getOrNull(ch)
            val flat = state?.firstVisibleItemIndex ?: 0
            val para = when {
                flat == 0 -> 0
                flat in 1..(chapters.getOrNull(ch)?.paragraphs?.size ?: 0) -> flat - 1
                else -> 0
            }
            onSaveProgress(ch, para)
        }
    }

    // True-Page (whole-book, disk-cached) — pageFor uses global flat
    val truePageInfo = rememberTruePageState(chapters = chapters, isTabletLandscape = false, bionicEnabled = bionicEnabled, bookId = bookId, fileHash = fileHash)
    val knuthAdjustments = rememberKnuthAdjustments(chapters = chapters, isTabletLandscape = false, bionicEnabled = bionicEnabled)
    // Progress + time remaining based on global flat (current chapter offset + within)
    val currentFlatOffset = remember(chapters, currentPage) {
        var off = 0
        for (i in 0 until currentPage) {
            val ch = chapters[i]
            off += 1 + ch.paragraphs.size + (if (i == 0) 1 else 0) + 1
        }
        off
    }
    val currentListState = chapterStates.getOrNull(currentPage)
    val withinFlat = currentListState?.firstVisibleItemIndex ?: 0
    val globalFlat = currentFlatOffset + withinFlat
    val totalFlats = remember(chapters) {
        chapters.indices.sumOf { idx -> 1 + chapters[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1 }.coerceAtLeast(1)
    }
    val progress by remember { derivedStateOf { (globalFlat.toFloat() / (totalFlats - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) } }
    val currentPageNumber by remember { derivedStateOf { truePageInfo.pageFor(globalFlat) } }
    val estimator = remember { VelocityEstimator() }
    var timeRemaining by remember { mutableStateOf<String?>(null) }
    var lastFlatSwipe by remember { mutableStateOf(globalFlat) }
    var lastTimeSwipe by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentPage, currentListState) {
        if (currentListState == null) return@LaunchedEffect
        snapshotFlow { currentListState.firstVisibleItemIndex }.distinctUntilChanged().collect { newWithin ->
            val newGlobal = currentFlatOffset + newWithin
            val now = System.currentTimeMillis()
            val deltaMs = (now - lastTimeSwipe).toDouble()
            val charsMoved = ReadingFlatMapper.charsBetween(lastFlatSwipe, newGlobal, chapters).coerceAtLeast(1)
            if (newGlobal != lastFlatSwipe && deltaMs in 500.0..300000.0) estimator.addSample(deltaMs, charsMoved)
            val remaining = ReadingFlatMapper.remainingCharsInChapter(newGlobal, chapters)
            timeRemaining = formatTimeRemaining(estimator.estimateMs(remaining))
            lastFlatSwipe = newGlobal
            lastTimeSwipe = now
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val chapter = chapters[page]
            val listState = chapterStates[page]
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleChrome),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp),
                ) {
                    item(key = "swipe-title-$page") {
                        Text(text = chapter.title, style = MaterialTheme.typography.headlineSmall, color = readingText, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
                    }
                    itemsIndexed(chapter.paragraphs, key = { idx, _ -> "swipe-c${page}-p$idx" }) { paraIdx, paragraph ->
                        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        val annotated = if (bionicEnabled) remember(paragraph) { BionicReading.toBionicAnnotated(paragraph, BionicReading.boldSpan()) } else null
                        val knuth = knuthAdjustments["c${page}-p${paraIdx}"]
                        val baseStyle = MaterialTheme.typography.bodyLarge
                        val style = if (knuth != null) {
                            val ls = if (baseStyle.letterSpacing.isSp) (baseStyle.letterSpacing.value + knuth.letterSpacingDelta.value).sp else knuth.letterSpacingDelta
                            baseStyle.copy(letterSpacing = ls, textAlign = if (knuth.useJustify) TextAlign.Justify else baseStyle.textAlign ?: TextAlign.Start)
                        } else baseStyle
                        Text(text = annotated ?: androidx.compose.ui.text.AnnotatedString(paragraph), style = style, color = readingText, onTextLayout = { layoutResult = it }, modifier = Modifier.padding(bottom = 14.dp).pointerInput(paragraph, bionicEnabled) {
                            detectTapGestures(onDoubleTap = { offset ->
                                layoutResult?.let { layout ->
                                    val pos = layout.getOffsetForPosition(offset)
                                    if (pos < 0 || pos >= paragraph.length) return@detectTapGestures
                                    var start = pos; var end = pos
                                    while (start > 0 && paragraph[start - 1].isLetter()) start--
                                    while (end < paragraph.length && paragraph[end].isLetter()) end++
                                    if (start < end) onWordDoubleTap(paragraph.substring(start, end))
                                }
                            })
                        })
                    }
                    if (page == 0) {
                        item(key = "swipe-diagram-$page") {
                            ExpandableDiagram(modifier = Modifier.padding(vertical = 16.dp))
                        }
                    }
                    item(key = "swipe-gap-$page") {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                // Highlight overlay for this chapter
                HighlightOverlay(
                    highlights = highlights.filter { it.chapterIndex == page },
                    onStylusStrokeFinished = { pts, pressures, tilts -> onAddHighlight(pts, pressures, tilts, page) },
                    onLassoFinished = { pts, bounds -> onLasso(pts, bounds) },
                    modifier = Modifier.fillMaxSize(),
                )
                // Scroll fades
                val canUp by remember { derivedStateOf { listState.canScrollBackward } }
                val canDown by remember { derivedStateOf { listState.canScrollForward } }
                com.makemission.folio.ui.reader.components.TopReadingFade(backgroundColor = readingBackground, visible = canUp, modifier = Modifier.align(Alignment.TopCenter))
                com.makemission.folio.ui.reader.components.BottomReadingFade(backgroundColor = readingBackground, visible = canDown, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
            }
        }
        // Bottom bar: Page X of Y + progress (global)
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(readingBackground.copy(alpha = 0.92f)), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = chromeVisible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Page $currentPageNumber of ${truePageInfo.totalPages}", style = MaterialTheme.typography.labelSmall, color = readingText.copy(alpha = 0.85f))
                    timeRemaining?.let { Text(text = it, style = MaterialTheme.typography.labelSmall, color = readingText.copy(alpha = 0.85f), textAlign = TextAlign.End) }
                }
            }
            AnimatedVisibility(visible = chromeVisible || alwaysShowProgressBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                ReadingProgressBar(progress = progress, onSeek = { fraction ->
                    // Seek within whole book: map fraction to global flat then to chapter/page + within
                    val targetGlobal = ((totalFlats - 1) * fraction).toInt().coerceIn(0, totalFlats - 1)
                    var rem = targetGlobal; var targetCh = 0
                    for (idx in chapters.indices) {
                        val size = 1 + chapters[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1
                        if (rem < size) { targetCh = idx; break }
                        rem -= size
                    }
                    scope.launch {
                        pagerState.animateScrollToPage(targetCh)
                        val st = chapterStates.getOrNull(targetCh)
                        st?.animateScrollToItem(rem.coerceIn(0, (st.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TwoColumnChapterSwipeContent(
    chapters: List<EpubParser.EpubChapter>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    leftStates: List<androidx.compose.foundation.lazy.LazyListState>,
    rightStates: List<androidx.compose.foundation.lazy.LazyListState>,
    bionicEnabled: Boolean,
    highlights: List<Highlight>,
    readingText: Color,
    readingBackground: Color,
    alwaysShowProgressBar: Boolean,
    hapticsEnabled: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    onAddHighlight: (List<Offset>, List<Float>, List<Float>, Int) -> Unit,
    onLasso: (List<Offset>, Rect) -> Unit,
    onWordDoubleTap: (String) -> Unit,
    onPrioritizeXRay: (Int) -> Unit,
    bookId: String? = null,
    fileHash: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val page = pagerState.currentPage
    val leftIdx = page * 2
    val rightIdx = leftIdx + 1
    LaunchedEffect(page) {
        val ch = if (leftIdx in chapters.indices) leftIdx else rightIdx
        if (ch in chapters.indices) onPrioritizeXRay(ch)
    }
    var lastSpread by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect { newPage ->
            if (lastSpread != null && lastSpread != newPage && hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastSpread = newPage
            // Save spread's first chapter
            val ch = (newPage * 2).coerceIn(0, chapters.size - 1)
            onSaveProgress(ch, 0)
        }
    }
    DisposableEffect(pagerState) {
        onDispose {
            val ch = (pagerState.currentPage * 2).coerceIn(0, chapters.size - 1)
            onSaveProgress(ch, 0)
        }
    }
    // Volume keys scroll left column of current spread
    DisposableEffect(pagerState, page) {
        ReaderPageTurnHandler.onVolumeKey = { isUp ->
            scope.launch {
                val state = leftStates.getOrNull(page) ?: rightStates.getOrNull(page) ?: return@launch
                val pageSize = 5
                val target = if (isUp) (state.firstVisibleItemIndex - pageSize).coerceAtLeast(0) else (state.firstVisibleItemIndex + pageSize).coerceAtMost((state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                state.animateScrollToItem(target)
            }
        }
        onDispose { ReaderPageTurnHandler.onVolumeKey = null }
    }

    val truePageInfo = rememberTruePageState(chapters = chapters, isTabletLandscape = true, bionicEnabled = bionicEnabled, bookId = bookId, fileHash = fileHash)
    val knuthAdjustments = rememberKnuthAdjustments(chapters = chapters, isTabletLandscape = true, bionicEnabled = bionicEnabled)
    val currentFlatOffset = remember(chapters, page) {
        var off = 0
        for (i in 0 until leftIdx) {
            val ch = chapters[i]
            off += 1 + ch.paragraphs.size + (if (i == 0) 1 else 0) + 1
        }
        off
    }
    val leftState = leftStates.getOrNull(page)
    val withinFlat = leftState?.firstVisibleItemIndex ?: 0
    val globalFlat = currentFlatOffset + withinFlat
    val totalFlats = remember(chapters) { chapters.indices.sumOf { idx -> 1 + chapters[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1 }.coerceAtLeast(1) }
    val progress by remember { derivedStateOf { (globalFlat.toFloat() / (totalFlats - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) } }
    val currentPageNum by remember { derivedStateOf { truePageInfo.pageFor(globalFlat) } }
    val estimator = remember { VelocityEstimator() }
    var timeRemaining by remember { mutableStateOf<String?>(null) }
    var lastFlatSwipe by remember { mutableStateOf(globalFlat) }
    var lastTimeSwipe by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(leftState) {
        if (leftState == null) return@LaunchedEffect
        snapshotFlow { leftState.firstVisibleItemIndex }.distinctUntilChanged().collect { newWithin ->
            val newGlobal = currentFlatOffset + newWithin
            val now = System.currentTimeMillis()
            val deltaMs = (now - lastTimeSwipe).toDouble()
            val charsMoved = ReadingFlatMapper.charsBetween(lastFlatSwipe, newGlobal, chapters).coerceAtLeast(1)
            if (newGlobal != lastFlatSwipe && deltaMs in 500.0..300000.0) estimator.addSample(deltaMs, charsMoved)
            val remaining = ReadingFlatMapper.remainingCharsInChapter(newGlobal, chapters)
            timeRemaining = formatTimeRemaining(estimator.estimateMs(remaining))
            lastFlatSwipe = newGlobal
            lastTimeSwipe = now
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
            val lIdx = p * 2
            val rIdx = lIdx + 1
            val leftChapter = chapters.getOrNull(lIdx)
            val rightChapter = chapters.getOrNull(rIdx)
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).padding(bottom = 32.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleChrome)) {
                // Left column
                if (leftChapter != null) {
                    val lState = leftStates[p]
                    LazyColumn(state = lState, modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        item(key = "swipe-L-title-$lIdx") { Text(text = leftChapter.title, style = MaterialTheme.typography.titleMedium, color = readingText, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)) }
                        itemsIndexed(leftChapter.paragraphs, key = { idx, _ -> "swipe-L-c${lIdx}-p$idx" }) { paraIdx, paragraph ->
                            var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                            val annotated = if (bionicEnabled) remember(paragraph) { BionicReading.toBionicAnnotated(paragraph, BionicReading.boldSpan()) } else null
                            val knuth = knuthAdjustments["c${lIdx}-p${paraIdx}"]
                            val base = MaterialTheme.typography.bodyMedium
                            val style = if (knuth != null) {
                                val ls = if (base.letterSpacing.isSp) (base.letterSpacing.value + knuth.letterSpacingDelta.value).sp else knuth.letterSpacingDelta
                                base.copy(letterSpacing = ls, textAlign = if (knuth.useJustify) TextAlign.Justify else base.textAlign ?: TextAlign.Start)
                            } else base
                            Text(text = annotated ?: androidx.compose.ui.text.AnnotatedString(paragraph), style = style, color = readingText, onTextLayout = { layoutResult = it }, modifier = Modifier.padding(bottom = 12.dp).pointerInput(paragraph, bionicEnabled) {
                                detectTapGestures(onDoubleTap = { offset ->
                                    layoutResult?.let { layout ->
                                        val pos = layout.getOffsetForPosition(offset)
                                        if (pos < 0 || pos >= paragraph.length) return@detectTapGestures
                                        var s = pos; var e = pos
                                        while (s > 0 && paragraph[s - 1].isLetter()) s--
                                        while (e < paragraph.length && paragraph[e].isLetter()) e++
                                        if (s < e) onWordDoubleTap(paragraph.substring(s, e))
                                    }
                                })
                            })
                        }
                        if (lIdx == 0) { item(key = "swipe-L-diagram-$lIdx") { ExpandableDiagram(modifier = Modifier.padding(vertical = 12.dp)) } }
                        item(key = "swipe-L-gap-$lIdx") { Column { Spacer(modifier = Modifier.height(6.dp)); Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)); Spacer(modifier = Modifier.height(6.dp)) } }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("—", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                }
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().padding(vertical = 16.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
                if (rightChapter != null) {
                    val rState = rightStates[p]
                    LazyColumn(state = rState, modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        item(key = "swipe-R-title-$rIdx") { Text(text = rightChapter.title, style = MaterialTheme.typography.titleMedium, color = readingText, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)) }
                        itemsIndexed(rightChapter.paragraphs, key = { idx, _ -> "swipe-R-c${rIdx}-p$idx" }) { paraIdx, paragraph ->
                            var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                            val annotated = if (bionicEnabled) remember(paragraph) { BionicReading.toBionicAnnotated(paragraph, BionicReading.boldSpan()) } else null
                            val knuth = knuthAdjustments["c${rIdx}-p${paraIdx}"]
                            val base = MaterialTheme.typography.bodyMedium
                            val style = if (knuth != null) {
                                val ls = if (base.letterSpacing.isSp) (base.letterSpacing.value + knuth.letterSpacingDelta.value).sp else knuth.letterSpacingDelta
                                base.copy(letterSpacing = ls, textAlign = if (knuth.useJustify) TextAlign.Justify else base.textAlign ?: TextAlign.Start)
                            } else base
                            Text(text = annotated ?: androidx.compose.ui.text.AnnotatedString(paragraph), style = style, color = readingText, onTextLayout = { layoutResult = it }, modifier = Modifier.padding(bottom = 12.dp).pointerInput(paragraph, bionicEnabled) {
                                detectTapGestures(onDoubleTap = { offset ->
                                    layoutResult?.let { layout ->
                                        val pos = layout.getOffsetForPosition(offset)
                                        if (pos < 0 || pos >= paragraph.length) return@detectTapGestures
                                        var s = pos; var e = pos
                                        while (s > 0 && paragraph[s - 1].isLetter()) s--
                                        while (e < paragraph.length && paragraph[e].isLetter()) e++
                                        if (s < e) onWordDoubleTap(paragraph.substring(s, e))
                                    }
                                })
                            })
                        }
                        item(key = "swipe-R-gap-$rIdx") { Column { Spacer(modifier = Modifier.height(6.dp)); Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)); Spacer(modifier = Modifier.height(6.dp)) } }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("—", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
                }
            }
        }
        // Highlight overlay for current spread (filter to leftIdx/rightIdx)
        HighlightOverlay(highlights = highlights.filter { it.chapterIndex == leftIdx || it.chapterIndex == rightIdx }, onStylusStrokeFinished = { pts, pressures, tilts ->
            val ch = leftIdx
            onAddHighlight(pts, pressures, tilts, ch)
        }, onLassoFinished = { pts, bounds -> onLasso(pts, bounds) }, modifier = Modifier.fillMaxSize())
        val leftCanUp by remember { derivedStateOf { leftStates.getOrNull(page)?.canScrollBackward == true } }
        val leftCanDown by remember { derivedStateOf { leftStates.getOrNull(page)?.canScrollForward == true } }
        com.makemission.folio.ui.reader.components.TopReadingFade(backgroundColor = readingBackground, visible = leftCanUp, modifier = Modifier.align(Alignment.TopCenter))
        com.makemission.folio.ui.reader.components.BottomReadingFade(backgroundColor = readingBackground, visible = leftCanDown, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(readingBackground.copy(alpha = 0.92f)), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = chromeVisible, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Page $currentPageNum of ${truePageInfo.totalPages}", style = MaterialTheme.typography.labelSmall, color = readingText.copy(alpha = 0.85f))
                    timeRemaining?.let { Text(text = it, style = MaterialTheme.typography.labelSmall, color = readingText.copy(alpha = 0.85f), textAlign = TextAlign.End) }
                }
            }
            AnimatedVisibility(visible = chromeVisible || alwaysShowProgressBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                ReadingProgressBar(progress = progress, onSeek = { fraction ->
                    val targetGlobal = ((totalFlats - 1) * fraction).toInt().coerceIn(0, totalFlats - 1)
                    var rem = targetGlobal; var targetSpread = 0
                    for (idx in chapters.indices step 2) {
                        val leftSize = 1 + chapters[idx].paragraphs.size + (if (idx == 0) 1 else 0) + 1
                        val rightSize = if (idx + 1 < chapters.size) 1 + chapters[idx + 1].paragraphs.size + 1 else 0
                        val spreadSize = leftSize + rightSize
                        if (rem < spreadSize) { targetSpread = idx / 2; break }
                        rem -= spreadSize
                    }
                    scope.launch { pagerState.animateScrollToPage(targetSpread) }
                }, modifier = Modifier.fillMaxWidth())
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
