package com.makemission.folio.data.xray

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Simple file cache for X-Ray index — computed once per book on first open,
 * then reused without recalculation. Pure on-device, no network.
 */
object XRayCache {

    private fun cacheFile(context: Context, bookId: String): File {
        val dir = File(context.filesDir, "xray").apply { mkdirs() }
        // Sanitize bookId for filename
        val safe = bookId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safe.json")
    }

    fun load(context: Context, bookId: String): Map<Int, List<XRayTerm>>? {
        return try {
            val f = cacheFile(context, bookId)
            if (!f.exists()) return null
            val text = f.readText()
            if (text.isBlank()) return null
            val obj = JSONObject(text)
            val result = mutableMapOf<Int, List<XRayTerm>>()
            for (key in obj.keys()) {
                val chIdx = key.toIntOrNull() ?: continue
                val arr = obj.getJSONArray(key)
                val terms = mutableListOf<XRayTerm>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    terms.add(
                        XRayTerm(
                            term = o.getString("term"),
                            normalized = o.getString("normalized"),
                            score = o.getDouble("score"),
                            chapterIndices = o.getJSONArray("chapterIndices").let { ja ->
                                (0 until ja.length()).map { ja.getInt(it) }
                            },
                            totalFrequency = o.getInt("totalFrequency"),
                        )
                    )
                }
                result[chIdx] = terms
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, bookId: String, index: Map<Int, List<XRayTerm>>) {
        try {
            val obj = JSONObject()
            for ((chIdx, terms) in index) {
                val arr = JSONArray()
                for (t in terms) {
                    val o = JSONObject()
                    o.put("term", t.term)
                    o.put("normalized", t.normalized)
                    o.put("score", t.score)
                    o.put("chapterIndices", JSONArray(t.chapterIndices))
                    o.put("totalFrequency", t.totalFrequency)
                    arr.put(o)
                }
                obj.put(chIdx.toString(), arr)
            }
            cacheFile(context, bookId).writeText(obj.toString())
        } catch (_: Exception) {
        }
    }

    fun invalidate(context: Context, bookId: String) {
        try { cacheFile(context, bookId).delete() } catch (_: Exception) {}
    }
}
