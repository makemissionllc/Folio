package com.makemission.folio.data.epub

import java.io.File

/** Routes imported books to the matching local reader. */
object BookFileParser {
    /** MIME hints passed to the system document picker; extension validation still happens after selection. */
    val pickerMimeTypes: Array<String>
        get() = arrayOf(
            "application/epub+zip",
            "application/x-mobipocket-ebook",
            "application/x-fictionbook+xml",
            "text/plain",
            "application/octet-stream",
            "*/*",
        )

    fun isSupportedFileName(name: String): Boolean = when (name.substringAfterLast('.', "").lowercase()) {
        "epub", "mobi", "fb2", "txt" -> true
        else -> false
    }

    /** Plain text is intentionally excluded from automatic storage scans to avoid importing notes/documents. */
    fun isDeviceScanFileName(name: String): Boolean = when (name.substringAfterLast('.', "").lowercase()) {
        "epub", "mobi", "fb2" -> true
        else -> false
    }

    fun metadata(file: File): EpubParser.EpubMetadata? = when (file.extension.lowercase()) {
        "mobi" -> MobiParser.metadata(file)
        "epub" -> EpubParser.extractMetadata(file)
        "fb2" -> Fb2Parser.metadata(file)
        "txt" -> TextBookParser.metadata(file)
        else -> null
    }

    fun parse(file: File): EpubParser.EpubBook? = when (file.extension.lowercase()) {
        "mobi" -> MobiParser.parse(file)
        "epub" -> EpubParser.parse(file)
        "fb2" -> Fb2Parser.parse(file)
        "txt" -> TextBookParser.parse(file)
        else -> null
    }
}
