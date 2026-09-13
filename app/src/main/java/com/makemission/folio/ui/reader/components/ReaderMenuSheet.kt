package com.makemission.folio.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.makemission.folio.ui.reader.FolioFontSize
import com.makemission.folio.ui.reader.FolioHighlightColor
import com.makemission.folio.ui.reader.FolioHighlightStyle
import com.makemission.folio.ui.reader.FolioLineSpacing
import com.makemission.folio.ui.reader.FolioMargin
import com.makemission.folio.ui.reader.FolioReadingFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMenuSheet(
    bionicEnabled: Boolean,
    onToggleBionic: () -> Unit,
    onOpenXRay: () -> Unit,
    contrastEnabled: Boolean,
    hasSensor: Boolean,
    onToggleContrast: () -> Unit,
    bookmarksCount: Int,
    isCurrentBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onToggleSearch: () -> Unit,
    chaptersCount: Int = 0,
    onOpenChapters: () -> Unit = {},
    highlightsCount: Int = 0,
    onOpenHighlights: () -> Unit = {},
    // Reading typography (Kindle/Apple Books–like)
    readingFont: FolioReadingFont = FolioReadingFont.DEFAULT,
    onSelectReadingFont: (FolioReadingFont) -> Unit = {},
    fontSize: FolioFontSize = FolioFontSize.NORMAL,
    onSelectFontSize: (FolioFontSize) -> Unit = {},
    lineSpacing: FolioLineSpacing = FolioLineSpacing.NORMAL,
    onSelectLineSpacing: (FolioLineSpacing) -> Unit = {},
    margin: FolioMargin = FolioMargin.NORMAL,
    onSelectMargin: (FolioMargin) -> Unit = {},
    // Highlight appearance (reference AnnotationColors: multiple colors + type)
    highlightColor: FolioHighlightColor = FolioHighlightColor.AMBER,
    onSelectHighlightColor: (FolioHighlightColor) -> Unit = {},
    highlightStyle: FolioHighlightStyle = FolioHighlightStyle.FILL,
    onSelectHighlightStyle: (FolioHighlightStyle) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Reading menu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            // Search
            MenuActionRow(
                title = "Search in book",
                subtitle = "Find passages — highlights first",
                actionLabel = "Open",
                onClick = { onToggleSearch(); onDismiss() },
            )
            // Chapters — quick jump (new)
            MenuActionRow(
                title = if (chaptersCount == 0) "Chapters" else "Chapters · $chaptersCount",
                subtitle = "Jump directly to any chapter",
                actionLabel = "Open",
                onClick = { onOpenChapters(); onDismiss() },
            )
            // Highlights — quick-access list parallel to Bookmarks (new)
            MenuActionRow(
                title = if (highlightsCount == 0) "Highlights" else "Highlights · $highlightsCount",
                subtitle = "Your inked passages — tap to jump",
                actionLabel = "Open",
                onClick = { onOpenHighlights(); onDismiss() },
            )
            // Bookmarks
            MenuActionRow(
                title = if (bookmarksCount == 0) "Bookmarks" else "Bookmarks · $bookmarksCount",
                subtitle = "Saved spots — tap to jump, distinct from highlights",
                actionLabel = "Open",
                onClick = { onOpenBookmarks(); onDismiss() },
            )
            MenuActionRow(
                title = if (isCurrentBookmarked) "Remove bookmark here" else "Bookmark this page",
                subtitle = "Marks current spot (chapter + paragraph)",
                actionLabel = if (isCurrentBookmarked) "Remove" else "Add",
                onClick = { onToggleBookmark(); onDismiss() },
            )
            // People & Topics — formerly X-Ray (Story Guide)
            MenuActionRow(
                title = "People & Topics (X-Ray)",
                subtitle = "Quick map of characters, places & key ideas — on-device",
                actionLabel = "Open",
                onClick = { onOpenXRay(); onDismiss() },
            )
            // Guided Reading — formerly Bionic Reading
            MenuToggleRow(
                title = "Guided Reading (Bionic)",
                subtitle = "Bold first syllable to guide eyes — read faster, stay focused",
                checked = bionicEnabled,
                onCheckedChange = { onToggleBionic() },
            )
            // Contrast toggle
            MenuToggleRow(
                title = if (!hasSensor) "Comfort Contrast — No sensor" else if (contrastEnabled) "Comfort Contrast — Auto (7:1)" else "Comfort Contrast — Fixed",
                subtitle = " Keeps contrast comfortable in any light (WCAG 7:1, via sensor)",
                checked = contrastEnabled && hasSensor,
                enabled = hasSensor,
                onCheckedChange = { if (hasSensor) onToggleContrast() },
            )

            // — Typography (Kindle/Apple Books–like) —
            Text(
                text = "Typography",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            MenuSegmentedRow(
                label = "Font",
                options = FolioReadingFont.entries.map { it.displayName },
                selectedIndex = FolioReadingFont.entries.indexOf(readingFont),
                onSelect = { idx -> onSelectReadingFont(FolioReadingFont.entries[idx]) },
            )
            MenuSegmentedRow(
                label = "Size",
                options = FolioFontSize.entries.map { it.label },
                selectedIndex = FolioFontSize.entries.indexOf(fontSize),
                onSelect = { idx -> onSelectFontSize(FolioFontSize.entries[idx]) },
            )
            MenuSegmentedRow(
                label = "Spacing",
                options = FolioLineSpacing.entries.map { it.label },
                selectedIndex = FolioLineSpacing.entries.indexOf(lineSpacing),
                onSelect = { idx -> onSelectLineSpacing(FolioLineSpacing.entries[idx]) },
            )
            MenuSegmentedRow(
                label = "Margins",
                options = FolioMargin.entries.map { it.label },
                selectedIndex = FolioMargin.entries.indexOf(margin),
                onSelect = { idx -> onSelectMargin(FolioMargin.entries[idx]) },
            )

            // — Highlights (multiple colors + underline vs fill, reference AnnotationColors) —
            Text(
                text = "Highlights",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            HighlightColorRow(
                selected = highlightColor,
                onSelect = onSelectHighlightColor,
            )
            MenuSegmentedRow(
                label = "Style",
                options = FolioHighlightStyle.entries.map { it.label },
                selectedIndex = FolioHighlightStyle.entries.indexOf(highlightStyle),
                onSelect = { idx -> onSelectHighlightStyle(FolioHighlightStyle.entries[idx]) },
            )

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Close")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MenuActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(
                onClick = onClick,
                modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 36.dp),
            ) { Text(actionLabel, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun MenuToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun MenuSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEachIndexed { idx, opt ->
                    val selected = idx == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable { onSelect(idx) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(opt, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightColorRow(
    selected: FolioHighlightColor,
    onSelect: (FolioHighlightColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Color", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FolioHighlightColor.entries.forEach { c ->
                    val isSelected = c == selected
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c.color)
                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onSelect(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
