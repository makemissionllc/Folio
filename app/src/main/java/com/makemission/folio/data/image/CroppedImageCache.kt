package com.makemission.folio.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Cache for cropped results so white-margin stripping isn't recalculated
 * every time the image is viewed (§5 requirement).
 *
 * Pure on-device, no network. Disk cache under filesDir/bbox_cache.
 * Also keeps a tiny in-memory map for the current session.
 */
object CroppedImageCache {

    private const val DIR_NAME = "bbox_cache"
    private const val MAX_DISK_FILES = 80

    // Simple in-memory holder for already decoded cropped bitmaps (key -> bitmap)
    private val memoryCache = mutableMapOf<String, Bitmap>()

    private fun cacheDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Stable key for an image: uses provided [key] (e.g., href or generated id)
     * plus bitmap dimensions to avoid collisions. Hash with SHA-256 hex.
     */
    fun keyFor(key: String, width: Int, height: Int): String {
        val raw = "$key:${width}x${height}"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(raw.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(24)
        } catch (_: Exception) {
            raw.hashCode().toString()
        }
    }

    fun cacheFile(context: Context, key: String): File =
        File(cacheDir(context), "$key.png")

    fun isCached(context: Context, key: String): Boolean =
        cacheFile(context, key).exists()

    fun getBitmap(context: Context, key: String): Bitmap? {
        // Check memory first
        memoryCache[key]?.let { if (!it.isRecycled) return it }
        // Check disk
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
            if (bmp != null) memoryCache[key] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Save [cropped] to disk cache under [key]. Returns the file.
     * Evicts oldest if over limit.
     */
    fun putBitmap(context: Context, key: String, cropped: Bitmap): File {
        val file = cacheFile(context, key)
        try {
            cacheDir(context).mkdirs()
            file.outputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            memoryCache[key] = cropped
            // Evict oldest if over limit (simple file date)
            val dir = cacheDir(context)
            val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (files.size > MAX_DISK_FILES) {
                for (i in 0 until files.size - MAX_DISK_FILES) {
                    try { files[i].delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return file
    }

    /**
     * Get cached cropped bitmap if present, otherwise crop [original] via
     * [BoundingBoxCropper], cache and return the result.
     * If the image is already tightly cropped, the original is cached as-is
     * (still counts as cached to avoid re-running detection).
     */
    fun getOrCreate(
        context: Context,
        original: Bitmap,
        key: String,
    ): Bitmap {
        getBitmap(context, key)?.let { return it }
        val cropped = try {
            BoundingBoxCropper.crop(original)
        } catch (_: Exception) {
            original
        }
        // Cache the result (even if same as original — marks as processed)
        // Note: we cache the cropped instance, not original, to fulfill "cropped result" caching
        val toCache = if (cropped !== original) cropped else original
        try {
            putBitmap(context, key, toCache)
            // Also keep in memory under same key
            memoryCache[key] = toCache
        } catch (_: Exception) {}
        return toCache
    }

    /**
     * Convenience for file-backed images: if original file at [originalPath]
     * exists, decode, crop, cache, and return cropped bitmap.
     */
    fun getOrCreateForFile(
        context: Context,
        originalPath: String,
        key: String,
    ): Bitmap? {
        // Check memory/disk first
        getBitmap(context, key)?.let { return it }
        val original = try {
            BitmapFactory.decodeFile(originalPath) ?: return null
        } catch (_: Exception) {
            return null
        }
        return getOrCreate(context, original, key)
    }

    fun clear(context: Context) {
        try {
            cacheDir(context).listFiles()?.forEach { try { it.delete() } catch (_: Exception) {} }
            memoryCache.values.forEach { try { if (!it.isRecycled) it.recycle() } catch (_: Exception) {} }
            memoryCache.clear()
        } catch (_: Exception) {}
    }
}
