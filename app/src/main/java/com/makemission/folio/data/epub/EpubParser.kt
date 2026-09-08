package com.makemission.folio.data.epub

import android.content.Context
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Minimal native EPUB3 parser — no network, no AI.
 *
 * Mirrors the approach in the reference app's `EpubTextParser` (ZIP + OPF
 * spine + Jsoup) but as a self-contained, Folio-specific implementation.
 * Falls back to [sampleFallbackChapters] when no file is present so the
 * Reading screen is usable without imported books.
 *
 * @see <a href="https://www.w3.org/publishing/epub3/epub-packages.html">EPUB Packages 3</a>
 */
object EpubParser {

    data class EpubChapter(
        val title: String,
        val paragraphs: List<String>,
    )

    data class EpubBook(
        val title: String,
        val author: String,
        val chapters: List<EpubChapter>,
    )

    /**
     * Parse an EPUB from [inputStream]. Returns null if the stream is not a
     * valid EPUB; callers should then use [sampleFallbackChapters].
     */
    fun parse(inputStream: InputStream): EpubBook? {
        return try {
            val entries = readZipEntries(inputStream)
            if (entries.isEmpty()) return null

            val opfPath = findOpfPath(entries)
            if (opfPath == null) {
                // No OPF — treat every html/xhtml entry as a chapter (graceful degrade).
                val filtered = entries.entries.filter { (name, _) -> isChapterName(name) }
                val sorted = filtered.sortedBy { (name, _) -> name }
                val fallback = sorted.mapNotNull { (name, bytes) -> parseChapterBytes(name, bytes) }
                if (fallback.isEmpty()) return null
                return EpubBook(
                    title = "Untitled EPUB",
                    author = "",
                    chapters = fallback,
                )
            }

            val opfBytes = entries[opfPath] ?: return null
            val opfText = opfBytes.toString(StandardCharsets.UTF_8)
            val opfDoc = Jsoup.parse(opfText, Parser.xmlParser())
            val opfDir = opfPath.substringBeforeLast('/', "")

            // Title / creator from OPF metadata
            val title = opfDoc.selectFirst("dc|title, title")?.text()?.trim().orEmpty()
                .ifEmpty { "Untitled" }
            val author = opfDoc.selectFirst("dc|creator, creator")?.text()?.trim().orEmpty()

            // Manifest: id -> href
            val manifest = opfDoc.select("manifest > item").associate { el ->
                el.attr("id") to URLDecoder.decode(el.attr("href"), StandardCharsets.UTF_8.name())
            }

            // Spine order
            val spineIds = opfDoc.select("spine > itemref").map { it.attr("idref") }

            // Optional toc.ncx titles — map file name -> chapter title
            val tocTitles = buildTocTitleMap(entries)

            val chapters = spineIds.mapNotNull { id ->
                val href = manifest[id] ?: return@mapNotNull null
                val resolved = resolveHref(opfDir, href).substringAfterLast('/')
                    .lowercase()
                // Zip entries may be under different dirs — match by file name.
                val entry = entries.entries.firstOrNull { (k, _) ->
                    k.substringAfterLast('/').lowercase() == resolved
                } ?: return@mapNotNull null
                val chapterTitle = tocTitles[resolved]
                parseChapterBytes(entry.key, entry.value, preferredTitle = chapterTitle)
            }

            if (chapters.isEmpty()) return null
            EpubBook(title = title, author = author, chapters = chapters)
        } catch (_: Exception) {
            null
        }
    }

    // ---- ZIP helpers ----

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    map[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        // META-INF/container.xml points at the OPF
        val containerBytes = entries["META-INF/container.xml"]
        if (containerBytes != null) {
            val doc = Jsoup.parse(containerBytes.toString(StandardCharsets.UTF_8), Parser.xmlParser())
            val fullPath = doc.selectFirst("rootfile")?.attr("full-path")?.trim()
            if (!fullPath.isNullOrEmpty()) return fullPath
        }
        // Fallback: first .opf entry
        return entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun buildTocTitleMap(entries: Map<String, ByteArray>): Map<String, String> {
        val ncxBytes = entries.entries.firstOrNull { (k, _) ->
            k.endsWith(".ncx", ignoreCase = true)
        }?.value ?: return emptyMap()
        val doc = Jsoup.parse(ncxBytes.toString(StandardCharsets.UTF_8), Parser.xmlParser())
        val map = mutableMapOf<String, String>()
        doc.select("navPoint").forEach { navPoint ->
            val title = navPoint.selectFirst("navLabel > text")?.text()?.trim()
                ?: return@forEach
            val src = navPoint.selectFirst("content")?.attr("src")?.trim()
                ?: return@forEach
            val decoded = URLDecoder.decode(src, StandardCharsets.UTF_8.name())
            val file = decoded.substringAfterLast('/').substringBefore('#').lowercase()
            if (file.isNotEmpty() && title.isNotBlank()) map[file] = title
        }
        return map
    }

    private fun resolveHref(opfDir: String, href: String): String {
        val decoded = URLDecoder.decode(href, StandardCharsets.UTF_8.name())
        return if (opfDir.isEmpty() || decoded.contains('/')) decoded
        else "$opfDir/$decoded"
    }

    private fun isChapterName(name: String): Boolean =
        name.endsWith(".html", true) || name.endsWith(".htm", true) ||
            name.endsWith(".xhtml", true)

    private fun parseChapterBytes(
        entryName: String,
        bytes: ByteArray,
        preferredTitle: String? = null,
    ): EpubChapter? {
        val html = bytes.toString(StandardCharsets.UTF_8)
        val doc = Jsoup.parse(html, Parser.htmlParser())
        // Strip scripts/styles
        doc.select("script, style, nav, header, footer").remove()

        // Paragraph-ish blocks
        val blocks = doc.select("p, h1, h2, h3, h4, h5, h6, li, blockquote")
            .mapNotNull { el ->
                val t = el.text().trim()
                if (t.isEmpty()) null else t
            }

        if (blocks.isEmpty()) return null

        val title = preferredTitle?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1, h2, h3")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: entryName.substringAfterLast('/').substringBefore('.')

        // First block duplicates the title — drop it.
        val paragraphs = if (blocks.firstOrNull()?.equals(title, ignoreCase = true) == true) {
            blocks.drop(1)
        } else blocks

        if (paragraphs.isEmpty()) return null
        return EpubChapter(title = title, paragraphs = paragraphs)
    }

    // ---- Fallback content (no file) ----

    fun sampleFallbackChapters(bookTitle: String): List<EpubChapter> = listOf(
        EpubChapter(
            title = "Chapter 1 — Arrival",
            paragraphs = listOf(
                "The morning light fell across the Folio library in a warm, paper-coloured wash. " +
                    "On the far wall the deep-green shelves held a careful, editorial grid — each " +
                    "spine a quiet promise. The air smelled faintly of ink and cedar.",
                "Mara set her satchel down and ran a finger along the nearest row. She was looking " +
                    "for a single volume, a slim quarto bound in burgundy cloth. The catalogue said it " +
                    "had arrived yesterday, but the library — like all good libraries — preferred to " +
                    "make its guests look a little.",
                "Outside, the city was already awake. Trams hummed and a delivery van reversed with " +
                    "a soft, insistent beep. Inside, the room kept its own time, measured in page " +
                    "turns and the amber tick of the reading lamp.",
                "She found the book on the second shelf, exactly where she had not thought to look. " +
                    "Its title was stamped in a heavy sans that caught the light. She lifted it and " +
                    "felt the familiar, satisfying weight of paper.",
            ),
        ),
        EpubChapter(
            title = "Chapter 2 — The Spread",
            paragraphs = listOf(
                "On a tablet the book opened into a spread — two pages facing one another like an " +
                    "open codex. Mara turned it to landscape and the text reflowed, columns squaring " +
                    "themselves with the care of a pressman. No widows, no orphans; the margins breathed.",
                "The serif ran clean and tall, generous in its leading. On a phone the same chapter " +
                    "fell into a single, immersive column, edge-to-edge, the Folio ground a deep " +
                    "green that held the eye without glare. The amber rule at the top marked her place, " +
                    "a thin, warm line.",
                "She read on. Each paragraph followed the last with the patience of good typesetting — " +
                    "micro-kerning eased and tightened by invisible hands, so the block sat square and " +
                    "still. It was the kind of detail most readers never notice, which is precisely " +
                    "why it matters.",
                "Somewhere, deep in the app's quiet storage, the last line she had read was being " +
                    "remembered — chapter, paragraph, the small, faithful coordinates of a bookmark " +
                    "she would never have to set herself.",
                "For \"$bookTitle,\" at least, the reading had begun.",
            ),
        ),
        EpubChapter(
            title = "Chapter 3 — Marginal Note",
            paragraphs = listOf(
                "Every good book eventually collects marginalia. Mara had once kept a notebook for the " +
                    "words she looked up — strange, lovely words that arrived like strangers and stayed " +
                    "like friends. The notebook was lost now, but the habit remained.",
                "Folio, she had been told, kept its own, quieter ledger: where she paused, what she " +
                    "returned to, how long a chapter held her. Not for a cloud, not for a model — for " +
                    "the paper itself. The progress lived in a small local store, a Room of its own.",
                "The system was, by design, boring. A table, a primary key, a timestamp. No network " +
                    "call would ever carry the shape of her reading elsewhere. It was the most editorial " +
                    "choice of all: privacy as craft.",
                "She closed the volume, but not quite. A ribbon — amber, of course — held the place. " +
                    "Tomorrow there would be more pages, more light, more of the patient, two-column " +
                    "spread when she tilted the tablet. For now, the shelf waited.",
            ),
        ),
    )

    /** Try assets/sample.epub, else return null so caller uses fallback. */
    fun loadFromAssetsOrNull(context: Context): EpubBook? = try {
        context.assets.open("sample.epub").use { stream -> parse(stream) }
    } catch (_: Exception) {
        null
    }
}
