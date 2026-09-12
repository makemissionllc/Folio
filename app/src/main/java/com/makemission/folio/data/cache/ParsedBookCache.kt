package com.makemission.folio.data.cache

import android.content.Context
import com.makemission.folio.data.epub.EpubParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk cache for fully parsed EPUB chapters — avoids re-parsing the ZIP/Jsoup on every open.
 *
 * Extends the existing XRayCache pattern (file-based JSON) to parsed text.
 * Stores the book's chapters + metadata alongside the file's SHA-256 hash so
 * the cache is invalidated only if the underlying file actually changes
 * (reuses hash-based dedup logic from auto-scan). Pure on-device, no network.
 */
object ParsedBookCache {

    private fun cacheFile(context: Context, bookId: String): File {
        val dir = File(context.filesDir, "parsed_cache").apply { mkdirs() }
        val safe = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safe.json")
    }

    fun save(context: Context, bookId: String, fileHash: String?, book: EpubParser.EpubBook) {
        try {
            val obj = JSONObject()
            fileHash?.let { obj.put("fileHash", it) }
            obj.put("title", book.title)
            obj.put("author", book.author)
            val chaptersArr = JSONArray()
            for (ch in book.chapters) {
                val chObj = JSONObject()
                chObj.put("title", ch.title)
                val parasArr = JSONArray()
                for (p in ch.paragraphs) parasArr.put(p)
                chObj.put("paragraphs", parasArr)
                chaptersArr.put(chObj)
            }
            obj.put("chapters", chaptersArr)
            // Atomic write: write to tmp then rename to avoid partially-written file being read by search while many workers run concurrently.
            val cache = cacheFile(context, bookId)
            val tmp = File(cache.parentFile, "${cache.name}.tmp")
            tmp.writeText(obj.toString())
            // Best-effort atomic rename; fallback to direct write if rename fails.
            if (!tmp.renameTo(cache)) {
                try { tmp.copyTo(cache, overwrite = true) } catch (_: Exception) {}
                try { tmp.delete() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } catch (_: OutOfMemoryError) {
        }
    }

    fun load(context: Context, bookId: String, currentFileHash: String?): EpubParser.EpubBook? {
        return try {
            val f = cacheFile(context, bookId)
            if (!f.exists()) return null
            val text = try { f.readText() } catch (_: Exception) { return null } catch (_: OutOfMemoryError) { return null }
            if (text.isBlank()) return null
            val obj = try { JSONObject(text) } catch (_: Exception) { return null } catch (_: OutOfMemoryError) { return null }
            // Hash check — invalidate if file changed (reuse dedup logic)
            val cachedHash = if (obj.has("fileHash")) if (obj.has("fileHash")) obj.optString("fileHash") else null else null
            if (currentFileHash != null && cachedHash != null && cachedHash != currentFileHash) {
                // Stale — delete and miss
                try { f.delete() } catch (_: Exception) {}
                return null
            }
            // If we have a current hash but cached has none (legacy), treat as miss
            if (currentFileHash != null && cachedHash == null) return null
            val title = obj.optString("title", "")
            val author = obj.optString("author", "")
            val chaptersArr = obj.optJSONArray("chapters") ?: return null
            val chapters = mutableListOf<EpubParser.EpubChapter>()
            for (i in 0 until chaptersArr.length()) {
                val chObj = chaptersArr.getJSONObject(i)
                val chTitle = chObj.optString("title", "Chapter")
                val parasArr = chObj.optJSONArray("paragraphs") ?: JSONArray()
                val paras = mutableListOf<String>()
                for (j in 0 until parasArr.length()) paras.add(parasArr.getString(j))
                chapters.add(EpubParser.EpubChapter(title = chTitle, paragraphs = paras))
            }
            if (chapters.isEmpty()) return null
            EpubParser.EpubBook(title = title, author = author, chapters = chapters)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun invalidate(context: Context, bookId: String) {
        try { cacheFile(context, bookId).delete() } catch (_: Exception) {}
    }
}
