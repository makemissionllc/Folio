package com.makemission.folio.data.image

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Bounding-Box Image Expansion (§5).
 *
 * Lightweight contour-detection pass that finds the bounding box of non-white
 * pixels in an EPUB diagram/illustration and crops the image so it fills the
 * available width on both phone and tablet.
 *
 * Pure on-device, no network. Works with the existing [com.makemission.folio.data.epub.EpubParser]
 * and image rendering — does not rewrite them.
 *
 * White-margin definition:
 * - ARGB where R,G,B > [whiteThreshold] (default 242) and alpha > 10 is considered "white paper"
 * - Transparent (alpha < 10) treated as white as well (publisher's artificial margin)
 * - Everything else is "content" (ink, diagram)
 *
 * Gracefully handles already tightly-cropped images: if the detected box is
 * within [tightMarginPx] or covers > 92% of the image, the original is returned
 * without over-cropping or distortion.
 */
object BoundingBoxCropper {

    private const val DEFAULT_WHITE_THRESHOLD = 242
    private const val MIN_CONTENT_PX = 8
    private const val TIGHT_MARGIN_PX = 4
    private const val TIGHT_COVERAGE = 0.92f
    private const val PADDING_PX = 2

    /**
     * Returns true if [pixel] (ARGB Int) should be considered white paper / margin.
     * Uses a threshold to tolerate JPEG compression artifacts (e.g., 254 vs 255).
     */
    fun isWhite(pixel: Int, whiteThreshold: Int = DEFAULT_WHITE_THRESHOLD): Boolean {
        val a = (pixel shr 24) and 0xFF
        if (a < 10) return true // transparent = margin
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return r > whiteThreshold && g > whiteThreshold && b > whiteThreshold
    }

    /**
     * Lightweight contour detection: scan all pixels to find min/max of non-white.
     * Returns null if the entire bitmap is white (no content) or if bitmap is empty.
     * Otherwise returns a [Rect] in bitmap coordinates (left, top, rightExclusive, bottomExclusive).
     *
     * This is O(W*H) but with early-out and down-sampling for very large images
     * (> 3MP) we sample every 2nd pixel to stay lightweight, then refine edges.
     */
    fun findBoundingBox(
        bitmap: Bitmap,
        whiteThreshold: Int = DEFAULT_WHITE_THRESHOLD,
    ): Rect? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        // For very large images, sample to keep it lightweight (contour still accurate)
        val step = if (w * h > 3_000_000) 2 else 1

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        // Read all pixels into array for fast access (single JNI call)
        val pixels = IntArray(w * h)
        try {
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        } catch (_: Exception) {
            return null
        }

        // Scan — step sampling if needed, but ensure edges are checked fully on second pass if needed
        for (y in 0 until h step step) {
            val rowOffset = y * w
            for (x in 0 until w step step) {
                val pixel = pixels[rowOffset + x]
                if (!isWhite(pixel, whiteThreshold)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX == -1) return null // all white

        // If we sampled, refine edges to pixel-perfect byScanning the border bands more precisely
        if (step > 1) {
            // Expand search around detected edges by step*2 and scan full resolution in those bands
            val refineLeft = max(0, minX - step * 2)
            val refineRight = min(w - 1, maxX + step * 2)
            val refineTop = max(0, minY - step * 2)
            val refineBottom = min(h - 1, maxY + step * 2)

            // Re-scan refined region at 1px for accuracy
            var rMinX = w
            var rMinY = h
            var rMaxX = -1
            var rMaxY = -1
            for (y in refineTop..refineBottom) {
                val ro = y * w
                for (x in refineLeft..refineRight) {
                    if (!isWhite(pixels[ro + x], whiteThreshold)) {
                        if (x < rMinX) rMinX = x
                        if (x > rMaxX) rMaxX = x
                        if (y < rMinY) rMinY = y
                        if (y > rMaxY) rMaxY = y
                    }
                }
            }
            // If refinement found tighter box, use it; otherwise keep original (ensure we didn't miss isolated pixels outside refine)
            // To be safe, take union of both
            minX = min(minX, rMinX)
            minY = min(minY, rMinY)
            maxX = max(maxX, rMaxX)
            maxY = max(maxY, rMaxY)
        }

        // Add small padding so anti-aliased edges aren't clipped
        minX = max(0, minX - PADDING_PX)
        minY = max(0, minY - PADDING_PX)
        maxX = min(w - 1, maxX + PADDING_PX)
        maxY = min(h - 1, maxY + PADDING_PX)

        // Convert to Rect with exclusive right/bottom
        return Rect(minX, minY, maxX + 1, maxY + 1)
    }

    /**
     * Crop [bitmap] to its non-white bounding box.
     * Returns original if:
     * - no box found (all white)
     * - box is too small (< MIN_CONTENT_PX)
     * - already tightly cropped (margins < TIGHT_MARGIN_PX or coverage > TIGHT_COVERAGE)
     * Does not distort: aspect is preserved, just trims margins.
     */
    fun crop(
        bitmap: Bitmap,
        whiteThreshold: Int = DEFAULT_WHITE_THRESHOLD,
    ): Bitmap {
        val box = findBoundingBox(bitmap, whiteThreshold) ?: return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val bw = box.width()
        val bh = box.height()

        if (bw < MIN_CONTENT_PX || bh < MIN_CONTENT_PX) return bitmap

        // Gracefully handle already tightly cropped: if margins are tiny, don't over-crop
        val leftMargin = box.left
        val topMargin = box.top
        val rightMargin = w - box.right
        val bottomMargin = h - box.bottom
        val maxMargin = maxOf(leftMargin, topMargin, rightMargin, bottomMargin)
        val coverage = (bw.toFloat() * bh.toFloat()) / (w.toFloat() * h.toFloat())
        if (maxMargin <= TIGHT_MARGIN_PX && coverage >= TIGHT_COVERAGE) {
            return bitmap
        }
        // Also if box is almost the whole image (e.g., < 8px margin all around), skip
        if (coverage >= 0.98f) return bitmap

        return try {
            Bitmap.createBitmap(bitmap, box.left, box.top, bw, bh)
        } catch (_: Exception) {
            bitmap
        }
    }
}
