package com.makemission.folio.data.epub

import java.io.File

/** Routes imported books to the matching local reader. */
object BookFileParser {
    fun metadata(file: File): EpubParser.EpubMetadata? = when (file.extension.lowercase()) {
        "mobi" -> MobiParser.metadata(file)
        "epub" -> EpubParser.extractMetadata(file)
        else -> null
    }

    fun parse(file: File): EpubParser.EpubBook? = when (file.extension.lowercase()) {
        "mobi" -> MobiParser.parse(file)
        "epub" -> EpubParser.parse(file)
        else -> null
    }
}
