package com.makemission.folio.data.epub

import android.content.Context
import com.makemission.folio.data.logging.FolioLogger
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
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

            if (chapters.isEmpty()) {
                FolioLogger.w("EpubParser", "Parse produced no chapters for stream")
                return null
            }
            FolioLogger.i("EpubParser", "Parsed book title='${title.take(60)}' chapters=${chapters.size}")
            EpubBook(title = title, author = author, chapters = chapters)
        } catch (e: Exception) {
            FolioLogger.w("EpubParser", "Parse failed: ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            FolioLogger.w("EpubParser", "Parse OOM: ${e.message}", e)
            System.gc()
            null
        } catch (e: Throwable) {
            FolioLogger.w("EpubParser", "Parse failed throwable: ${e.message}", e)
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

    /** Parse from a [File] on private storage. */
    fun parse(file: File): EpubBook? = try {
        file.inputStream().use { parse(it) }
    } catch (e: Exception) {
        FolioLogger.w("EpubParser", "Parse file failed: ${file.name.take(80)} ${e.message}", e)
        null
    } catch (e: OutOfMemoryError) {
        FolioLogger.w("EpubParser", "Parse file OOM: ${file.name.take(80)} ${e.message}", e)
        System.gc()
        null
    } catch (e: Throwable) {
        FolioLogger.w("EpubParser", "Parse file throwable: ${file.name.take(80)} ${e.message}", e)
        null
    }

    /** Quick metadata-only extraction (title/author) without parsing chapters — for minimal import. */
    data class EpubMetadata(val title: String, val author: String)
    fun extractMetadata(file: File): EpubMetadata? = try {
        file.inputStream().use { extractMetadata(it) }
    } catch (e: Exception) {
        FolioLogger.w("EpubParser", "extractMetadata file failed: ${file.name.take(80)} ${e.message}", e)
        null
    } catch (e: OutOfMemoryError) {
        FolioLogger.w("EpubParser", "extractMetadata file OOM: ${file.name.take(80)} ${e.message}", e)
        null
    } catch (e: Throwable) {
        FolioLogger.w("EpubParser", "extractMetadata file throwable: ${file.name.take(80)} ${e.message}", e)
        null
    }
    fun extractMetadata(inputStream: InputStream): EpubMetadata? {
        return try {
            val entries = readZipEntries(inputStream)
            if (entries.isEmpty()) return null
            val opfPath = findOpfPath(entries) ?: return EpubMetadata("Untitled", "")
            val opfBytes = entries[opfPath] ?: return EpubMetadata("Untitled", "")
            val opfText = opfBytes.toString(StandardCharsets.UTF_8)
            val opfDoc = Jsoup.parse(opfText, Parser.xmlParser())
            val title = opfDoc.selectFirst("dc|title, title")?.text()?.trim().orEmpty().ifEmpty { "Untitled" }
            val author = opfDoc.selectFirst("dc|creator, creator")?.text()?.trim().orEmpty()
            EpubMetadata(title = title, author = author)
        } catch (e: Exception) {
            FolioLogger.w("EpubParser", "extractMetadata failed: ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            FolioLogger.w("EpubParser", "extractMetadata OOM: ${e.message}", e)
            null
        } catch (e: Throwable) {
            FolioLogger.w("EpubParser", "extractMetadata throwable: ${e.message}", e)
            null
        }
    }

    /**
     * Extract cover image if present: looks for `<meta name="cover" content="id">`
     * or `properties="cover-image"` in the OPF manifest, saves the bytes to
     * `covers/<bookId>.jpg` and returns the absolute path. Returns null if
     * no cover is found — the UI will fall back to a palette color.
     */
    fun extractCoverToFile(
        inputStream: InputStream,
        context: Context,
        bookId: String,
    ): String? {
        return try {
            // Need a fresh copy of entries — read once for cover extraction.
            val entries = readZipEntries(inputStream)
            if (entries.isEmpty()) return null
            val opfPath = findOpfPath(entries) ?: return null
            val opfBytes = entries[opfPath] ?: return null
            val opfText = opfBytes.toString(StandardCharsets.UTF_8)
            val opfDoc = Jsoup.parse(opfText, Parser.xmlParser())

            // 1) meta -> manifest id
            var coverId: String? = opfDoc.selectFirst("meta[name=cover]")?.attr("content")?.trim()
            // 2) fallback: manifest item with properties="cover-image"
            if (coverId.isNullOrEmpty()) {
                coverId = opfDoc.selectFirst("item[properties~=cover-image]")?.attr("id")?.trim()
            }
            // 3) fallback: id containing "cover"
            if (coverId.isNullOrEmpty()) {
                coverId = opfDoc.select("manifest > item").firstOrNull { el ->
                    el.attr("id").contains("cover", ignoreCase = true)
                }?.attr("id")
            }
            if (coverId.isNullOrEmpty()) return null

            val href = opfDoc.selectFirst("item[id=$coverId]")?.attr("href")
                ?: opfDoc.select("manifest > item[id=$coverId]").firstOrNull()?.attr("href")
                ?: return null
            val decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8.name())
            val opfDir = opfPath.substringBeforeLast('/', "")
            val resolved = resolveHref(opfDir, decodedHref).substringAfterLast('/').lowercase()
            val coverEntry = entries.entries.firstOrNull { (k, _) ->
                k.substringAfterLast('/').lowercase() == resolved
            } ?: return null

            val bytes = coverEntry.value
            // Basic check — must look like an image (JPG/PNG/WEBP header)
            if (bytes.size < 100) return null
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val ext = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
                bytes.size >= 8 && bytes[0] == 0x89.toByte() -> "png"
                else -> "jpg"
            }
            val outFile = File(coversDir, "$bookId.$ext")
            outFile.writeBytes(bytes)
            FolioLogger.i("EpubParser", "Cover extracted for $bookId (${bytes.size} bytes)")
            outFile.absolutePath
        } catch (e: Exception) {
            FolioLogger.w("EpubParser", "Cover extract failed for $bookId: ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            FolioLogger.w("EpubParser", "Cover extract OOM for $bookId: ${e.message}", e)
            null
        } catch (e: Throwable) {
            FolioLogger.w("EpubParser", "Cover extract throwable for $bookId: ${e.message}", e)
            null
        }
    }

    fun extractCoverToFile(file: File, context: Context, bookId: String): String? {
        return try {
            file.inputStream().use { extractCoverToFile(it, context, bookId) }
        } catch (e: Exception) {
            FolioLogger.w("EpubParser", "Cover extract (file) failed for $bookId: ${e.message}", e)
            null
        } catch (e: OutOfMemoryError) {
            FolioLogger.w("EpubParser", "Cover extract (file) OOM for $bookId: ${e.message}", e)
            null
        } catch (e: Throwable) {
            FolioLogger.w("EpubParser", "Cover extract (file) throwable for $bookId: ${e.message}", e)
            null
        }
    }

    // ---- Fallback content (no file) ----

    // Single built-in sample — "How to use Folio" guide (replaces 8 placeholder titles)
    fun sampleFallbackChapters(bookTitle: String): List<EpubChapter> = listOf(
        EpubChapter(
            title = "Welcome to Folio",
            paragraphs = listOf(
                "Folio is a quiet, offline reader that feels like a well-made book. Everything lives on your device — your books, highlights, bookmarks, and vocabulary — with no cloud, no account, and no network. This short guide is your built-in sample: it shows how Folio works while you read it.",
                "On phones Folio is a single, immersive column; on tablets in landscape it opens into a two-page spread with a book-spine gutter. Your place is remembered by paragraph, not just chapter, so reopening lands exactly where you left off.",
            ),
        ),
        EpubChapter(
            title = "Your Library",
            paragraphs = listOf(
                "The Library is an editorial grid of covers (2:3, 16dp rounded, memoized 440×660 with Coil). Pull down to reveal search — Folio looks only inside your own books, ranking highlights and bookmarks first, then titles and text. Your search never leaves the device.",
                "Books find themselves: Folio can auto-scan Downloads, Documents, or a folder you choose via Storage Access Framework (SAF) — with hash and path dedup so nothing duplicates. You can also tap a book’s cover to open it, long-press for Remove / Info / Reset progress, or add one manually via Settings → Library → Add book manually (a quiet fallback, not a big button). If you open an EPUB from Files, email, or browser, Folio appears as a handler and imports with the same pipeline.",
                "Swipe right anywhere on the grid to open Settings, swipe left to see Insights — your reading journal. Need more? The quick row shows Vocabulary due and an Insights teaser without turning the Library into a dashboard.",
            ),
        ),
        EpubChapter(
            title = "Reading, Your Way",
            paragraphs = listOf(
                "Open any book to find a clean serif body (17/27) and heavy sans titles, squared-off paragraphs (Knuth-Plass micro-kerning, no orphans or widows), and true page numbers (Page X of Y) measured for your screen and cached per configuration.",
                "At the top you’ll see only Back and a menu (☰). Tap the text to hide chrome for distraction-free reading; a thin amber progress lets you seek. If you prefer, keep the bar always visible via Settings → Reading → Always show progress bar. Volume keys turn pages, even one-handed.",
                "Two navigation styles live under Settings → Reading and via in-reader AnimatedContent: Continuous (whole-book vertical scroll) and Chapter swipe (vertical within a chapter, horizontal swipe between chapters; tablet shows spreads). Folio remembers your choice.",
                "Open the menu (☰) to jump quickly: Chapters lists every chapter for instant jumps; Bookmarks and Highlights list what you’ve saved (tap to jump); Search in book looks within this book only; People & Topics (X-Ray) maps characters and ideas; Guided Reading (Bionic) bolds the first syllable to guide scanning; Comfort Contrast (Adaptive 7:1) eases colors with ambient light. All toggles in the menu are the same ones in Settings → Reading, and they persist across restarts.",
            ),
        ),
        EpubChapter(
            title = "Make It Yours",
            paragraphs = listOf(
                "With an Apple Pencil or stylus, just draw — no toolbar. Folio inks with true Multiply in warm amber so text stays crisp, varying width with pressure and tilt. Close a loop to lasso: over an image it crops the diagram to full width (bounding-box detection, cached); over text it copies the words (on-device OCR) — both private, offline.",
                "Bookmarks are not highlights: a bookmark just tucks your place (chapter + paragraph) with a 120-char preview, shown in a bottom sheet. You can keep many per book — distinct chapter/paragraph positions coexist; toggling the same spot removes only that exact bookmark (now enforced with a unique index on (bookId, chapterIndex, paragraphIndex)). Highlights, by contrast, store ink strokes plus an 80-char anchor and are reattached with LCS if a file is updated, so they don’t get lost.",
                "Double-tap any word for a definition from the 12k offline WordNet guide (113 KB gz). Select a phrase and tap Explain for the same. Each new word can be saved for spaced repetition: Folio uses SM-2 on device, bringing words back just before you’d forget. Review when the Library badge says ‘N due.’",
                "Everything you mark — highlights, bookmarks, vocabulary — feeds Insights, a quiet ledger (Shelf, Marginalia, Lexicon, Rhythm) that simply counts what already exists, with no extra tracking. No charts, just thoughtful numbers.",
            ),
        ),
        EpubChapter(
            title = "Smart, Private, Calm",
            paragraphs = listOf(
                "Folio’s help stays on device: X-Ray (TF-IDF per chapter, progressive and prioritized for the chapter you’re on), True Pages, line breaking, LCS re-anchoring, contrast (WCAG 7:1 via AdaptiveContrastEngine + AmbientLightSensor, throttled), evening warmth (TimeTintEngine, system clock, gradual), bounding-box diagrams, search, dictionary, and velocity-based time-left (Rolling-Weight EMA) — all without a network. Heavy work (full parse + X-Ray) runs in WorkManager chapter-by-chapter (semaphore 2 for 50+ imports) with atomic cache writes, so it never blocks your reading and doesn’t crash while you search.",
                "Polish is editorial, not flashy: haptics tick only at chapter boundaries, scroll fades feather the top (36dp) and bottom (40dp) with a 3-stop gradient so text never hard-cuts (now reused for Library, Settings, Insights, Vocabulary), micro-animations stagger the grid and sheets, and navigation slides and Crossfades (360ms) feel premium. Dark palettes (Folio Green, OLED True Black, Warm Sepia, Cool Slate — Slate default) plus Light/Dark/Auto and evening warmth keep the page comfortable anywhere.",
                "Try it: swipe, search, highlight, bookmark, open Chapters or Highlights from the menu, and pick a dark palette in Settings → Appearance. When you’re ready for your own books, use Auto-scan or Add book manually. For help, see Settings → Smart Features for a plain-English guide to the twelve quiet helpers. Happy reading — \"$bookTitle\" is just the start.",
            ),
        ),
    )

    /** Try assets/sample.epub, else return null so caller uses fallback. */
    fun loadFromAssetsOrNull(context: Context): EpubBook? = try {
        context.assets.open("sample.epub").use { stream -> parse(stream) }
    } catch (e: Exception) {
        FolioLogger.w("EpubParser", "loadFromAssetsOrNull failed: ${e.message}", e)
        null
    } catch (e: OutOfMemoryError) {
        FolioLogger.w("EpubParser", "loadFromAssetsOrNull OOM: ${e.message}", e)
        null
    } catch (e: Throwable) {
        FolioLogger.w("EpubParser", "loadFromAssetsOrNull throwable: ${e.message}", e)
        null
    }
}
