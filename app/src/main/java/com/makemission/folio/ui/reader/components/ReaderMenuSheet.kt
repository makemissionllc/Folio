package com.makemission.folio.ui.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
