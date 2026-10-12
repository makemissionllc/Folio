package com.makemission.folio.data.epub

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Reads unencrypted PalmDOC/MOBI6 books (raw or PalmDOC-compressed text records). */
object MobiParser {
    private const val MAX_TEXT_BYTES = 32 * 1024 * 1024

    fun metadata(file: File): EpubParser.EpubMetadata? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = readHeader(raf) ?: return null
            EpubParser.EpubMetadata(header.title, "")
        }
    }.getOrNull()

    fun parse(file: File): EpubParser.EpubBook? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = readHeader(raf) ?: return null
            if (header.compression != 1 && header.compression != 2) return null
            if (header.encryption != 0 || header.textLength > MAX_TEXT_BYTES) return null
            val output = ByteArrayOutputStream(header.textLength.coerceAtMost(MAX_TEXT_BYTES))
            for (index in 1..header.textRecords) {
                val start = header.offsets[index]
                val end = if (index + 1 < header.offsets.size) header.offsets[index + 1] else raf.length()
                if (start < 0 || end < start || end > raf.length() || end - start > 65536) return null
                val bytes = ByteArray((end - start).toInt())
                raf.seek(start)
                raf.readFully(bytes)
                val decoded = if (header.compression == 2) decompressPalmDoc(bytes) ?: return null else bytes
                if (output.size() + decoded.size > MAX_TEXT_BYTES) return null
                output.write(decoded)
            }
            val text = output.toByteArray().copyOf(header.textLength.coerceAtMost(output.size()))
                .toString(header.charset)
            val doc = Jsoup.parse(text)
            doc.select("script, style, nav").remove()
            val paragraphs = doc.select("p, h1, h2, h3, h4, li, blockquote")
                .map { it.text().trim() }.filter { it.length >= 3 }
                .ifEmpty { doc.body().wholeText().split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.length >= 3 } }
            if (paragraphs.isEmpty()) return null
            EpubParser.EpubBook(
                title = header.title,
                author = "",
                chapters = listOf(EpubParser.EpubChapter(header.title, paragraphs)),
            )
        }
    }.getOrNull()

    private data class Header(
        val title: String,
        val compression: Int,
        val encryption: Int,
        val textLength: Int,
        val textRecords: Int,
        val offsets: LongArray,
        val charset: Charset,
    )

    private fun readHeader(raf: RandomAccessFile): Header? {
        if (raf.length() < 100) return null
        val pdbName = ByteArray(32).also { raf.readFully(it) }
            .toString(StandardCharsets.ISO_8859_1).substringBefore('\u0000').trim()
        raf.seek(60)
        val type = ByteArray(4).also { raf.readFully(it) }.toString(StandardCharsets.US_ASCII)
        val creator = ByteArray(4).also { raf.readFully(it) }.toString(StandardCharsets.US_ASCII)
        if (type != "BOOK" || creator != "MOBI") return null
        raf.seek(76)
        val records = raf.readUnsignedShort()
        if (records < 2 || records > 10000 || 78L + records * 8L > raf.length()) return null
        val offsets = LongArray(records)
        for (i in 0 until records) {
            raf.seek(78L + i * 8L)
            offsets[i] = raf.readInt().toLong() and 0xffffffffL
        }
        val first = offsets[0]
        if (first < 78 + records * 8 || first + 100 > raf.length()) return null
        raf.seek(first)
        val compression = raf.readUnsignedShort()
        raf.skipBytes(2)
        val textLengthLong = raf.readInt().toLong() and 0xffffffffL
        if (textLengthLong > MAX_TEXT_BYTES) return null
        val textRecords = raf.readUnsignedShort()
        raf.skipBytes(2)
        val encryption = raf.readUnsignedShort()
        if (textRecords < 1 || textRecords >= records) return null
        raf.seek(first + 16)
        val magic = ByteArray(4).also { raf.readFully(it) }.toString(StandardCharsets.US_ASCII)
        if (magic != "MOBI") return null
        val mobiLength = raf.readInt()
        if (mobiLength < 92 || first + 16L + mobiLength > raf.length()) return null
        raf.seek(first + 16 + 28L)
        val encoding = raf.readInt()
        val charset = when (encoding) {
            65001 -> StandardCharsets.UTF_8
            1252 -> Charset.forName("windows-1252")
            else -> StandardCharsets.ISO_8859_1
        }
        raf.seek(first + 16 + 84L)
        val titleOffset = raf.readInt().toLong() and 0xffffffffL
        val titleLength = raf.readInt()
        val title = if (titleLength in 1..1024 && first + titleOffset + titleLength <= raf.length()) {
            raf.seek(first + titleOffset)
            ByteArray(titleLength).also { raf.readFully(it) }.toString(charset).trim()
        } else pdbName
        return Header(title.ifBlank { pdbName.ifBlank { "Untitled MOBI" } }, compression,
            encryption, textLengthLong.toInt(), textRecords, offsets, charset)
    }

    private fun decompressPalmDoc(input: ByteArray): ByteArray? {
        var output = ByteArray((input.size * 2).coerceAtLeast(1024))
        var written = 0
        fun append(value: Int) {
            if (written >= MAX_TEXT_BYTES) throw IllegalArgumentException("MOBI text exceeds size limit")
            if (written == output.size) output = output.copyOf((output.size * 2).coerceAtMost(MAX_TEXT_BYTES))
            output[written++] = value.toByte()
        }
        var i = 0
        while (i < input.size) {
            val byte = input[i++].toInt() and 0xff
            when {
                byte in 1..8 -> {
                    if (i + byte > input.size) return null
                    repeat(byte) { append(input[i++].toInt()) }
                }
                byte <= 0x7f -> append(byte)
                byte <= 0xbf -> {
                    if (i >= input.size) return null
                    val pair = (byte shl 8) or (input[i++].toInt() and 0xff)
                    val distance = (pair shr 3) and 0x7ff
                    val length = (pair and 7) + 3
                    if (distance == 0 || distance > written) return null
                    repeat(length) { append(output[written - distance].toInt()) }
                }
                else -> {
                    append(' '.code)
                    append(byte xor 0x80)
                }
            }
            if (written > MAX_TEXT_BYTES) return null
        }
        return output.copyOf(written)
    }
}
