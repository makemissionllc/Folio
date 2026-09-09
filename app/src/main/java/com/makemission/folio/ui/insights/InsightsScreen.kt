package com.makemission.folio.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Insights — a quiet reader's journal, not a fitness dashboard.
 *
 * Read-only aggregation over existing Highlight / Bookmark / ReadingProgress /
 * VocabularyCard data (§6). No new data collection, just Room queries.
 * FolioTheme editorial style: deep green / burgundy / amber, heavy sans
 * headers + serif body, rounded surface cards, flat Canvas hero. No generic
 * charts/graphs — just thoughtful numbers and understated dividers.
 *
 * Inspiration from book-story-master's history/grouping only.
 */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { InsightsHeader(onBack = onBack) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        val isEmpty = state.totalBooks == 0 && state.totalHighlights == 0 && state.totalBookmarks == 0 && state.totalVocab == 0 && state.readingSessions == 0

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item { InsightsHero(isEmpty = isEmpty) }

            if (isEmpty) {
                item { EmptyJournalCard() }
            } else {
                // Shelf — books
                item {
                    InsightsSection(
                        title = "Shelf",
                        subtitle = "Books in your folio",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Books",
                                value = state.totalBooks.toString(),
                                caption = if (state.totalBooks == 1) "title" else "titles in library",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "In progress",
                                value = state.inProgressBooks.toString(),
                                caption = "with a saved position",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.totalBooks > 0 && state.inProgressBooks == 0) {
                            Text(
                                text = "Open a book — Folio will remember where you left off.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                // Marginalia — highlights & bookmarks
                item {
                    InsightsSection(
                        title = "Marginalia",
                        subtitle = "Marks you’ve left in the text",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Highlights",
                                value = state.totalHighlights.toString(),
                                caption = "inked passages",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Bookmarks",
                                value = state.totalBookmarks.toString(),
                                caption = "quiet placeholders",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.totalHighlights == 0 && state.totalBookmarks == 0) {
                            Text(
                                text = "No marks yet — a stylus stroke or a bookmark will appear here, lightly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                // Vocabulary — SM-2
                item {
                    InsightsSection(
                        title = "Lexicon",
                        subtitle = "Words gathered along the way",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Words",
                                value = state.totalVocab.toString(),
                                caption = "looked up",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Mastered",
                                value = state.masteredVocab.toString(),
                                caption = "repetitions ≥ 3",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Due",
                                value = state.dueVocab.toString(),
                                caption = "for review",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Learning",
                                value = (state.totalVocab - state.masteredVocab).coerceAtLeast(0).toString(),
                                caption = "still in cycle",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.totalVocab == 0) {
                            Text(
                                text = "Double-tap a word while reading — Folio keeps it here, on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                // Rhythm — reading sessions & streaks
                item {
                    InsightsSection(
                        title = "Rhythm",
                        subtitle = "Quiet patterns from your reading",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Streak",
                                value = if (state.currentStreak == 0 && state.distinctDays > 0) "—" else "${state.currentStreak}d",
                                caption = if (state.currentStreak == 1) "consecutive day" else "consecutive days",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Sessions",
                                value = state.readingSessions.toString(),
                                caption = "saved positions",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCell(
                                label = "Distinct days",
                                value = state.distinctDays.toString(),
                                caption = "days you opened a book",
                                modifier = Modifier.weight(1f),
                            )
                            StatCell(
                                label = "Last read",
                                value = state.lastReadLabel ?: "—",
                                caption = if (state.lastReadLabel == null) "not yet" else "most recent",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.longestStreak > state.currentStreak && state.longestStreak > 1) {
                            Text(
                                text = "Longest streak: ${state.longestStreak} days — a small, steady habit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (state.readingSessions == 0) {
                            Text(
                                text = "Your rhythm will appear after you turn a few pages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }

                item {
                    // Closing journal note — understated, editorial
                    Text(
                        text = "All counts are on-device, from your library — Folio keeps no cloud ledger.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InsightsHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("← Back", style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "A quiet ledger — not a dashboard",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsHero(isEmpty: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
                    val green = androidx.compose.ui.graphics.Color(0xFF004F39)
                    val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
                    // Open journal / ledger
                    drawRoundRect(color = amber.copy(alpha = 0.95f), topLeft = center.copy(x = center.x - 18f, y = center.y - 12f), size = androidx.compose.ui.geometry.Size(36f, 24f))
                    drawRoundRect(color = green.copy(alpha = 0.9f), topLeft = center.copy(x = center.x - 16f, y = center.y - 10f), size = androidx.compose.ui.geometry.Size(14f, 20f))
                    drawRoundRect(color = burgundy.copy(alpha = 0.85f), topLeft = center.copy(x = center.x + 2f, y = center.y - 10f), size = androidx.compose.ui.geometry.Size(14f, 20f))
                    // Small amber rule
                    drawRoundRect(color = green, topLeft = center.copy(x = center.x - 10f, y = center.y + 2f), size = androidx.compose.ui.geometry.Size(10f, 1.5f))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEmpty) "Your journal awaits" else "Reading, noticed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isEmpty) "Folio gathers softly — highlights, bookmarks, words, rhythm — all on-device."
                    else "Folio counts what’s already there — no new tracking, just what you’ve left behind.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyJournalCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No pages turned yet — and that’s fine.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open a book from your Library. Make a highlight with Apple Pencil, tuck in a bookmark, double-tap a word to keep it. Your Insights will grow here, like margins filling over time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Folio keeps everything on-device — no account, no cloud.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InsightsSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Small amber accent dot — editorial, not chart
            Canvas(modifier = Modifier.size(6.dp).padding(bottom = 8.dp)) {
                drawCircle(color = androidx.compose.ui.graphics.Color(0xFFF7B538), radius = 3f)
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
