package com.makemission.folio.ui.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.ui.theme.FolioAmber

/**
 * Zero-friction stylus highlight overlay (§4).
 *
 * - Only stylus (`PointerType.Stylus`) starts a stroke; finger touch is
 *   ignored so scrolling and tap-to-toggle still work.
 * - Stylus down instantly begins a path — no menu, no confirmation.
 * - Rendering uses [BlendMode.Multiply] with Folio amber so the text stays
 *   crisp, simulating real ink on paper (true-ink).
 *
 * Storage is handled by the parent: [onStylusStrokeFinished] receives
 * normalized points (0..1) so Room rows stay resolution-independent.
 */
@Composable
fun HighlightOverlay(
    highlights: List<Highlight>,
    onStylusStrokeFinished: (normalizedPoints: List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
    highlightColor: Color = FolioAmber,
) {
    var currentStroke by remember { mutableStateOf<List<Offset>?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(highlights) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val stylusChanges = event.changes.filter { it.type == PointerType.Stylus }
                        if (stylusChanges.isEmpty()) {
                            // Finger / mouse — don't consume, let LazyColumn handle scroll/tap.
                            continue
                        }
                        val change = stylusChanges.first()
                        when (event.type) {
                            PointerEventType.Press -> {
                                currentStroke = listOf(change.position)
                                change.consume()
                            }
                            PointerEventType.Move -> {
                                val prev = currentStroke ?: emptyList()
                                // Only append if moved noticeably to keep path light.
                                if (prev.isEmpty() || (change.position - prev.last()).getDistance() > 1.5f) {
                                    currentStroke = prev + change.position
                                }
                                change.consume()
                            }
                            PointerEventType.Release -> {
                                val finished = currentStroke
                                if (!finished.isNullOrEmpty() && finished.size > 1) {
                                    // Normalize to 0..1 relative to canvas size.
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    val normalized = finished.map { Offset(it.x / w, it.y / h) }
                                    onStylusStrokeFinished(normalized)
                                }
                                currentStroke = null
                                change.consume()
                            }
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        val strokeWidthPx = 28.dp.toPx()

        fun drawHighlight(points: List<Offset>, color: Color) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.52f),
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
                blendMode = BlendMode.Multiply,
            )
        }

        // Persisted highlights — denormalize from 0..1 to current size.
        for (hl in highlights) {
            val norm = decodePoints(hl.pointsData)
            if (norm.size < 2) continue
            val pts = norm.map { Offset(it.x * size.width, it.y * size.height) }
            val col = try { Color(hl.color) } catch (_: Exception) { highlightColor }
            drawHighlight(pts, col)
        }

        // In-progress stroke (pixel coords already).
        currentStroke?.let { pts ->
            drawHighlight(pts, highlightColor)
        }
    }
}

fun encodePoints(normalized: List<Offset>): String =
    normalized.joinToString(",") { "${it.x},${it.y}" }

fun decodePoints(data: String): List<Offset> {
    if (data.isBlank()) return emptyList()
    val parts = data.split(",")
    if (parts.size % 2 != 0) return emptyList()
    val out = mutableListOf<Offset>()
    var i = 0
    while (i + 1 < parts.size) {
        val x = parts[i].toFloatOrNull() ?: run { i += 2; continue }
        val y = parts[i + 1].toFloatOrNull() ?: run { i += 2; continue }
        out.add(Offset(x, y))
        i += 2
    }
    return out
}
