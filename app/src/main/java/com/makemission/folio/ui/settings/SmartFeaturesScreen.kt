package com.makemission.folio.ui.settings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

private data class SmartFeature(
    val number: String,
    val title: String,
    val problem: String,
    val fix: String,
    val why: String,
)

/**
 * Smart Features — an editorial guide, not a changelog.
 * Every Folio intelligence lives on your device, offline, private, deterministic.
 * Plain English only in user-facing text: no TF-IDF, SM-2, LCS, Knuth-Plass jargon.
 * Structural inspiration from book-story-master's info/lists only — no code copied.
 */
@Composable
fun SmartFeaturesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val features = rememberFeatures()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SmartFeaturesHeader(onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { SmartFeaturesHero() }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "How to read this guide",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Folio’s smart features live entirely on your phone or tablet. No network, no account, no waiting. They notice quietly, adjust gently, and get out of the way — so you can stay with the book.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Twelve quiet helpers — each solves one small, real annoyance of reading on a screen.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            items(features, key = { it.number }) { feature ->
                SmartFeatureCard(feature = feature)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "All on-device, all private",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Every feature above is calculated right here on your device — not sent anywhere. That keeps Folio fast even without signal, and keeps your reading yours alone. If you change a book file or rotate your tablet, Folio simply recalculates. No setup needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SmartFeaturesHeader(
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
            text = "Smart Features",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "A thoughtful guide to Folio’s quiet intelligence",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartFeaturesHero(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
                    val green = androidx.compose.ui.graphics.Color(0xFF004F39)
                    val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
                    // soft editorial stack: amber light above, books below
                    drawCircle(color = amber.copy(alpha = 0.95f), radius = 13f, center = center.copy(y = center.y - 8f))
                    drawCircle(color = amber.copy(alpha = 0.35f), radius = 20f, center = center.copy(y = center.y - 8f))
                    drawRoundRect(color = burgundy, topLeft = Offset(center.x - 18f, center.y + 6f), size = androidx.compose.ui.geometry.Size(12f, 12f))
                    drawRoundRect(color = green, topLeft = Offset(center.x - 4f, center.y + 6f), size = androidx.compose.ui.geometry.Size(12f, 12f))
                    drawRoundRect(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), topLeft = Offset(center.x + 10f, center.y + 6f), size = androidx.compose.ui.geometry.Size(10f, 12f))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Folio thinks alongside you",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Twelve small, careful ideas that make long reading feel effortless — all calculated here, not in the cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmartFeatureCard(
    feature: SmartFeature,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = feature.number.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FeatureBlock(label = "The small annoyance", text = feature.problem)
            FeatureBlock(label = "How Folio quietly helps", text = feature.fix)
            FeatureBlock(label = "Why it matters to you", text = feature.why)
        }
    }
}

@Composable
private fun FeatureBlock(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberFeatures(): List<SmartFeature> = listOf(
    SmartFeature(
        number = "01 — Guided reading",
        title = "Words that gently guide your eyes",
        problem = "Long lines can tire your eyes — it’s easy to slip, re-read the same bit, or lose pace without noticing.",
        fix = "Folio softly emphasizes the first part of each word, giving your eyes a calm place to land as you sweep across the line. You can turn it on or off from the reading bar.",
        why = "You keep momentum without effort. The page feels lighter, and you stay inside the thought instead of chasing the text."
    ),
    SmartFeature(
        number = "02 — Time left you can trust",
        title = "An honest estimate of what’s left in the chapter",
        problem = "A simple average of pages makes the time left jump around — pause for tea and it still thinks you’re reading.",
        fix = "Folio notices how you’ve been turning pages lately, quietly ignores long pauses, and looks at how much text is actually ahead. Then it offers a small, steady estimate near the progress bar.",
        why = "You can decide — one more chapter or a pause — without guessing. The number feels human, not mechanical."
    ),
    SmartFeature(
        number = "03 — Who’s who and what matters",
        title = "A quiet guide to the people and ideas in your book",
        problem = "In a long story it’s easy to forget who someone is or why a word keeps appearing, and flicking backwards breaks flow.",
        fix = "As you open a book, Folio reads it right here on your device and notes which names and ideas appear often in one chapter but rarely elsewhere. It gathers them into a small sheet you can open with a tap.",
        why = "You get your bearings without leaving the page. The story stays continuous, and you don’t have to hunt."
    ),
    SmartFeature(
        number = "04 — Words that stay with you",
        title = "A little practice at just the right moment",
        problem = "Looking up a new word helps for a moment, but it’s hard to make it stick without steady, well-timed revisiting.",
        fix = "When you open a meaning, Folio saves the word privately on your device and brings it back later — a day later, then a few days later, then less often — only when a gentle reminder would help most. You can review from the Library badge when you’re ready.",
        why = "Your vocabulary grows without quizzes or pressure. Words become yours because they return when you’re about to forget them."
    ),
    SmartFeature(
        number = "05 — Pages you can count on",
        title = "Real pages, even though the text can flow",
        problem = "On screens the text shifts with every size or font choice, so “location 842” never feels like a real place to remember or share.",
        fix = "Before you read, Folio quietly measures the whole book against your current screen — how wide the column is, how tall the page is — and tucks in invisible page breaks. It remembers “Page 47 of 312” until you rotate or change size.",
        why = "You get the same comforting certainty as a printed book. “See page 47” finally means something again."
    ),
    SmartFeature(
        number = "06 — Paragraphs that breathe",
        title = "Even margins that let the text sit calmly",
        problem = "When a single word is left alone on a new page, or a paragraph ends awkwardly, the block of text feels unsettled and harder to rest on.",
        fix = "Folio looks at the whole paragraph at once, not line by line, and makes tiny, almost invisible adjustments to the spacing between letters and words. It keeps lines together where they belong and squares the edges softly.",
        why = "The page looks cared-for, like a well-set book. Your eyes don’t snag, and the shape of the text stays calm."
    ),
    SmartFeature(
        number = "07 — Marks that never get lost",
        title = "Your highlights stay with the right sentence",
        problem = "If a publisher fixes a typo and the file shifts by a few letters, a highlight saved by position can land in the wrong place or disappear.",
        fix = "Folio saves a few words around your mark along with it. If the book changes, it quietly searches the new text for the closest match and reattaches your mark there. If it can’t find it, it leaves the mark gently aside instead of guessing.",
        why = "Work you did with your stylus stays trustworthy, even as books are updated. Your notes keep their meaning."
    ),
    SmartFeature(
        number = "08 — Comfort in any light",
        title = "The page keeps its contrast, wherever you read",
        problem = "Dimming the whole screen can make the letters look washed or harsh, and reading by a window feels different from reading late at night.",
        fix = "Folio listens to the light around you and nudges the very colors of the paper and the ink — just enough — to keep the contrast comfortable. The change eases in over less than a second, so it never flashes.",
        why = "You don’t strain to read in bright sun or in a dim room. The page simply feels right, without you thinking about it."
    ),
    SmartFeature(
        number = "09 — Diagrams that finally fit",
        title = "White borders trimmed, pictures given room",
        problem = "Many books tuck diagrams inside wide, empty white frames, so the drawing looks tiny on a phone or tablet.",
        fix = "When you tap a diagram, Folio looks at the image right here on your device, finds the true edge of the drawing, shaves away the extra white, and lets it grow to the full width of your column. The trimmed copy is saved, so next time it’s instant.",
        why = "You actually see the detail the author meant you to see, without pinching or squinting."
    ),
    SmartFeature(
        number = "10 — Evening warmth",
        title = "A softer light as the day settles",
        problem = "A bright, cool screen in the evening can feel harsh when your eyes — and the room — want something warmer.",
        fix = "Folio watches the clock on your device and, as evening comes, lets the page drift toward a slightly warmer, softer tone. During the day it stays neutral; it never snaps, it just eases.",
        why = "Late-night reading feels kinder and more book-like, without you having to remember a setting."
    ),
    SmartFeature(
        number = "11 — Find any line, instantly",
        title = "Search that respects what you’ve marked",
        problem = "Hunting for a half-remembered line by flipping pages breaks concentration, and a long book makes it harder.",
        fix = "Pull down on your Library to search. Folio looks only inside your own books, right here on the device — first in the lines you’ve highlighted or bookmarked, then everywhere else — and lets you jump straight to the sentence.",
        why = "You spend less time searching and more time reading. What you cared enough to mark shows up first."
    ),
    SmartFeature(
        number = "12 — Meaning, right where you are",
        title = "A quiet dictionary that lives in the book",
        problem = "Leaving the page to look up a word pulls you out of the sentence and often needs a connection.",
        fix = "Double-tap any word — or select a few words and tap ‘Explain’ — and a small card shows the meaning, right by your finger. Everything comes from a compact word list kept on your device, so it works offline, and the word can be saved for gentle practice later.",
        why = "You understand the sentence without losing your place, and new words have a chance to stay with you."
    ),
)
