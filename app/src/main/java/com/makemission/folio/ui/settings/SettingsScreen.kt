package com.makemission.folio.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.ui.library.LibraryViewModel
import com.makemission.folio.ui.reader.ReadingNavigationMode
import com.makemission.folio.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Folio Settings — room to grow, organized into clear sections
 * (Reading / Library / Appearance / Privacy/Data).
 * Uses existing FolioTheme (deep green / burgundy / amber, heavy sans headers,
 * serif body) so it feels consistent, not generic Android settings.
 * Library section hosts auto-scan toggle + manual "Scan device" (EpubScanner).
 * Structural inspiration from book-story-master's SettingsScreen (sections -> sub-screens)
 * but Folio-specific and self-contained.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onInsightsClick: () -> Unit = {},
    onSmartFeaturesClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = SettingsRepository.get(context)
    val alwaysShow by repo.alwaysShowProgressBar.collectAsState(initial = false)
    val hapticsEnabled by repo.hapticsEnabled.collectAsState(initial = true)
    val autoScanEnabled by repo.autoScanEnabled.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Share LibraryViewModel (activity-scoped) so scan progress/snackbar are unified with Library screen
    val activity = context as? ComponentActivity
    val libraryViewModel: LibraryViewModel = if (activity != null) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }
    val isScanning by libraryViewModel.isScanning.collectAsState()
    val scanProgress by libraryViewModel.scanProgress.collectAsState()
    val hasPermission = EpubScanner.hasStoragePermission(context)

    LaunchedEffect(Unit) {
        libraryViewModel.scanResult.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SettingsHeader(onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Illustration header — editorial, flat, Folio palette (amber sun + books)
            item { SettingsHero() }

            item {
                SettingsSection(
                    title = "Reading",
                    subtitle = "",
                ) {
                    SettingsToggleRow(
                        title = "Always show progress bar",
                        subtitle = "",
                        checked = alwaysShow,
                        onCheckedChange = { checked ->
                            scope.launch { repo.setAlwaysShowProgressBar(checked) }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Haptic feedback",
                        subtitle = "On chapter turn",
                        checked = hapticsEnabled,
                        onCheckedChange = { checked ->
                            scope.launch { repo.setHapticsEnabled(checked) }
                        },
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val navigationMode by repo.readingNavigationMode.collectAsState(initial = ReadingNavigationMode.CONTINUOUS)
                    ReadingNavigationModeSegmentedControl(
                        selected = navigationMode,
                        onSelect = { mode -> scope.launch { repo.setReadingNavigationMode(mode) } }
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Library",
                    subtitle = "",
                ) {
                    SettingsToggleRow(
                        title = "Auto-scan on launch",
                        subtitle = if (hasPermission) "Downloads & Documents" else "Needs permission",
                        checked = autoScanEnabled && hasPermission,
                        onCheckedChange = { checked ->
                            scope.launch { repo.setAutoScanEnabled(checked) }
                        },
                    )
                    if (!hasPermission) {
                        Text(
                            text = "Grant in system settings to enable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Scan device",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (isScanning) (scanProgress ?: "Scanning…")
                                else "Find new EPUBs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Button(
                                onClick = { libraryViewModel.scanDevice(context) },
                                enabled = hasPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text("Scan", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Insights",
                    subtitle = "",
                ) {
                    Button(
                        onClick = onInsightsClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Open", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Smart Features",
                    subtitle = "",
                ) {
                    Button(
                        onClick = onSmartFeaturesClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Explore", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Appearance",
                    subtitle = "",
                ) {
                    val themeMode by repo.themeMode.collectAsState(initial = ThemeMode.AUTO)
                    val selectedPalette by repo.darkPalette.collectAsState(initial = com.makemission.folio.ui.theme.FolioPalette.SLATE)
                    val timeTintEnabled by repo.timeTintEnabled.collectAsState(initial = false)
                    ThemeModeSegmentedControl(
                        selected = themeMode,
                        onSelect = { mode -> scope.launch { repo.setThemeMode(mode) } }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Dark palette",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    com.makemission.folio.ui.theme.FolioPalette.entries.forEach { palette ->
                        PaletteOptionRow(
                            palette = palette,
                            selected = selectedPalette == palette,
                            onClick = { scope.launch { repo.setDarkPalette(palette) } }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = "Cool Slate is the default in dark mode. Your pick persists across Light / Dark / Auto.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsToggleRow(
                        title = "Evening warmth",
                        subtitle = "Warmer tones after sunset",
                        checked = timeTintEnabled,
                        onCheckedChange = { checked -> scope.launch { repo.setTimeTintEnabled(checked) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsInfoRow(
                        title = "Adaptive contrast",
                        subtitle = "Reader → Contrast Auto",
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Support / Developer",
                    subtitle = "",
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Local log for troubleshooting. Nothing sent automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onLogsClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("View logs", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            item {
                SettingsSection(
                    title = "Privacy / Data",
                    subtitle = "",
                ) {
                    SettingsInfoRow(
                        title = "Local-only reading",
                        subtitle = "No cloud sync",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsInfoRow(
                        title = "Caches",
                        subtitle = "Cleared with app data",
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsHeader(
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
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SettingsHero(modifier: Modifier = Modifier) {
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
            // Small flat illustration: amber circle + green/burgundy books
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
                    drawCircle(color = amber, radius = 14f, center = center.copy(y = center.y - 6f))
                    // books as rects
                    drawRoundRect(color = burgundy, topLeft = center.copy(x = center.x - 18f, y = center.y + 8f), size = androidx.compose.ui.geometry.Size(12f, 14f))
                    drawRoundRect(color = green, topLeft = center.copy(x = center.x - 4f, y = center.y + 8f), size = androidx.compose.ui.geometry.Size(12f, 14f))
                    drawRoundRect(color = amber.copy(alpha = 0.9f), topLeft = center.copy(x = center.x + 10f, y = center.y + 8f), size = androidx.compose.ui.geometry.Size(10f, 14f))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Folio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
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
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.03f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "toggleScale"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = if (subtitle.length < 30) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun PaletteOptionRow(
    palette: com.makemission.folio.ui.theme.FolioPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = com.makemission.folio.ui.theme.folioDarkSchemeFor(palette)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Preview swatches: background / surfaceVariant / amber / burgundy
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(scheme.background)
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(com.makemission.folio.ui.theme.FolioAmber)
                )
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(com.makemission.folio.ui.theme.FolioBurgundy)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = palette.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = palette.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.9f),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeSegmentedControl(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (mode) {
                                ThemeMode.LIGHT -> "☀"
                                ThemeMode.DARK -> "☾"
                                ThemeMode.AUTO -> "◐"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Text(
            text = when (selected) {
                ThemeMode.LIGHT -> "Always light — paper even if system is dark."
                ThemeMode.DARK -> "Always dark — Cool Slate by default."
                ThemeMode.AUTO -> "Matches your device setting."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ReadingNavigationModeSegmentedControl(
    selected: ReadingNavigationMode,
    onSelect: (ReadingNavigationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Navigation",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ReadingNavigationMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable { onSelect(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (mode) {
                                ReadingNavigationMode.CONTINUOUS -> "↕"
                                ReadingNavigationMode.CHAPTER_SWIPE -> "⇄"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            text = when (selected) {
                ReadingNavigationMode.CONTINUOUS -> "Vertical through whole book."
                ReadingNavigationMode.CHAPTER_SWIPE -> "Vertical within chapter, swipe left/right between chapters."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
