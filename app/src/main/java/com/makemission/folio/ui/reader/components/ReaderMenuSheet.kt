package com.makemission.folio.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.makemission.folio.ui.reader.FolioFontSize
import com.makemission.folio.ui.reader.FolioHighlightColor
import com.makemission.folio.ui.reader.FolioHighlightStyle
import com.makemission.folio.ui.reader.FolioLineSpacing
import com.makemission.folio.ui.reader.FolioMargin
import com.makemission.folio.ui.reader.FolioReadingFont
import com.makemission.folio.ui.reader.ReaderPageStyle
import com.makemission.folio.ui.reader.ReadingNavigationMode
import com.makemission.folio.ui.theme.FolioReadingPaper
import com.makemission.folio.ui.theme.FolioLightColorScheme
import com.makemission.folio.ui.theme.FolioSlateColorScheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMenuSheet(
    bionicEnabled: Boolean,
    onToggleBionic: () -> Unit,
    onOpenXRay: () -> Unit,
    contrastEnabled: Boolean,
    hasSensor: Boolean,
    onToggleContrast: () -> Unit,
    keepScreenAwake: Boolean,
    onToggleKeepScreenAwake: () -> Unit,
    pageStyle: ReaderPageStyle,
    onSelectPageStyle: (ReaderPageStyle) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    navigationMode: ReadingNavigationMode,
    onSelectNavigationMode: (ReadingNavigationMode) -> Unit,
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
    isTablet: Boolean = false,
    sleepTimerMinutesLeft: Int = 0,
    onSetSleepTimer: (Int) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFullSettings by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    if (isTablet && !showFullSettings) {
        TabletReaderSettingsPopover(
            pageStyle = pageStyle,
            onSelectPageStyle = onSelectPageStyle,
            brightness = brightness,
            onBrightnessChange = onBrightnessChange,
            fontSize = fontSize,
            onSelectFontSize = onSelectFontSize,
            margin = margin,
            onSelectMargin = onSelectMargin,
            readingFont = readingFont,
            onSelectReadingFont = onSelectReadingFont,
            onMore = { showFullSettings = true },
            onDismiss = onDismiss,
        )
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var brightnessSlider by remember(brightness) { mutableFloatStateOf(if (brightness < 0f) 0.7f else brightness) }
    var fontSlider by remember(fontSize) { mutableFloatStateOf(FolioFontSize.entries.indexOf(fontSize).toFloat()) }
    var marginSlider by remember(margin) { mutableFloatStateOf(FolioMargin.entries.indexOf(margin).toFloat()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Reading Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Dim", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = brightnessSlider,
                    onValueChange = { brightnessSlider = it },
                    onValueChangeFinished = { onBrightnessChange(brightnessSlider) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("Sun", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("A", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = fontSlider,
                    onValueChange = { fontSlider = it },
                    onValueChangeFinished = { onSelectFontSize(FolioFontSize.entries[fontSlider.roundToInt().coerceIn(FolioFontSize.entries.indices)]) },
                    valueRange = 0f..FolioFontSize.entries.lastIndex.toFloat(),
                    steps = FolioFontSize.entries.size - 2,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text("A", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ReaderAppearanceRow(pageStyle = pageStyle, onSelect = onSelectPageStyle)
            MenuToggleRow(
                title = "Auto Night Mode",
                subtitle = "Follow your device appearance",
                checked = pageStyle == ReaderPageStyle.AUTO,
                onCheckedChange = { onSelectPageStyle(if (it) ReaderPageStyle.AUTO else ReaderPageStyle.PAPER) },
            )
            MenuSegmentedRow(
                label = "Navigation",
                options = listOf("Scroll", "Slide"),
                selectedIndex = if (navigationMode == ReadingNavigationMode.CONTINUOUS) 0 else 1,
                onSelect = { onSelectNavigationMode(if (it == 0) ReadingNavigationMode.CONTINUOUS else ReadingNavigationMode.CHAPTER_SWIPE) },
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
                title = if (highlightsCount == 0) "Notes & Highlights" else "Notes & Highlights · $highlightsCount",
                subtitle = "Review passages and add notes",
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
            MenuToggleRow(
                title = "Keep screen awake",
                subtitle = "Only while this reader is open",
                checked = keepScreenAwake,
                onCheckedChange = { onToggleKeepScreenAwake() },
            )
            MenuActionRow(
                title = "Sleep timer",
                subtitle = if (sleepTimerMinutesLeft > 0) "Screen dims in about $sleepTimerMinutesLeft min" else "Dim the screen when you’re done reading",
                actionLabel = if (sleepTimerMinutesLeft > 0) "Change" else "Set",
                onClick = { showSleepTimerDialog = true },
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
                label = "Spacing",
                options = FolioLineSpacing.entries.map { it.label },
                selectedIndex = FolioLineSpacing.entries.indexOf(lineSpacing),
                onSelect = { idx -> onSelectLineSpacing(FolioLineSpacing.entries[idx]) },
            )
            Text("Margins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = marginSlider,
                onValueChange = { marginSlider = it },
                onValueChangeFinished = { onSelectMargin(FolioMargin.entries[marginSlider.roundToInt().coerceIn(FolioMargin.entries.indices)]) },
                valueRange = 0f..FolioMargin.entries.lastIndex.toFloat(),
                steps = FolioMargin.entries.size - 2,
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
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dim the display and let your device’s screen timeout turn it off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            onClick = {
                                onSetSleepTimer(minutes)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("$minutes minutes", modifier = Modifier.fillMaxWidth()) }
                    }
                    if (sleepTimerMinutesLeft > 0) {
                        TextButton(
                            onClick = {
                                onSetSleepTimer(0)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Turn off timer", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun TabletReaderSettingsPopover(
    pageStyle: ReaderPageStyle,
    onSelectPageStyle: (ReaderPageStyle) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    fontSize: FolioFontSize,
    onSelectFontSize: (FolioFontSize) -> Unit,
    margin: FolioMargin,
    onSelectMargin: (FolioMargin) -> Unit,
    readingFont: FolioReadingFont,
    onSelectReadingFont: (FolioReadingFont) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = pageStyle == ReaderPageStyle.NIGHT ||
        (pageStyle == ReaderPageStyle.AUTO && isSystemInDarkTheme())
    val offset = with(LocalDensity.current) { IntOffset((-24).dp.roundToPx(), 76.dp.roundToPx()) }
    var brightnessSlider by remember(brightness) { mutableFloatStateOf(if (brightness < 0f) 0.7f else brightness) }
    var fontSlider by remember(fontSize) { mutableFloatStateOf(FolioFontSize.entries.indexOf(fontSize).toFloat()) }
    var marginSlider by remember(margin) { mutableFloatStateOf(FolioMargin.entries.indexOf(margin).toFloat()) }
    Popup(
        alignment = Alignment.TopEnd,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        MaterialTheme(colorScheme = if (dark) FolioSlateColorScheme else FolioLightColorScheme) {
            Card(
                modifier = Modifier.width(380.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderAppearanceRow(pageStyle, onSelectPageStyle)
                    Text("Brightness", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        onValueChangeFinished = { onBrightnessChange(brightnessSlider) },
                        valueRange = 0.05f..1f,
                    )
                    Text("Text size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = fontSlider,
                        onValueChange = { fontSlider = it },
                        onValueChangeFinished = { onSelectFontSize(FolioFontSize.entries[fontSlider.roundToInt().coerceIn(FolioFontSize.entries.indices)]) },
                        valueRange = 0f..FolioFontSize.entries.lastIndex.toFloat(),
                        steps = FolioFontSize.entries.size - 2,
                    )
                    Text("Margins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = marginSlider,
                        onValueChange = { marginSlider = it },
                        onValueChangeFinished = { onSelectMargin(FolioMargin.entries[marginSlider.roundToInt().coerceIn(FolioMargin.entries.indices)]) },
                        valueRange = 0f..FolioMargin.entries.lastIndex.toFloat(),
                        steps = FolioMargin.entries.size - 2,
                    )
                    MenuSegmentedRow(
                        label = "Font",
                        options = listOf("Serif", "Sans", "Mono"),
                        selectedIndex = when (readingFont) {
                            FolioReadingFont.SANS -> 1
                            FolioReadingFont.MONO -> 2
                            else -> 0
                        },
                        onSelect = { index ->
                            onSelectReadingFont(when (index) {
                                1 -> FolioReadingFont.SANS
                                2 -> FolioReadingFont.MONO
                                else -> FolioReadingFont.DEFAULT
                            })
                        },
                    )
                    TextButton(onClick = onMore, modifier = Modifier.align(Alignment.End)) { Text("More settings") }
                }
            }
        }
    }
}

@Composable
private fun ReaderAppearanceRow(pageStyle: ReaderPageStyle, onSelect: (ReaderPageStyle) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val selected = if (pageStyle == ReaderPageStyle.AUTO) {
        if (systemDark) ReaderPageStyle.NIGHT else ReaderPageStyle.PAPER
    } else pageStyle
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(ReaderPageStyle.PAPER, "Paper", FolioReadingPaper),
            Triple(ReaderPageStyle.WHITE, "White", Color.White),
            Triple(ReaderPageStyle.SEPIA, "Sepia", Color(0xFFE8D9BB)),
            Triple(ReaderPageStyle.NIGHT, "Night", Color(0xFF111114)),
        ).forEach { (style, label, swatch) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(68.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(swatch)
                        .border(2.dp, if (selected == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp))
                        .clickable { onSelect(style) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", style = MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif), color = if (style == ReaderPageStyle.NIGHT) Color.White else Color(0xFF1D1D1F))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
            }
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
