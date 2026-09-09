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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.ui.library.LibraryViewModel
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = SettingsRepository.get(context)
    val alwaysShow by repo.alwaysShowProgressBar.collectAsState(initial = false)
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
                    subtitle = "Progress & navigation",
                ) {
                    SettingsToggleRow(
                        title = "Always show progress bar",
                        subtitle = "When on, the reading progress bar stays visible and tap-to-hide is ignored",
                        checked = alwaysShow,
                        onCheckedChange = { checked ->
                            scope.launch { repo.setAlwaysShowProgressBar(checked) }
                        },
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Library",
                    subtitle = "Device storage & auto-scan",
                ) {
                    SettingsToggleRow(
                        title = "Auto-scan on launch",
                        subtitle = if (hasPermission) "Search Downloads, Documents & external storage for new EPUBs on app launch" else "Storage permission not granted — auto-scan unavailable",
                        checked = autoScanEnabled && hasPermission,
                        onCheckedChange = { checked ->
                            scope.launch { repo.setAutoScanEnabled(checked) }
                        },
                    )
                    if (!hasPermission) {
                        Text(
                            text = "Storage permission denied during onboarding — grant in system settings to enable scanning. Manual import via + still works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
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
                                else if (hasPermission) "Search for .epub files not yet in your library"
                                else "Unavailable — permission not granted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(
                        text = "Only EPUB for now — PDF parsing doesn't exist yet (plug-in point in EpubScanner + EpubParser). Files are copied to private storage, so originals can be moved/deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Appearance",
                    subtitle = "Theme & contrast",
                ) {
                    SettingsInfoRow(
                        title = "Folio theme",
                        subtitle = "Deep green • Burgundy • Amber — editorial elegance",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsInfoRow(
                        title = "Adaptive contrast",
                        subtitle = "Controlled in the reader (Top Bar → Contrast Auto) and via ambient light sensor",
                    )
                }
            }

            item {
                SettingsSection(
                    title = "Privacy / Data",
                    subtitle = "On-device only",
                ) {
                    SettingsInfoRow(
                        title = "Local-only reading",
                        subtitle = "Books, progress, highlights and vocabulary are stored locally via Room. No cloud sync.",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsInfoRow(
                        title = "Caches",
                        subtitle = "X-Ray index and cropped images are cached on-device and cleared with app data",
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
        Text(
            text = "Reading • Library • Appearance • Privacy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(
                    text = "Organized to grow — reading, appearance, and data stay separate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
