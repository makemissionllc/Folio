package com.makemission.folio.ui.reader

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.epub.EpubParser

/**
 * Core reading screen — native EPUB rendering, serif typography, adaptive layout.
 *
 * §3 phone: single-column, edge-to-edge, immersive.
 * §3 tablet: two-column spread in landscape (book-like).
 * §6 progress: Room-backed chapter/paragraph position (no annotations yet).
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
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
                    onSaveProgress = onSaveProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SingleColumnReadingContent(
                    chapters = uiState.chapters,
                    restoredChapterIndex = uiState.restoredChapterIndex,
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
    onSaveProgress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Restore position
    LaunchedEffect(chapters) {
        if (restoredChapterIndex in chapters.indices) {
            // Approximate: scroll to chapter start
            val flatIndex = chapters.take(restoredChapterIndex).sumOf { 1 + it.paragraphs.size }
            if (flatIndex > 0) listState.scrollToItem(flatIndex.coerceAtMost(flatIndex))
        }
    }

    // Persist progress on dispose — simple first-visible heuristic
    DisposableEffect(listState) {
        onDispose {
            val firstVisible = listState.firstVisibleItemIndex
            val (ch, _) = flatIndexToChapterParagraph(firstVisible, chapters)
            onSaveProgress(ch, 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 8.dp,
            bottom = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
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
                // Thin rule between chapters — amber, per Folio accent
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

// ---- Tablet landscape: two-column spread ----

@Composable
private fun TwoColumnReadingContent(
    chapters: List<EpubParser.EpubChapter>,
    restoredChapterIndex: Int,
    onSaveProgress: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Split chapters roughly in half for a spread illusion.
    // For a real pagination engine (§5 True-Page) this would be a virtual
    // canvas; here we keep it simple: left = first half, right = second half.
    val mid = (chapters.size + 1) / 2
    val left = remember(chapters) { chapters.take(mid) }
    val right = remember(chapters) { chapters.drop(mid) }

    val leftState = rememberLazyListState()
    val rightState = rememberLazyListState()

    // Restore left column position (primary)
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

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
    ) {
        // Left page
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

        // Central gutter — physical spread spine
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 16.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )

        // Right page
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
}

private fun flatIndexToChapterParagraph(
    flatIndex: Int,
    chapters: List<EpubParser.EpubChapter>,
): Pair<Int, Int> {
    var remaining = flatIndex
    chapters.forEachIndexed { chIdx, ch ->
        val chapterSize = 1 + ch.paragraphs.size + 1 // title + paragraphs + gap
        if (remaining < chapterSize) return chIdx to 0
        remaining -= chapterSize
    }
    return 0 to 0
}
