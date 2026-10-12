package com.makemission.folio.ui.library.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.makemission.folio.data.model.Book
import com.makemission.folio.data.model.ShelfStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBookActionsSheet(
    book: Book,
    isCurated: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onResetProgress: () -> Unit,
    onBookInfo: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isCurated) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Curated sample — included with Folio. Can't be removed, but you can reset its progress or view info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onBookInfo(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Book info", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { onResetProgress(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Reset progress", style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onRemove(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCurated,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (isCurated) "Can't remove sample" else "Remove from library", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Close")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun BookInfoDialog(
    book: Book,
    onDismiss: () -> Unit,
    onRead: () -> Unit = {},
    onStatusChange: (ShelfStatus) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    var selectedStatus by remember(book.id) { mutableStateOf(book.shelfStatus) }
    LaunchedEffect(book.shelfStatus) { selectedStatus = book.shelfStatus }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.then(if (isTablet) Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 760.dp) else Modifier.fillMaxSize()),
            shape = if (isTablet) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .then(if (isTablet) Modifier else Modifier.statusBarsPadding().navigationBarsPadding())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("‹  Library", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${book.title} by ${book.author}")
                        }
                        context.startActivity(Intent.createChooser(share, "Share book"))
                    }) { Text("Share", color = MaterialTheme.colorScheme.primary) }
                }
                Box(
                    Modifier.size(width = 174.dp, height = 260.dp)
                        .shadow(12.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                        .background(book.coverColor),
                ) {
                    if (book.coverImagePath != null) {
                        AsyncImage(
                            model = File(book.coverImagePath),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif), color = Color.White, maxLines = 4)
                            Spacer(Modifier.weight(1f))
                            Text(book.author, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Serif), color = Color.White)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(book.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onRead,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (book.lastReadMillis > 0L) "Continue Reading" else "Start Reading") }
                if (book.filePath != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                selectedStatus = if (selectedStatus == ShelfStatus.WANT) ShelfStatus.AUTO else ShelfStatus.WANT
                                onStatusChange(selectedStatus)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(if (selectedStatus == ShelfStatus.WANT) "Remove from List" else "Want to Read", maxLines = 1) }
                        OutlinedButton(
                            onClick = {
                                selectedStatus = if (selectedStatus == ShelfStatus.FINISHED) ShelfStatus.AUTO else ShelfStatus.FINISHED
                                onStatusChange(selectedStatus)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(if (selectedStatus == ShelfStatus.FINISHED) "Mark Unread" else "Mark Finished", maxLines = 1) }
                    }
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selectedStatus == ShelfStatus.FINISHED) "100%" else "${(book.progressFraction * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                            Text("Read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(book.filePath?.substringAfterLast('.')?.uppercase() ?: "GUIDE", style = MaterialTheme.typography.titleMedium)
                            Text("Format", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(when (selectedStatus) { ShelfStatus.AUTO -> if (book.lastReadMillis > 0) "Reading" else "New"; ShelfStatus.WANT -> "Saved"; ShelfStatus.FINISHED -> "Done" }, style = MaterialTheme.typography.titleMedium)
                            Text("Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
