package com.makemission.folio.ui.reader.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.data.epub.EpubParser

private enum class NotesFilter(val label: String) {
    ALL("All"), HIGHLIGHTS("Highlights"), NOTES("Notes")
}

/** Highlights and attached notes, with a tablet review pane and a compact phone sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsBottomSheet(
    highlights: List<Highlight>,
    chapters: List<EpubParser.EpubChapter>,
    onHighlightClick: (Highlight) -> Unit,
    onSaveNote: (Long, String) -> Unit,
    onDeleteHighlight: (Highlight) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(NotesFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf(-1L) }
    val filtered = when (filter) {
        NotesFilter.NOTES -> highlights.filter { it.note.isNotBlank() }
        else -> highlights
    }
    val selected = highlights.firstOrNull { it.id == selectedId } ?: filtered.firstOrNull()
    val context = LocalContext.current
    val shareNotes = {
        val text = highlights.filter { it.note.isNotBlank() }
            .joinToString("\n\n") { h -> "${chapters.getOrNull(h.chapterIndex)?.title ?: "Chapter ${h.chapterIndex + 1}"}\n“${h.anchorText}”\n${h.note}" }
        if (text.isNotBlank()) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, "Share notes"))
        }
    }
    if (LocalConfiguration.current.screenWidthDp >= 600) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Column(Modifier.width(340.dp).fillMaxHeight().padding(22.dp)) {
                        TextButton(onClick = onDismiss) { Text("‹  Reading") }
                        NotesHeader(highlights.size, filter, onFilter = { filter = it })
                        NotesList(
                            filtered, chapters, selected?.id,
                            onSelect = { selectedId = it.id },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = shareNotes, enabled = highlights.any { it.note.isNotBlank() }) { Text("Share Notes") }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NoteEditor(
                        selected, chapters,
                        onSaveNote = onSaveNote,
                        onGoToPage = { onHighlightClick(it) },
                        onDelete = { onDeleteHighlight(it); selectedId = -1L },
                        modifier = Modifier.weight(1f).padding(28.dp),
                    )
                }
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                NotesHeader(highlights.size, filter, onFilter = { filter = it })
                LazyColumn(modifier = Modifier.heightIn(max = 590.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { highlight ->
                        NoteListCard(highlight, chapters, selected?.id == highlight.id) { selectedId = highlight.id }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                if (filter == NotesFilter.NOTES) "Add a note to a highlight to see it here." else "Highlight a passage while reading to see it here.",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    selected?.let { highlight ->
                        item(key = "editor-${highlight.id}") {
                            NoteEditor(
                                highlight, chapters,
                                onSaveNote = onSaveNote,
                                onGoToPage = { onHighlightClick(it) },
                                onDelete = { onDeleteHighlight(it); selectedId = -1L },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                    }
                    item {
                        TextButton(onClick = shareNotes, enabled = highlights.any { it.note.isNotBlank() }) { Text("Share Notes") }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesHeader(count: Int, filter: NotesFilter, onFilter: (NotesFilter) -> Unit) {
    Text("Notes", style = MaterialTheme.typography.headlineMedium)
    Text("$count ${if (count == 1) "item" else "items"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NotesFilter.entries.forEach { option ->
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onFilter(option) },
                color = if (filter == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    option.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (filter == option) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun NotesList(
    highlights: List<Highlight>,
    chapters: List<EpubParser.EpubChapter>,
    selectedId: Long?,
    onSelect: (Highlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(highlights, key = { it.id }) { highlight ->
            NoteListCard(highlight, chapters, selectedId == highlight.id) { onSelect(highlight) }
        }
        if (highlights.isEmpty()) {
            item { Text("Your highlights and notes will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun NoteListCard(highlight: Highlight, chapters: List<EpubParser.EpubChapter>, selected: Boolean, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                chapters.getOrNull(highlight.chapterIndex)?.title ?: "Chapter ${highlight.chapterIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                highlight.anchorText.ifBlank { "Highlighted passage" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (highlight.note.isNotBlank()) {
                Text(highlight.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NoteEditor(
    highlight: Highlight?,
    chapters: List<EpubParser.EpubChapter>,
    onSaveNote: (Long, String) -> Unit,
    onGoToPage: (Highlight) -> Unit,
    onDelete: (Highlight) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (highlight == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Select a highlight to review it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var note by remember(highlight.id, highlight.note) { mutableStateOf(highlight.note) }
    val latestNote by rememberUpdatedState(note)
    val latestSavedNote by rememberUpdatedState(highlight.note)
    LaunchedEffect(highlight.id, highlight.note, note) {
        if (note == highlight.note) return@LaunchedEffect
        delay(500)
        onSaveNote(highlight.id, note)
    }
    DisposableEffect(highlight.id) {
        val highlightId = highlight.id
        onDispose {
            if (latestNote != latestSavedNote) onSaveNote(highlightId, latestNote)
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            chapters.getOrNull(highlight.chapterIndex)?.title ?: "Chapter ${highlight.chapterIndex + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            highlight.anchorText.ifBlank { "Highlighted passage" },
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier.background(Color(0xFFFFDF88).copy(alpha = 0.72f), RoundedCornerShape(4.dp)).padding(6.dp),
        )
        Text("Your note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(5_000) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = { Text("Add your thoughts about this passage") },
            shape = RoundedCornerShape(14.dp),
        )
        Text(
            if (note == highlight.note) "Saved on this device" else "Saving automatically…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSaveNote(highlight.id, note) }, enabled = note != highlight.note) { Text("Save now") }
            OutlinedButton(onClick = { onGoToPage(highlight) }) { Text("Go to page") }
            TextButton(onClick = { onDelete(highlight) }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
