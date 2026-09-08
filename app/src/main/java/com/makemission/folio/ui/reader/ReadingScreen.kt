package com.makemission.folio.ui.reader

import android.app.Application
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.ui.reader.components.ReadingProgressBar
import kotlinx.coroutines.launch

/**
 * Core reading screen — native EPUB rendering, serif typography, adaptive layout,
 * plus frictionless navigation (volume-key page turns, tappable progress bar,
 * tap-to-toggle immersive chrome) per §3 Frictionless Navigation.
 *
 * Structure inspired by the reference app's `ReaderLayout` + `ReaderContent`
 * layering; implementation is Folio-specific and minimal.
 */
@Composable
fun ReadingScreen(
    bookId: String,
    bookTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val factory = remember(bookId, bookTitle) {
        ReadingViewModelFactory(application, bookId, bookTitle)
    }
    val viewModel: ReadingViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    ReadingScreenContent(
        uiState = uiState,
        onBack = onBack,
        onSaveProgress = viewModel::saveProgress,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingScreenContent(
    uiState: ReadingUiState,
    onBack: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isTabletLandscape = remember(configuration) {
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenWidthDp >= 840
    }

    var chromeVisible by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                        androidx.compose.material3.TextButton(onClick = onBack) {
                            Text("← Back", style = MaterialTheme.typography.labelLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    windowInsets = WindowInsets.statusBars,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background),
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
            } else if (isTabletLandscape) {
                TwoColumnReadingContent(
                    chapters = uiState.chapters,
                    restoredChapterIndex = uiState.restoredChapterIndex,
                    chromeVisible = chromeVisible,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onSaveProgress = onSaveProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SingleColumnReadingContent(
                    chapters = uiState.chapters,
                    restoredChapterIndex = uiState.restoredChapterIndex,
                    chromeVisible = chromeVisible,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onSaveProgress = onSaveProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ---- Phone: single-column, immersive, edge-to-edge ----

@Composable
private fun SingleColumnReadingContent(
    chapters: List<EpubParser.EpubChapter>,
    restoredChapterIndex: Int,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val progress by remember {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            val total = listState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            (first.toFloat() / (total - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        }
    }

    // Volume keys → page turns (one-handed phone reading).
    DisposableEffect(listState) {
        ReaderPageTurnHandler.onVolumeKey = { isUp ->
            scope.launch {
                val current = listState.firstVisibleItemIndex
                val pageSize = 6 // ~one screen worth of items
                val target = if (isUp) (current - pageSize).coerceAtLeast(0)
                else (current + pageSize).coerceAtMost(
                    (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
                )
                listState.animateScrollToItem(target)
            }
        }
        onDispose { ReaderPageTurnHandler.onVolumeKey = null }
    }

    // Restore position
    LaunchedEffect(chapters) {
        if (restoredChapterIndex in chapters.indices) {
            val flatIndex = chapters.take(restoredChapterIndex).sumOf { 1 + it.paragraphs.size }
            if (flatIndex > 0) listState.scrollToItem(flatIndex.coerceAtMost(flatIndex))
        }
    }

    // Persist progress on dispose
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
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 28.dp, bottom = 12.dp),
                    )
                }
                itemsIndexed(
                    chapter.paragraphs,
                    key = { paraIndex, _ -> "c${chapterIndex}-p$paraIndex" },
                ) { _, paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
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

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReadingProgressBar(
                progress = progress,
                onSeek = { fraction ->
                    val target = ((listState.layoutInfo.totalItemsCount - 1) * fraction).toInt()
                        .coerceIn(0, (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    scope.launch { listState.animateScrollToItem(target) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            )
        }
    }
}

// ---- Tablet landscape: two-column spread ----

@Composable
private fun TwoColumnReadingContent(
    chapters: List<EpubParser.EpubChapter>,
    restoredChapterIndex: Int,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mid = (chapters.size + 1) / 2
    val left = remember(chapters) { chapters.take(mid) }
    val right = remember(chapters) { chapters.drop(mid) }

    val leftState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val progress by remember {
        derivedStateOf {
            val first = leftState.firstVisibleItemIndex
            val total = leftState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            (first.toFloat() / (total - 1).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
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
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                        )
                    }
                    itemsIndexed(chapter.paragraphs, key = { i, _ -> "L-c$chapterIndex-p$i" }) { _, p ->
                        Text(
                            text = p,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
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
                val rightState = rememberLazyListState()
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
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                            )
                        }
                        itemsIndexed(chapter.paragraphs, key = { i, _ -> "R-c$chapterIndex-p$i" }) { _, p ->
                            Text(
                                text = p,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 12.dp),
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

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReadingProgressBar(
                progress = progress,
                onSeek = { fraction ->
                    val target = ((leftState.layoutInfo.totalItemsCount - 1) * fraction).toInt()
                        .coerceIn(0, (leftState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
                    scope.launch { leftState.animateScrollToItem(target) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
            )
        }
    }
}

private fun flatIndexToChapterParagraph(
    flatIndex: Int,
    chapters: List<EpubParser.EpubChapter>,
): Pair<Int, Int> {
    var remaining = flatIndex
    chapters.forEachIndexed { chIdx, ch ->
        val chapterSize = 1 + ch.paragraphs.size + 1
        if (remaining < chapterSize) return chIdx to 0
        remaining -= chapterSize
    }
    return 0 to 0
}
