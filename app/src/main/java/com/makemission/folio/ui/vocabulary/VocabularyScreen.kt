package com.makemission.folio.ui.vocabulary

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.makemission.folio.ui.reader.components.BottomReadingFade
import com.makemission.folio.ui.reader.components.TopReadingFade
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.vocabulary.Sm2

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onBack: () -> Unit,
    viewModel: VocabularyViewModel = viewModel(),
) {
    val dueCards by viewModel.dueCards.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val current by viewModel.currentReview.collectAsState()
    val showDef by viewModel.showDefinition.collectAsState()
    val dueListState = rememberLazyListState()
    val allListState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vocabulary", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text("← Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (current != null) {
                ReviewCard(
                    word = current!!.word,
                    definition = current!!.definition,
                    showDefinition = showDef,
                    onReveal = { viewModel.revealDefinition() },
                    onRate = { q -> viewModel.rateCurrent(q) },
                    onDismiss = { viewModel.dismissReview() },
                    dueCount = dueCards.size,
                )
            } else if (dueCards.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${dueCards.size} word(s) due for review",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = { viewModel.startReview() }) {
                        Text("Start review")
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = dueListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dueCards, key = { it.word }) { card ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(card.word, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        card.definition,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    )
                                    Text(
                                        "Due • ${card.intervalDays}d • EF ${String.format("%.2f", card.easeFactor)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                        }
                        val canUpDue by remember { derivedStateOf { dueListState.canScrollBackward } }
                        val canDownDue by remember { derivedStateOf { dueListState.canScrollForward } }
                        TopReadingFade(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            visible = canUpDue,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                        BottomReadingFade(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            visible = canDownDue,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            } else if (allCards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No vocabulary yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Double-tap words while reading to look them up — they’ll appear here for review.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "All caught up!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${allCards.size} words tracked • ${dueCards.size} due",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = allListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allCards, key = { it.word }) { card ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(card.word, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        card.definition.take(120),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        }
                        val canUpAll by remember { derivedStateOf { allListState.canScrollBackward } }
                        val canDownAll by remember { derivedStateOf { allListState.canScrollForward } }
                        TopReadingFade(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            visible = canUpAll,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                        BottomReadingFade(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            visible = canDownAll,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    word: String,
    definition: String,
    showDefinition: Boolean,
    onReveal: () -> Unit,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
    dueCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$dueCount due",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    word,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.height(16.dp))
                if (showDefinition) {
                    Text(
                        definition,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onRate(Sm2.AGAIN) }) { Text("Again") }
                        TextButton(onClick = { onRate(Sm2.HARD) }) { Text("Hard") }
                        TextButton(onClick = { onRate(Sm2.GOOD) }) { Text("Good") }
                        TextButton(onClick = { onRate(Sm2.EASY) }) { Text("Easy") }
                    }
                } else {
                    TextButton(onClick = onReveal) { Text("Show definition") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDismiss) { Text("Back to list") }
    }
}
