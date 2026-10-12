package com.makemission.folio.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.makemission.folio.data.search.SearchRepository

/** Full-screen local book search, including browsable matches and direct passage jumps. */
@Composable
fun InBookSearchDialog(
    query: String,
    results: List<SearchRepository.SearchResult>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchRepository.SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(query, results.size) { selectedIndex = 0 }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 920.dp).fillMaxWidth().fillMaxHeight().statusBarsPadding().navigationBarsPadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text("Search in book") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }
                    if (query.isNotBlank()) {
                        Text(
                            if (isSearching) "Searching this book…" else "${results.size} ${if (results.size == 1) "result" else "results"} in this book",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSearching) {
                        Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(results, key = { index, result -> "${result.chapterIndex}-${result.paragraphIndex}-$index" }) { index, result ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .background(if (index == selectedIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
                                    .clickable { selectedIndex = index; onResultClick(result) }
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Chapter ${result.chapterIndex + 1} · Passage ${result.paragraphIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    highlightedSnippet(result.snippet, query),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (query.length >= 2 && !isSearching && results.isEmpty()) {
                            item {
                                Text(
                                    "No matching passages. Try another word or phrase.",
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = {
                                val targetIndex = (selectedIndex - 1).coerceAtLeast(0)
                                selectedIndex = targetIndex
                                results.getOrNull(targetIndex)?.let(onResultClick)
                            },
                            enabled = results.isNotEmpty() && selectedIndex > 0,
                        ) { Text("Previous") }
                        TextButton(
                            onClick = {
                                val targetIndex = (selectedIndex + 1).coerceAtMost(results.lastIndex)
                                selectedIndex = targetIndex
                                results.getOrNull(targetIndex)?.let(onResultClick)
                            },
                            enabled = results.isNotEmpty() && selectedIndex < results.lastIndex,
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedIndex, results.size) {
        if (results.isNotEmpty() && selectedIndex in results.indices) listState.animateScrollToItem(selectedIndex)
    }
}

private fun highlightedSnippet(snippet: String, query: String) = buildAnnotatedString {
    if (query.isBlank()) {
        append(snippet)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < snippet.length) {
        val match = snippet.indexOf(query, startIndex = cursor, ignoreCase = true)
        if (match < 0) {
            append(snippet.substring(cursor))
            break
        }
        append(snippet.substring(cursor, match))
        withStyle(SpanStyle(background = Color(0xFFFFDF88), color = Color(0xFF1D1D1F))) {
            append(snippet.substring(match, match + query.length))
        }
        cursor = match + query.length
    }
}
