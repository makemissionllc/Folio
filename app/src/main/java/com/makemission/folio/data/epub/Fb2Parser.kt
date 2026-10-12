package com.makemission.folio.data.epub

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File

/** Lightweight reader for FictionBook 2 (FB2) XML files. */
object Fb2Parser {
    private const val MAX_FILE_BYTES = 32L * 1024 * 1024

    fun metadata(file: File): EpubParser.EpubMetadata? = runCatching {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val doc = Jsoup.parse(file, null, "", Parser.xmlParser())
        val title = doc.selectFirst("description title-info book-title")?.text()?.trim()
            .orEmpty().ifBlank { cleanTitle(file) }
        val author = doc.select("description title-info author").joinToString(", ") { person ->
            person.select("first-name, middle-name, last-name, nickname")
                .joinToString(" ") { it.text().trim() }
                .trim()
        }.ifBlank { "" }
        EpubParser.EpubMetadata(title, author)
    }.getOrNull()

    fun parse(file: File): EpubParser.EpubBook? = runCatching {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return null
        val doc = Jsoup.parse(file, null, "", Parser.xmlParser())
        val metadata = metadataFromDocument(doc, file)
        val body = doc.selectFirst("body:not([name=notes])") ?: doc.selectFirst("body") ?: return null
        val sections = body.children().filter { it.tagName() == "section" }
        val chapters = mutableListOf<EpubParser.EpubChapter>()

        fun addSection(section: org.jsoup.nodes.Element) {
            val heading = section.children().firstOrNull { it.tagName() == "title" }
                ?.text()?.trim().orEmpty()
            val paragraphs = mutableListOf<String>()
            section.children().forEach { child ->
                when (child.tagName()) {
                    "section", "title" -> Unit
                    "p", "subtitle", "text-author" -> child.text().trim().takeIf { it.length >= 2 }?.let(paragraphs::add)
                    "poem" -> child.select("stanza > v").forEach { line ->
                        line.text().trim().takeIf { it.isNotBlank() }?.let(paragraphs::add)
                    }
                    "epigraph", "cite" -> child.select("p, text-author").forEach { line ->
                        line.text().trim().takeIf { it.length >= 2 }?.let(paragraphs::add)
                    }
                }
            }
            if (paragraphs.isNotEmpty()) {
                val title = EpubParser.sanitizeChapterTitle(heading, chapters.size)
                chapters += EpubParser.EpubChapter(title, paragraphs)
            }
            section.children().filter { it.tagName() == "section" }.forEach(::addSection)
        }

        if (sections.isNotEmpty()) {
            sections.forEach(::addSection)
        } else {
            val paragraphs = body.select("p, subtitle, text-author, poem > stanza > v")
                .map { it.text().trim() }.filter { it.length >= 2 }
            if (paragraphs.isNotEmpty()) {
                chapters += EpubParser.EpubChapter("Chapter 1", paragraphs)
            }
        }
        if (chapters.isEmpty()) return null
        EpubParser.EpubBook(metadata.title, metadata.author, chapters)
    }.getOrNull()

    private fun metadataFromDocument(doc: org.jsoup.nodes.Document, file: File): EpubParser.EpubMetadata {
        val title = doc.selectFirst("description title-info book-title")?.text()?.trim()
            .orEmpty().ifBlank { cleanTitle(file) }
        val author = doc.select("description title-info author").joinToString(", ") { person ->
            person.select("first-name, middle-name, last-name, nickname")
                .joinToString(" ") { it.text().trim() }.trim()
        }
        return EpubParser.EpubMetadata(title, author)
    }

    private fun cleanTitle(file: File): String = file.nameWithoutExtension
        .replace(Regex("^(tmp_|scan_)?[0-9a-fA-F-]{36}_"), "")
        .ifBlank { "Untitled FB2" }
}
