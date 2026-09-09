package com.makemission.folio.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.makemission.folio.data.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * First-launch onboarding — Folio introduction + upfront permission requests.
 * Shown only on first launch (tracked via DataStore hasSeenOnboarding, reusing
 * existing SettingsRepository pattern). Uses FolioTheme visual style (deep green,
 * burgundy, amber, heavy sans + serif) not generic templates. Structural
 * inspiration from book-story-master's StartScreen (pager + sections) only.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SettingsRepository.get(context) }

    val pagerState = rememberPagerState(pageCount = { 4 })
    // Permission state tracking
    var storageGranted by remember {
        mutableStateOf(checkStorageGranted(context))
    }
    var notificationsGranted by remember {
        mutableStateOf(checkNotificationsGranted(context))
    }
    var storageRequested by remember { mutableStateOf(false) }
    var notificationsRequested by remember { mutableStateOf(false) }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        storageRequested = true
        // If any granted, consider granted
        storageGranted = results.values.any { it } || checkStorageGranted(context)
    }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsRequested = true
        notificationsGranted = granted || checkNotificationsGranted(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (pagerState.currentPage < 3) {
                    TextButton(onClick = {
                        scope.launch {
                            repo.setHasSeenOnboarding(true)
                            onComplete()
                        }
                    }) {
                        Text("Skip", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> OnboardingPageWelcome()
                    1 -> OnboardingPageStylus()
                    2 -> OnboardingPageSmart()
                    3 -> OnboardingPagePrivacy(
                        storageGranted = storageGranted,
                        storageRequested = storageRequested,
                        notificationsGranted = notificationsGranted,
                        notificationsRequested = notificationsRequested,
                        onRequestStorage = {
                            val perms = storagePermissions()
                            if (perms.isNotEmpty()) storageLauncher.launch(perms)
                            else storageRequested = true
                        },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationsRequested = true
                                notificationsGranted = true
                            }
                        },
                    )
                }
            }

            // Dots indicator
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(4) { idx ->
                    val isSelected = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 12.dp else 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.size(64.dp))
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            scope.launch {
                                repo.setHasSeenOnboarding(true)
                                onComplete()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = if (pagerState.currentPage == 3) "Get started" else "Next",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageWelcome(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIllustrationWelcome()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to Folio",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Premium, distraction-free reading — where digital convenience meets the tactile craft of traditional bookmaking.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Magazine-quality typography • Editorial library • Fluid stylus",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingPageStylus(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIllustrationStylus()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Write like paper",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Zero-friction stylus highlighting with true-ink Multiply, pressure & tilt physics, and lasso extraction for diagrams or text — instantly, without menus.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingPageSmart(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIllustrationSmart()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Smart, on-device",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Bionic reading, X-Ray, True-Page, Knuth-Plass, SM-2 vocabulary and colorimetric contrast — all deterministic algorithms, no cloud, private by design.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingPagePrivacy(
    storageGranted: Boolean,
    storageRequested: Boolean,
    notificationsGranted: Boolean,
    notificationsRequested: Boolean,
    onRequestStorage: () -> Unit,
    onRequestNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingIllustrationPrivacy()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Private by design",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Books, progress, highlights and vocabulary stay on-device via Room. No account, no tracking. Permissions below help Folio find books and keep you updated — but you can always import manually.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionRow(
                    title = "Files & storage",
                    subtitle = "Find EPUBs automatically on your device (for auto-scanning). Without this, use + to import manually.",
                    granted = storageGranted,
                    requested = storageRequested,
                    onRequest = onRequestStorage,
                )
                PermissionRow(
                    title = "Notifications",
                    subtitle = "For future reading reminders and vocabulary due alerts. Optional.",
                    granted = notificationsGranted,
                    requested = notificationsRequested,
                    onRequest = onRequestNotifications,
                )
                if (storageRequested && !storageGranted) {
                    Text(
                        text = "Storage permission denied — you can still import books manually with the + button.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (notificationsRequested && !notificationsGranted) {
                    Text(
                        text = "Notifications denied — Folio will remain silent. You can enable later in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    requested: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (granted) {
                Text(text = "✓ Granted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else if (requested) {
                Text(text = "Not granted — manual import still works", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!granted) {
            TextButton(onClick = onRequest) {
                Text("Allow")
            }
        } else {
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

// --- Folio-styled illustrations (flat, editorial, deep green / burgundy / amber) ---

@Composable
private fun OnboardingIllustrationWelcome(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
            val green = androidx.compose.ui.graphics.Color(0xFF004F39)
            val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
            drawCircle(color = amber, radius = 28f, center = center.copy(y = center.y - 12f))
            // shelf
            drawRoundRect(color = green.copy(alpha = 0.9f), topLeft = center.copy(x = center.x - 36f, y = center.y + 16f), size = androidx.compose.ui.geometry.Size(72f, 6f))
            // books
            drawRoundRect(color = burgundy, topLeft = center.copy(x = center.x - 30f, y = center.y - 4f), size = androidx.compose.ui.geometry.Size(14f, 20f))
            drawRoundRect(color = green, topLeft = center.copy(x = center.x - 12f, y = center.y - 4f), size = androidx.compose.ui.geometry.Size(14f, 20f))
            drawRoundRect(color = amber, topLeft = center.copy(x = center.x + 6f, y = center.y - 4f), size = androidx.compose.ui.geometry.Size(14f, 20f))
            drawRoundRect(color = burgundy.copy(alpha = 0.8f), topLeft = center.copy(x = center.x + 22f, y = center.y - 4f), size = androidx.compose.ui.geometry.Size(8f, 20f))
        }
    }
}

@Composable
private fun OnboardingIllustrationStylus(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
            val green = androidx.compose.ui.graphics.Color(0xFF004F39)
            val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
            // paper
            drawRoundRect(color = androidx.compose.ui.graphics.Color(0xFFFFFBF0), topLeft = center.copy(x = center.x - 32f, y = center.y - 24f), size = androidx.compose.ui.geometry.Size(64f, 48f))
            // highlight stroke with multiply feel
            drawRoundRect(color = amber.copy(alpha = 0.9f), topLeft = center.copy(x = center.x - 24f, y = center.y - 6f), size = androidx.compose.ui.geometry.Size(48f, 10f))
            // stylus
            drawRoundRect(color = burgundy, topLeft = center.copy(x = center.x + 10f, y = center.y - 28f), size = androidx.compose.ui.geometry.Size(4f, 36f))
            drawCircle(color = green, radius = 6f, center = center.copy(x = center.x + 12f, y = center.y - 30f))
        }
    }
}

@Composable
private fun OnboardingIllustrationSmart(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
            val green = androidx.compose.ui.graphics.Color(0xFF004F39)
            val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
            // chip / on-device
            drawRoundRect(color = green, topLeft = center.copy(x = center.x - 28f, y = center.y - 18f), size = androidx.compose.ui.geometry.Size(56f, 36f))
            drawCircle(color = amber, radius = 10f, center = center)
            // rays
            val ray = androidx.compose.ui.graphics.Color(0xFFF7B538).copy(alpha = 0.6f)
            drawCircle(color = ray, radius = 18f, center = center)
            drawRoundRect(color = burgundy.copy(alpha = 0.7f), topLeft = center.copy(x = center.x - 8f, y = center.y + 18f), size = androidx.compose.ui.geometry.Size(16f, 8f))
        }
    }
}

@Composable
private fun OnboardingIllustrationPrivacy(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(90.dp)) {
            val amber = androidx.compose.ui.graphics.Color(0xFFF7B538)
            val green = androidx.compose.ui.graphics.Color(0xFF004F39)
            val burgundy = androidx.compose.ui.graphics.Color(0xFF780116)
            // shield
            drawRoundRect(color = green, topLeft = center.copy(x = center.x - 22f, y = center.y - 20f), size = androidx.compose.ui.geometry.Size(44f, 40f))
            drawCircle(color = amber, radius = 8f, center = center.copy(y = center.y - 2f))
            // lock
            drawRoundRect(color = burgundy, topLeft = center.copy(x = center.x - 6f, y = center.y + 8f), size = androidx.compose.ui.geometry.Size(12f, 10f))
        }
    }
}

private fun storagePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun checkStorageGranted(context: android.content.Context): Boolean {
    val perms = storagePermissions()
    return perms.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

private fun checkNotificationsGranted(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true // not applicable pre-33
    }
}
