package com.makemission.folio.ui.reader.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.image.BoundingBoxCropper
import com.makemission.folio.data.image.CroppedImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bounding-Box Image Expansion (§5) — demo diagram for ReadingScreen.
 *
 * Builds on top of existing [com.makemission.folio.data.epub.EpubParser] diagram
 * placeholder and lasso extraction — does not rewrite them. Shows a diagram that
 * intentionally has large hardcoded white margins (as many technical EPUBs do).
 * When tapped, runs a lightweight on-device contour-detection pass
 * ([BoundingBoxCropper.findBoundingBox]) to locate non-white pixels, crops to
 * that box, caches the result ([CroppedImageCache]), and re-renders filling the
 * available width on both phone and tablet without distortion. Already tightly
 * cropped images are left untouched.
 *
 * Pure on-device, no network.
 */
@Composable
fun ExpandableDiagram(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Generate a sample bitmap with baked-in white margins (simulates EPUB image).
    // 600x360 with 100px white border on each side, inner amber diagram 400x160.
    // This makes the "before" look awkwardly small, "after" fills width.
    val originalBitmap = remember {
        generateSampleDiagramBitmap()
    }

    // Cache key for this demo image (stable across rotations)
    val cacheKey = remember(originalBitmap) {
        CroppedImageCache.keyFor("sample_diagram_fig1", originalBitmap.width, originalBitmap.height)
    }

    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isExpanded by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    // Try to load cached cropped version on first composition
    LaunchedEffect(cacheKey) {
        val cached = withContext(Dispatchers.IO) {
            try { CroppedImageCache.getBitmap(context, cacheKey) } catch (_: Exception) { null }
        }
        if (cached != null && cached != originalBitmap) {
            croppedBitmap = cached
            // Don't auto-expand; let user tap to see effect, but cache is ready
            statusText = "Cached cropped ready — tap to expand"
        }
    }

    val displayBitmap = if (isExpanded && croppedBitmap != null) croppedBitmap else originalBitmap
    val isShowingCropped = isExpanded && croppedBitmap != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = !isProcessing) {
                if (isShowingCropped) {
                    // Toggle back to original (still cached)
                    isExpanded = false
                    statusText = "Showing original with white margins — tap to expand"
                } else {
                    // Expand: if already cropped, just show it; otherwise run detection
                    val already = croppedBitmap
                    if (already != null && already != originalBitmap) {
                        isExpanded = true
                        statusText = "White margins stripped — fills width (cached)"
                    } else {
                        isProcessing = true
                        statusText = "Detecting content bounds…"
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                try {
                                    // Check cache again (in case another thread filled it)
                                    val cachedNow = CroppedImageCache.getBitmap(context, cacheKey)
                                    if (cachedNow != null) return@withContext cachedNow to true

                                    val cropped = BoundingBoxCropper.crop(originalBitmap)
                                    // Handle already tightly cropped gracefully
                                    val isSame = cropped === originalBitmap || (cropped.width == originalBitmap.width && cropped.height == originalBitmap.height)
                                    if (isSame) {
                                        // Cache original as processed to avoid re-running
                                        try { CroppedImageCache.putBitmap(context, cacheKey, originalBitmap) } catch (_: Exception) {}
                                        originalBitmap to false
                                    } else {
                                        CroppedImageCache.putBitmap(context, cacheKey, cropped)
                                        cropped to true
                                    }
                                } catch (_: Exception) {
                                    originalBitmap to false
                                }
                            }
                            val (bmp, didCrop) = result
                            croppedBitmap = bmp
                            isExpanded = true
                            isProcessing = false
                            statusText = if (didCrop) "White margins stripped — fills width (on-device)"
                            else "Already tightly cropped — no change"
                        }
                    }
                }
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Text(
            text = "◈  Fig. 1 — Folio Diagram",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = displayBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Folio diagram (tap to ${if (isShowingCropped) "restore" else "expand"})",
                    modifier = Modifier
                        .fillMaxWidth()
                        // Keep aspect of the bitmap, but let it fill width
                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusText ?: if (isProcessing) "Processing…" else "Tap diagram to strip white margins and expand full-width (on-device contour detection, cached)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (isShowingCropped) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${originalBitmap.width}×${originalBitmap.height} → ${croppedBitmap?.width}×${croppedBitmap?.height} • cached • phone & tablet full-width",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Original has ${originalBitmap.width}×${originalBitmap.height} with baked white margins • pure on-device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Generates a sample diagram bitmap that simulates a technical EPUB illustration
 * with large hardcoded white margins. White background + centered amber diagram
 * with Folio text and a border, leaving ~100px white on each side and 80px top/bottom.
 */
private fun generateSampleDiagramBitmap(): Bitmap {
    val w = 600
    val h = 360
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // White paper background (the "margin")
    canvas.drawColor(AndroidColor.WHITE)

    // Inner diagram bounds with simulated margins
    val marginLeft = 100
    val marginTop = 80
    val marginRight = 100
    val marginBottom = 80
    val innerLeft = marginLeft.toFloat()
    val innerTop = marginTop.toFloat()
    val innerRight = (w - marginRight).toFloat()
    val innerBottom = (h - marginBottom).toFloat()
    val innerW = innerRight - innerLeft
    val innerH = innerBottom - innerTop

    // Diagram fill — Folio amber with deep-green border
    val fillPaint = Paint().apply {
        color = AndroidColor.parseColor("#F7B538")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val borderPaint = Paint().apply {
        color = AndroidColor.parseColor("#004F39")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    val radius = 12f
    canvas.drawRoundRect(innerLeft, innerTop, innerRight, innerBottom, radius, radius, fillPaint)
    canvas.drawRoundRect(innerLeft, innerTop, innerRight, innerBottom, radius, radius, borderPaint)

    // Diagram content: simple axes + label to look like a technical figure
    val axisPaint = Paint().apply {
        color = AndroidColor.parseColor("#780116")
        strokeWidth = 3f
        isAntiAlias = true
    }
    // X axis
    canvas.drawLine(innerLeft + 24f, innerBottom - 32f, innerRight - 24f, innerBottom - 32f, axisPaint)
    // Y axis
    canvas.drawLine(innerLeft + 32f, innerTop + 24f, innerLeft + 32f, innerBottom - 32f, axisPaint)
    // Sample bars
    val barPaint = Paint().apply {
        color = AndroidColor.parseColor("#004F39")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    val barW = innerW * 0.12f
    val barGap = innerW * 0.04f
    var x = innerLeft + 48f
    val baseY = innerBottom - 32f
    val heights = listOf(0.45f, 0.7f, 0.55f, 0.85f)
    for (hh in heights) {
        val bh = innerH * 0.6f * hh
        canvas.drawRect(x, baseY - bh, x + barW, baseY, barPaint)
        x += barW + barGap
    }

    // Label
    val textPaint = Paint().apply {
        color = AndroidColor.parseColor("#780116")
        textSize = 22f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("FOLIO DIAGRAM — MARGINS", w / 2f, innerTop + 28f, textPaint)
    val subPaint = Paint().apply {
        color = AndroidColor.parseColor("#003325")
        textSize = 14f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Tap to strip white margins (contour detection)", w / 2f, innerBottom - 10f, subPaint)

    return bmp
}

/**
 * For EPUB-embedded images: convenience to display any bitmap with the same
 * bounding-box expansion behavior. Reuses the same cropper + cache.
 * This is the extension point for real EPUB <img> tags without rewriting EpubParser.
 */
@Composable
fun ExpandableEpubImage(
    bitmap: Bitmap,
    contentDescription: String?,
    cacheKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var displayBmp by remember(bitmap, cacheKey) { mutableStateOf(bitmap) }
    var isCropped by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // Try load cached on launch
    LaunchedEffect(cacheKey) {
        val cached = withContext(Dispatchers.IO) {
            try { CroppedImageCache.getBitmap(context, cacheKey) } catch (_: Exception) { null }
        }
        if (cached != null) {
            displayBmp = cached
            isCropped = cached !== bitmap
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(enabled = !isProcessing) {
                if (isCropped) {
                    displayBmp = bitmap
                    isCropped = false
                } else {
                    val already = displayBmp
                    if (already !== bitmap && already.width != bitmap.width) {
                        // Already showing cropped from cache
                        displayBmp = already
                        isCropped = true
                    } else {
                        isProcessing = true
                        scope.launch {
                            val cropped = withContext(Dispatchers.Default) {
                                val cachedNow = try { CroppedImageCache.getBitmap(context, cacheKey) } catch (_: Exception) { null }
                                if (cachedNow != null) return@withContext cachedNow
                                val c = BoundingBoxCropper.crop(bitmap)
                                if (c !== bitmap) CroppedImageCache.putBitmap(context, cacheKey, c)
                                c
                            }
                            displayBmp = cropped
                            isCropped = cropped !== bitmap
                            isProcessing = false
                        }
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = displayBmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(displayBmp.width.toFloat() / displayBmp.height.toFloat().coerceAtLeast(1f)),
            contentScale = ContentScale.FillWidth,
        )
    }
}
