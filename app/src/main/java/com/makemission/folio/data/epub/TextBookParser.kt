package com.makemission.folio.data.epub

import java.io.File
import java.nio.charset.StandardCharsets

/** Converts plain text ebooks into readable paragraphs without scanning unrelated device text files. */
object TextBookParser {
    private const val MAX_FILE_BYTES = 32 * 1024 * 1024

    fun metadata(file: File): EpubParser.EpubMetadata? {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES.toLong()) return null
        return EpubParser.EpubMetadata(cleanTitle(file), "")
    }

    fun parse(file: File): EpubParser.EpubBook? = runCatching {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES.toLong()) return null
        val bytes = file.readBytes()
        val content = decode(bytes)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u0000', ' ')
        val paragraphs = content.split(Regex("\\n[\\t ]*\\n+"))
            .map { it.replace(Regex("[\\t ]*\\n[\\t ]*"), " ").trim() }
            .filter { it.length >= 2 }
        if (paragraphs.isEmpty()) return null
        val title = cleanTitle(file)
        EpubParser.EpubBook(title, "", listOf(EpubParser.EpubChapter("Chapter 1", paragraphs)))
    }.getOrNull()

    private fun decode(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        else -> bytes.toString(StandardCharsets.UTF_8)
    }

    private fun cleanTitle(file: File): String = file.nameWithoutExtension
        .replace(Regex("^(tmp_|scan_)?[0-9a-fA-F-]{36}_"), "")
        .ifBlank { "Untitled text" }
}
