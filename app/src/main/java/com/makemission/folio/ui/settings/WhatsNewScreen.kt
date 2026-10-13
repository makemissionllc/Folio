package com.makemission.folio.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makemission.folio.ui.theme.FolioDoodle
import com.makemission.folio.ui.theme.FolioDoodleKind

private data class UpdateSection(
    val title: String,
    val summary: String,
    val details: List<String>,
)

private val updateSections = listOf(
    UpdateSection(
        title = "A calmer library",
        summary = "Folio follows the phone and tablet mockups with an editorial shelf, blue accents and small hand-drawn marks.",
        details = listOf(
            "Browse All, Reading, Want to Read and Finished shelves; open a book’s details to change its shelf status.",
            "Moved a book to the wrong shelf? Undo the change from the confirmation message. Your shelf and sort choices also survive rotation.",
            "Continue Reading shows your saved progress. Search books from the library or open the full-screen search inside a book.",
            "An empty library now has a direct Choose books action, so the first import starts without a trip through Settings.",
        ),
    ),
    UpdateSection(
        title = "A clearer interface type",
        summary = "Folio now uses Inter across its Android interface for a consistent, San Francisco-inspired feel.",
        details = listOf(
            "Library, settings, onboarding, tablet controls and widget actions share the same type family.",
            "Your reader font preference continues to apply to the book itself.",
        ),
    ),
    UpdateSection(
        title = "Find books without browsing folders",
        summary = "Folio now reads EPUB, MOBI, FictionBook (FB2) and plain-text (TXT) books.",
        details = listOf(
            "With Android’s shared-storage access, Folio can discover EPUB, MOBI and FB2 books across device storage.",
            "Pick TXT files manually. This avoids importing unrelated notes and documents during a device scan.",
            "Scanning and indexing wait for idle device conditions. Cached file details help Folio skip unchanged books.",
            "Choose a folder or add files manually when you prefer a narrower import route.",
        ),
    ),
    UpdateSection(
        title = "Add a stack of books at once",
        summary = "The system book picker can import multiple supported books in one pass.",
        details = listOf(
            "Choose EPUB, MOBI, FB2 and TXT files together; Folio copies and indexes them one by one and reports any files it couldn't read.",
        ),
    ),
    UpdateSection(
        title = "Background work stays gentle",
        summary = "Heavy text and chapter analysis avoid active use and wait for a cooler, quiet period.",
        details = listOf(
            "Book discovery and chapter enrichment wait until the device is idle, battery is healthy and storage is available.",
            "Folio prepares one book at a time and pauses when Android reports thermal pressure. You can open and read a book as soon as its import finishes.",
        ),
    ),
    UpdateSection(
        title = "Pick up where you left off",
        summary = "Add Folio’s Continue Reading widget for book cover art, saved progress and a direct route back into your library.",
        details = listOf(
            "Long-press the home screen, choose Widgets, then Folio → Continue Reading. Tap the book to return to your saved place; use the Library shortcut to browse.",
            "The widget follows light and dark appearance and keeps its book artwork small to reduce memory use.",
        ),
    ),
    UpdateSection(
        title = "Make the page yours",
        summary = "Reading settings now include Paper, White, Sepia and Night, plus text size, margins, brightness and scroll or slide navigation.",
        details = listOf(
            "Auto Night Mode follows the system appearance. Reader appearance and typography settings are saved on this device.",
            "Set a 15, 30, 45 or 60 minute sleep timer. Folio dims the screen and lets the device’s normal timeout turn it off.",
            "Keep the display awake while reading, then let the sleep timer release that override when it ends.",
        ),
    ),
    UpdateSection(
        title = "Keep your thoughts with the passage",
        summary = "Highlights attach to text, and you can add notes, revisit the passage, and share your notes.",
        details = listOf(
            "On phones, long-press a word or passage and choose Folio’s Highlight action. The mark stays attached when text reflows.",
            "Notes save automatically as you type, with a Save now action when you want to commit immediately.",
            "Gemini Nano availability is detected on supported devices for local passage explanations. Other offline reading tools remain available when Nano isn’t supported.",
        ),
    ),
    UpdateSection(
        title = "Helpful reading tools",
        summary = "Stay oriented in a book and keep useful words close at hand.",
        details = listOf(
            "Jump from Contents, set bookmarks, or use People & Topics to review names and ideas from a chapter.",
            "Look up words in Folio’s offline dictionary and review saved vocabulary later. Reading progress and time remaining update as you go.",
            "Comfort Contrast and Evening Warmth can adjust the reading page for ambient light and time of day.",
        ),
    ),
    UpdateSection(
        title = "Your library remembers",
        summary = "Folio reopens on the shelf and sort order you last chose, even after you close the app.",
        details = listOf(
            "Choose All, Reading, Want to Read or Finished, then switch between Recent and Title. Your choices stay on this device.",
        ),
    ),
    UpdateSection(
        title = "A roomier tablet reader",
        summary = "Landscape tablets get a two-page spread, a collapsible Contents rail, and a compact reader-settings popover.",
        details = listOf(
            "Drag the Contents divider to choose a width; Folio remembers it through rotation. Notes open in a split review layout.",
            "With a hardware keyboard, Page Up and Page Down move through the book, Ctrl+F opens in-book search, Ctrl+O opens Add Books, Ctrl+K opens library search, and Escape clears or closes it.",
        ),
    ),
    UpdateSection(
        title = "Choose your tablet shelf density",
        summary = "Switch between roomy covers and a compact grid when you want to see more of your library at once.",
        details = listOf(
            "Use the Grid control above the book list. Folio remembers the choice on this device.",
        ),
    ),
    UpdateSection(
        title = "A quick-look panel for tablet libraries",
        summary = "Select a book on a wide tablet to see its cover, progress and shelf actions beside your library. Drag the divider to set the balance you prefer.",
        details = listOf(
            "Start or continue reading without leaving the shelf. The divider remembers its width through rotation; smaller windows keep the familiar book-details sheet.",
        ),
    ),
    UpdateSection(
        title = "Private by design",
        summary = "Your library, progress, highlights, notes and search stay on the device.",
        details = listOf(
            "Health Connect is available to open when you choose. Folio does not request permissions or read health data.",
        ),
    ),
)

@Composable
fun WhatsNewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                TextButton(onClick = onBack) { Text("‹  Settings") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What’s New", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                    FolioDoodle(FolioDoodleKind.SPARK, Modifier.size(22.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                }
                Text("A tour of the latest Folio features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(2.dp)) }
            items(updateSections, key = { it.title }) { section ->
                Card(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(section.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        section.details.forEach { detail ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
