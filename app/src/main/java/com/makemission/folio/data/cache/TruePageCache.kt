package com.makemission.folio.data.cache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk cache for True-Page calculations — extends XRayCache pattern.
 *
 * TruePage depends on screen dimensions, font scale, bionic, and book content hash,
 * so the cache key includes those plus the book's fileHash (for invalidation only
 * if file actually changes, reusing hash dedup). Pure on-device.
 */
object TruePageCache {

    private fun cacheFile(context: Context, bookId: String, configKey: String, fileHash: String?): File {
        val dir = File(context.filesDir, "truepage").apply { mkdirs() }
        val safeId = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        // Include fileHash fragment to auto-invalidate on change (or store inside)
        val hashPart = fileHash?.take(8) ?: "nohash"
        return File(dir, "${safeId}_${configKey}_${hashPart}.json")
    }

    data class CachedInfo(
        val totalPages: Int,
        val prefixSums: IntArray,
        val perScreenHeightPx: Int,
    )

    fun save(
        context: Context,
        bookId: String,
        configKey: String,
        fileHash: String?,
        totalPages: Int,
        prefixSums: IntArray,
        perScreenHeightPx: Int,
    ) {
        try {
            val obj = JSONObject()
            fileHash?.let { obj.put("fileHash", it) }
            obj.put("configKey", configKey)
            obj.put("totalPages", totalPages)
            obj.put("perScreenHeightPx", perScreenHeightPx)
            val arr = JSONArray()
            for (v in prefixSums) arr.put(v)
            obj.put("prefixSums", arr)
            cacheFile(context, bookId, configKey, fileHash).writeText(obj.toString())
            // Evict old configs for same book (keep at most 3)
            evictOld(context, bookId)
        } catch (_: Exception) {}
    }

    fun load(
        context: Context,
        bookId: String,
        configKey: String,
        currentFileHash: String?,
    ): CachedInfo? {
        return try {
            val f = cacheFile(context, bookId, configKey, currentFileHash)
            if (!f.exists()) {
                // Try without hash fragment (legacy)
                val dir = File(context.filesDir, "truepage")
                val candidates = dir.listFiles()?.filter { it.name.startsWith(bookId.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_" + configKey) } ?: emptyList()
                val found = candidates.firstOrNull { it.exists() } ?: return null
                return parseFile(found, currentFileHash)
            }
            parseFile(f, currentFileHash)
        } catch (_: Exception) { null }
    }

    private fun parseFile(f: File, currentFileHash: String?): CachedInfo? {
        return try {
            val text = f.readText()
            if (text.isBlank()) return null
            val obj = JSONObject(text)
            val cachedHash = if (obj.has("fileHash")) if (obj.has("fileHash")) obj.optString("fileHash") else null else null
            if (currentFileHash != null && cachedHash != null && cachedHash != currentFileHash) {
                try { f.delete() } catch (_: Exception) {}
                return null
            }
            val totalPages = obj.optInt("totalPages", 1)
            val perScreen = obj.optInt("perScreenHeightPx", 0)
            val arr = obj.optJSONArray("prefixSums") ?: return null
            val prefix = IntArray(arr.length()) { arr.getInt(it) }
            CachedInfo(totalPages = totalPages, prefixSums = prefix, perScreenHeightPx = perScreen)
        } catch (_: Exception) { null }
    }

    private fun evictOld(context: Context, bookId: String) {
        try {
            val dir = File(context.filesDir, "truepage")
            val safeId = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val files = dir.listFiles()?.filter { it.name.startsWith(safeId + "_") }?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > 4) {
                for (i in 4 until files.size) try { files[i].delete() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun invalidate(context: Context, bookId: String) {
        try {
            val dir = File(context.filesDir, "truepage")
            val safeId = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            dir.listFiles()?.filter { it.name.startsWith(safeId) }?.forEach { try { it.delete() } catch (_: Exception) {} }
        } catch (_: Exception) {}
    }

    fun configKey(
        screenWidthDp: Int,
        screenHeightDp: Int,
        orientation: Int,
        fontScale: Float,
        density: Float,
        bionicEnabled: Boolean,
        isTablet: Boolean,
        chaptersHash: Int,
    ): String {
        return "${screenWidthDp}x${screenHeightDp}_o${orientation}_fs${fontScale}_d${density}_b${bionicEnabled}_t${isTablet}_ch${chaptersHash}".replace(".", "_")
    }
}
