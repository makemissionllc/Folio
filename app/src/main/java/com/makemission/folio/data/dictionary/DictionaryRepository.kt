package com.makemission.folio.data.dictionary

import android.content.Context
import org.json.JSONObject

/**
 * Offline dictionary — compact open-source word list bundled as
 * `assets/dictionary.json`, no network. Lookups are case-insensitive
 * and fully on-device (§6 "entirely private").
 */
object DictionaryRepository {

    @Volatile
    private var cache: Map<String, String>? = null

    private fun load(context: Context): Map<String, String> {
        cache?.let { return it }
        return synchronized(this) {
            cache?.let { return it }
            val map = mutableMapOf<String, String>()
            try {
                context.assets.open("dictionary.json").bufferedReader().use { r ->
                    val text = r.readText()
                    val obj = JSONObject(text)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = obj.optString(k, "")
                        if (v.isNotBlank()) map[k.lowercase()] = v
                    }
                }
            } catch (_: Exception) {
                // Leave map empty — caller will show "No definition found"
            }
            cache = map
            map
        }
    }

    /** Returns definition for [word] (case-insensitive, punctuation stripped) or null. */
    fun lookup(word: String, context: Context): String? {
        if (word.isBlank()) return null
        val key = word.lowercase().trim().trim('.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '“', '”', '‘', '’')
        if (key.isEmpty()) return null
        val dict = load(context)
        // Direct
        dict[key]?.let { return it }
        // Try singular (strip trailing s) for plurals
        if (key.endsWith("s") && key.length > 3) {
            dict[key.dropLast(1)]?.let { return it }
        }
        return null
    }

    fun isLoaded(): Boolean = cache != null
}
