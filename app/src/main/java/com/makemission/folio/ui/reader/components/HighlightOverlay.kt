package com.makemission.folio.ui.reader.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.makemission.folio.data.db.entity.Highlight
import com.makemission.folio.ui.theme.FolioAmber
import kotlin.math.PI

private data class StylusPoint(
    val offset: Offset,
    val pressure: Float,
    val tilt: Float,
)

/**
 * Zero-friction stylus highlight overlay (§4) — now with organic pressure/tilt
 * physics and lasso extraction.
 *
 * - Only stylus (`TOOL_TYPE_STYLUS`) starts a stroke; finger touch is
 *   ignored so scrolling and tap-to-toggle still work.
 * - Stylus down instantly begins a path — no menu, no confirmation.
 * - Pressure (0..1) and tilt (0..PI/2 rad) from MotionEvent dynamically
 *   scale stroke width, so highlights feel organic.
 * - Closed-loop strokes are classified as lassos (distinct from highlights);
 *   the parent decides whether to extract an image or run on-device OCR.
 * - Rendering uses [BlendMode.Multiply] with Folio amber so the text stays
 *   crisp, simulating real ink on paper (true-ink).
 */
@Composable
fun HighlightOverlay(
    highlights: List<Highlight>,
    onStylusStrokeFinished: (normalizedPoints: List<Offset>, pressures: List<Float>, tilts: List<Float>) -> Unit,
    onLassoFinished: (normalizedPoints: List<Offset>, bounds: Rect) -> Unit,
    modifier: Modifier = Modifier,
    highlightColor: Color = FolioAmber,
) {
    var currentPoints by remember { mutableStateOf<List<StylusPoint>?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInteropFilter { event ->
                if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
                    return@pointerInteropFilter false
                }
                val pressure = event.pressure.coerceIn(0f, 1f)
                val tilt = try {
                    event.getAxisValue(MotionEvent.AXIS_TILT).coerceIn(0f, (PI / 2).toFloat())
                } catch (_: Exception) {
                    0f
                }
                val pos = Offset(event.x, event.y)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentPoints = listOf(StylusPoint(pos, pressure, tilt))
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val prev = currentPoints ?: emptyList()
                        if (prev.isEmpty() || (pos - prev.last().offset).getDistance() > 1.2f) {
                            currentPoints = prev + StylusPoint(pos, pressure, tilt)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val finished = currentPoints
                        if (!finished.isNullOrEmpty() && finished.size > 1) {
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val normalized = finished.map { Offset(it.offset.x / w, it.offset.y / h) }
                            val pressures = finished.map { it.pressure }
                            val tilts = finished.map { it.tilt }
                            val pts = finished.map { it.offset }
                            if (isLassoStroke(pts)) {
                                val minX = pts.minOf { it.x }
                                val maxX = pts.maxOf { it.x }
                                val minY = pts.minOf { it.y }
                                val maxY = pts.maxOf { it.y }
                                val bounds = Rect(Offset(minX, minY), Offset(maxX, maxY))
                                onLassoFinished(normalized, bounds)
                            } else {
                                onStylusStrokeFinished(normalized, pressures, tilts)
                            }
                        }
                        currentPoints = null
                        true
                    }
                    else -> false
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseWidthPx = 28.dp.toPx()

            fun drawVariable(pts: List<Offset>, pressures: List<Float>, tilts: List<Float>, color: Color) {
                if (pts.size < 2) return
                for (i in 0 until pts.size - 1) {
                    val pa = if (pressures.size == pts.size) (pressures[i] + pressures[i + 1]) / 2f else 0.7f
                    val ta = if (tilts.size == pts.size) (tilts[i] + tilts[i + 1]) / 2f else 0f
                    val pressureFactor = 0.55f + pa * 0.9f
                    val tiltFactor = 1f + (ta / (PI / 2).toFloat()) * 0.35f
                    val width = baseWidthPx * pressureFactor * tiltFactor
                    drawLine(
                        color = color.copy(alpha = 0.52f),
                        start = pts[i],
                        end = pts[i + 1],
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Multiply,
                    )
                }
            }

            fun drawFixed(pts: List<Offset>, color: Color) {
                if (pts.size < 2) return
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.52f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = baseWidthPx,
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                    blendMode = BlendMode.Multiply,
                )
            }

            // Persisted highlights — denormalize from 0..1.
            for (hl in highlights) {
                val norm = decodePoints(hl.pointsData)
                if (norm.size < 2) continue
                val pts = norm.map { Offset(it.x * size.width, it.y * size.height) }
                val pressures = decodeFloats(hl.pressuresData)
                val tilts = decodeFloats(hl.tiltsData)
                val col = try { Color(hl.color) } catch (_: Exception) { highlightColor }
                if (pressures.size == pts.size && tilts.size == pts.size && pressures.isNotEmpty()) {
                    drawVariable(pts, pressures, tilts, col)
                } else {
                    drawFixed(pts, col)
                }
            }

            // In-progress stroke (pixel coords).
            currentPoints?.let { pts ->
                val offs = pts.map { it.offset }
                val pressures = pts.map { it.pressure }
                val tilts = pts.map { it.tilt }
                drawVariable(offs, pressures, tilts, highlightColor)
            }
        }
    }
}

/** Legacy overload — fixed-width, no lasso (kept for compatibility). */
@Composable
fun HighlightOverlay(
    highlights: List<Highlight>,
    onStylusStrokeFinished: (normalizedPoints: List<Offset>) -> Unit,
    modifier: Modifier = Modifier,
    highlightColor: Color = FolioAmber,
) {
    HighlightOverlay(
        highlights = highlights,
        onStylusStrokeFinished = { pts, _, _ -> onStylusStrokeFinished(pts) },
        onLassoFinished = { _, _ -> },
        modifier = modifier,
        highlightColor = highlightColor,
    )
}

private fun isLassoStroke(points: List<Offset>): Boolean {
    if (points.size < 18) return false
    val first = points.first()
    val last = points.last()
    val closeDist = (last - first).getDistance()
    if (closeDist > 72f) return false
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val w = maxX - minX
    val h = maxY - minY
    if (w < 60f || h < 60f) return false
    val aspect = w / h
    if (aspect > 3.5f || aspect < 0.28f) return false
    var len = 0f
    for (i in 1 until points.size) len += (points[i] - points[i - 1]).getDistance()
    val perim = 2 * (w + h)
    if (len < perim * 0.55f) return false
    val area = w * h
    if (area < 80f * 80f) return false
    return true
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

fun encodeFloats(values: List<Float>): String = values.joinToString(",")
fun decodeFloats(data: String): List<Float> {
    if (data.isBlank()) return emptyList()
    return data.split(",").mapNotNull { it.toFloatOrNull() }
}
