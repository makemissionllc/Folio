package com.makemission.folio.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.makemission.folio.ui.theme.FolioDoodle
import com.makemission.folio.ui.theme.FolioDoodleKind

private enum class TourDemo { LIBRARY, IMPORT, READER, SEARCH, NOTES, TOOLS, AI, WIDGET, TABLET, PRIVACY }

private data class TourStop(
    val eyebrow: String,
    val title: String,
    val copy: String,
    val points: List<String>,
    val demo: TourDemo,
)

private val tourStops = listOf(
    TourStop(
        "01 · YOUR LIBRARY", "Everything starts on one calm shelf",
        "Keep reading, find a book, or decide what to read next without digging through menus.",
        listOf("All, Reading, Want to Read and Finished shelves", "Move a book between shelves with a quick Undo", "Your shelf and sort choices stay saved between visits"),
        TourDemo.LIBRARY,
    ),
    TourStop(
        "02 · ADD BOOKS", "Bring your books in your way",
        "Pick one or several EPUB, MOBI, FB2 or plain-text files, open a book from another app, or enable a device scan when you want Folio to find them.",
        listOf("Choose books directly from the empty library; whole-device discovery includes EPUB, MOBI and FB2", "Scanning and chapter indexing wait for idle time and pause on a warm device", "File caching skips unchanged books; you can read as soon as import finishes"),
        TourDemo.IMPORT,
    ),
    TourStop(
        "03 · READ COMFORTABLY", "Make each page feel right",
        "Adjust the page to your eyes and your device. Folio remembers your reading setup.",
        listOf("Inter gives the interface a clear, familiar system feel; book text keeps your chosen font", "Paper, White, Sepia and Night page styles with text size, spacing, margins and brightness", "Continuous or chapter-swipe reading, keep-awake and sleep timer"),
        TourDemo.READER,
    ),
    TourStop(
        "04 · FIND YOUR PLACE", "Get back to the line you need",
        "Search inside a book or across your library, then jump straight to the matching passage.",
        listOf("Full-screen in-book search with highlighted results", "Contents and bookmarks return to exact passages", "True page count and time remaining keep you oriented"),
        TourDemo.SEARCH,
    ),
    TourStop(
        "05 · MARK THE MARGINS", "Keep a thought beside the words",
        "Use a finger or stylus to mark a passage, then leave a note that saves as you type.",
        listOf("Long-press text and choose Highlight, or mark with a stylus", "Five highlight colors with Fill or Underline", "Notes auto-save; share, revisit or remove a note or mark"),
        TourDemo.NOTES,
    ),
    TourStop(
        "06 · LEARN AS YOU READ", "Turn useful moments into lasting knowledge",
        "Small reading tools help you understand a word now and remember it later.",
        listOf("Offline dictionary and spaced vocabulary review", "People & Topics surfaces names and ideas from chapters", "Bionic Reading, Comfort Contrast and Evening Warmth"),
        TourDemo.TOOLS,
    ),
    TourStop(
        "07 · ON-DEVICE HELP", "Ask about a passage privately",
        "On supported phones, Gemini Nano can explain selected text on the device. Folio checks availability and keeps other offline reading tools ready when Nano is unavailable.",
        listOf("No passage is sent to a cloud model", "Availability depends on Android, the device and installed model support"),
        TourDemo.AI,
    ),
    TourStop(
        "08 · HOME SCREEN", "Pick up a book in one tap",
        "Add Folio’s Continue Reading widget for your latest book’s cover and saved progress, with a shortcut back to the library.",
        listOf("Long-press an empty home-screen area", "Choose Widgets → Folio → Continue Reading", "Tap to reopen your saved book; an empty widget starts import, and an all-finished shelf opens your library"),
        TourDemo.WIDGET,
    ),
    TourStop(
        "09 · TABLETS", "Use the space around the story",
        "Folio reshapes its library and reader for a wider screen. Drag the divider beside Quick look to give the book grid or details more room.",
        listOf("Landscape two-page reading and a resizable Contents rail", "Page Up/Down turn pages; Ctrl+F searches inside a book", "Ctrl+1–4 switch library shelves: All, Reading, Want to Read and Finished", "Ctrl+O adds books; Ctrl+K searches the library; Escape clears or closes search"),
        TourDemo.TABLET,
    ),
    TourStop(
        "10 · YOUR CONTROL", "A quiet app, with help close by",
        "Your books, notes, highlights, vocabulary and search stay on this device. You choose when to connect anything else.",
        listOf("Health Connect is optional; Folio requests no health permissions", "Settings → What’s New lists recent changes", "Settings → Smart Features explains the reading tools"),
        TourDemo.PRIVACY,
    ),
)

@Composable
fun InteractiveTourScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { tourStops.size })
    val scope = rememberCoroutineScope()
    val progressWidth by animateDpAsState(260.dp * ((pager.currentPage + 1).toFloat() / tourStops.size), tween(260), label = "tour_progress_width")
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onBack) { Text("‹  Settings") }
                    Text("${pager.currentPage + 1} of ${tourStops.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A tour of Folio", style = MaterialTheme.typography.headlineLarge)
                    FolioDoodle(FolioDoodleKind.SPARK, Modifier.size(22.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                }
                Text("Swipe, tap Next, and try the little previews as you go.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.width(progressWidth).fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).widthIn(max = 820.dp),
                key = { it },
            ) { page ->
                TourStopPage(tourStops[page], page)
            }
            Row(
                Modifier.fillMaxWidth().widthIn(max = 820.dp).padding(top = 8.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onBack() },
                    enabled = pager.currentPage > 0,
                ) { Text(if (pager.currentPage > 0) "Back" else "Settings") }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    tourStops.indices.forEach { index ->
                        val selected = pager.currentPage == index
                        val width by animateDpAsState(if (selected) 18.dp else 6.dp, tween(180), label = "tour_step_dot_$index")
                        Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Button(onClick = {
                    if (pager.currentPage < tourStops.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onBack()
                }) {
                    Text(if (pager.currentPage == tourStops.lastIndex) "Done" else "Next")
                }
            }
        }
    }
}

@Composable
private fun TourStopPage(stop: TourStop, page: Int) {
    Column(
        Modifier.fillMaxSize().widthIn(max = 820.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TourDemoCard(stop.demo, page)
        AnimatedContent(
            targetState = stop,
            transitionSpec = { fadeIn(tween(180)) + slideInHorizontally(tween(200)) { it / 16 } togetherWith fadeOut(tween(110)) + slideOutHorizontally(tween(150)) { -it / 20 } },
            label = "tour_description",
        ) { animatedStop ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(animatedStop.eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(animatedStop.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(animatedStop.copy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                animatedStop.points.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Text("•", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        Text(point, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TourDemoCard(demo: TourDemo, key: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().height(224.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            when (demo) {
                TourDemo.LIBRARY -> LibraryDemo(key)
                TourDemo.IMPORT -> ImportDemo()
                TourDemo.READER -> ReaderDemo(key)
                TourDemo.SEARCH -> SearchDemo(key)
                TourDemo.NOTES -> NotesDemo(key)
                TourDemo.TOOLS -> ToolsDemo(key)
                TourDemo.AI -> SimpleTourDemo("Ask about a passage", "Select a paragraph to see local explanation options.", "GEMINI NANO · ON-DEVICE")
                TourDemo.WIDGET -> WidgetDemo(key)
                TourDemo.TABLET -> TabletDemo(key)
                TourDemo.PRIVACY -> SimpleTourDemo("Your reading, on this device", "Books · notes · highlights · vocabulary", "PRIVATE BY DESIGN")
            }
        }
    }
}

@Composable
private fun LibraryDemo(key: Int) {
    var selected by remember(key) { mutableStateOf("All Books") }
    var shelfChanged by remember(key) { mutableStateOf(false) }
    var sortByTitle by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All Books", "Reading", "Want to Read", "Finished").forEach { shelf ->
                DemoChip(shelf, selected == shelf) { selected = shelf }
            }
            DemoChip(if (sortByTitle) "Title A–Z" else "Recent", sortByTitle) { sortByTitle = !sortByTitle }
        }
        AnimatedContent(targetState = selected, label = "library_shelf_preview") { shelf ->
            DemoBookLine(title = if (shelf == "Want to Read") "A book for later" else "Continue Reading", subtitle = if (shelf == "Reading") "Your place is saved" else "A quiet shelf, ready for you")
        }
        TextButton(onClick = { shelfChanged = true }) { Text("Mark finished") }
        AnimatedVisibility(visible = shelfChanged, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Moved to Finished", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { shelfChanged = false }) { Text("Undo") }
            }
        }
        Text("Your shelf and sort stay saved between visits.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportDemo() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Add Books", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoAction("Choose books", "Select several files")
            DemoAction("Scan device", "Wait for idle time")
        }
        Text("EPUB  ·  MOBI  ·  FB2  ·  TXT  ·  Optional folder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReaderDemo(key: Int) {
    var chosen by remember(key) { mutableStateOf("Paper") }
    val bg by animateColorAsState(when (chosen) {
        "Night" -> Color(0xFF212426)
        "Sepia" -> Color(0xFFF2E6CF)
        else -> Color(0xFFFFFCF5)
    }, tween(260), label = "reader_theme_demo")
    val fg = if (chosen == "Night") Color(0xFFE9E3D9) else Color(0xFF29251E)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("Paper", "Sepia", "Night").forEach { DemoChip(it, chosen == it) { chosen = it } } }
        Surface(Modifier.fillMaxWidth().weight(1f), color = bg, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                Text("A page at your pace", color = fg, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 17.sp)
                Text("Theme preview · tap a style above", color = fg.copy(alpha = 0.64f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SearchDemo(key: Int) {
    var query by remember(key) { mutableStateOf("") }
    val results = if (query.isBlank()) listOf("Search a title, word or passage") else listOf("‘${query.take(20)}’ · Chapter 2", "Another passage with ${query.take(16)}")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Find a passage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box { if (query.isEmpty()) Text("Search inside this book…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); inner() }
                },
            )
        }
        results.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun NotesDemo(key: Int) {
    var note by remember(key) { mutableStateOf("") }
    var saved by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("A thought beside the passage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), shape = RoundedCornerShape(12.dp)) {
            BasicTextField(
                value = note,
                onValueChange = { note = it; saved = false },
                modifier = Modifier.fillMaxWidth().padding(11.dp),
                decorationBox = { inner ->
                    Box { if (note.isEmpty()) Text("Try a short note…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("In the app, notes auto-save as you type.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { saved = true }, enabled = note.isNotBlank()) { Text("Save now") }
        }
        AnimatedVisibility(saved) { Text("Saved in this practice preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun ToolsDemo(key: Int) {
    var active by remember(key) { mutableStateOf("Dictionary") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Dictionary", "Vocabulary", "Topics").forEach { DemoChip(it, active == it) { active = it } }
        }
        AnimatedContent(active, label = "reading_tool_demo") { tool ->
            val detail = when (tool) {
                "Vocabulary" -> "Review saved words with gentle spaced practice."
                "Topics" -> "See people and ideas gathered from this chapter."
                else -> "Look up a word with the offline dictionary."
            }
            DemoBookLine(title = tool, subtitle = detail)
        }
    }
}

@Composable
private fun WidgetDemo(key: Int) {
    var details by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Folio · Continue Reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(width = 38.dp, height = 54.dp), color = Color(0xFF315D72), shape = RoundedCornerShape(5.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("F", color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 23.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("The next chapter is waiting", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("58% read  ·  Open book  ·  Library", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { 0.58f }, modifier = Modifier.fillMaxWidth().height(4.dp))
            }
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide setup" else "How to add this widget") }
        AnimatedVisibility(details) {
            Text("Long-press your home screen, choose Widgets, then Folio → Continue Reading.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TabletDemo(key: Int) {
    var compact by remember(key) { mutableStateOf(false) }
    var previewFraction by remember(key) { mutableStateOf(0.38f) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Library preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            DemoChip(if (compact) "Compact" else "Roomy", compact) { compact = !compact }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f - previewFraction), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(if (compact) 4 else 3) { index ->
                Surface(
                    Modifier.weight(1f).aspectRatio(.68f),
                    color = listOf(Color(0xFF315D72), Color(0xFF9C665C), Color(0xFF747751), Color(0xFF87735A))[index],
                    shape = RoundedCornerShape(8.dp),
                ) { Box(contentAlignment = Alignment.BottomStart) { Text("BOOK ${index + 1}", Modifier.padding(5.dp), color = Color.White, fontSize = 8.sp, maxLines = 1) } }
            }
            }
            Spacer(Modifier.width(5.dp))
            Surface(Modifier.weight(previewFraction).height(88.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(9.dp)) {
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Quick look", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Book details", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Start reading", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resize quick look", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = previewFraction, onValueChange = { previewFraction = it }, valueRange = 0.28f..0.52f, modifier = Modifier.weight(1f))
        }
        Text("Two-page reader · Contents rail · Ctrl+1–4 shelves · Ctrl+O add · Ctrl+K search", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SimpleTourDemo(title: String, detail: String, eyebrow: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FolioDoodle(FolioDoodleKind.UNDERLINE, Modifier.width(120.dp).height(14.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    }
}

@Composable
private fun DemoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(180), label = "demo_chip_bg")
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(label, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DemoAction(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun DemoBookLine(title: String, subtitle: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(34.dp, 44.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f), shape = RoundedCornerShape(5.dp)) {
                Box(contentAlignment = Alignment.Center) { FolioDoodle(FolioDoodleKind.SPARK, Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
        }
    }
}
