package com.makemission.folio.data.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class AdditionalBookFormatsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesFb2MetadataAndNestedSectionsInReadingOrder() {
        val file = File(temporaryFolder.root, "story.fb2").apply {
            writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                      <description><title-info>
                        <genre>sf</genre><author><first-name>Ada</first-name><last-name>Lovelace</last-name></author>
                        <book-title>Analytical Stories</book-title>
                      </title-info></description>
                      <body><section><title><p>First section</p></title>
                        <p>The first passage.</p>
                        <section><title><p>Inside</p></title><p>The nested passage.</p></section>
                      </section></body>
                    </FictionBook>""".trimIndent(),
            )
        }

        val metadata = BookFileParser.metadata(file)
        val book = BookFileParser.parse(file)

        assertEquals("Analytical Stories", metadata?.title)
        assertEquals("Ada Lovelace", metadata?.author)
        assertNotNull(book)
        assertEquals(listOf("First section", "Inside"), book!!.chapters.map { it.title })
        assertEquals("The first passage.", book.chapters.first().paragraphs.first())
        assertEquals("The nested passage.", book.chapters.last().paragraphs.first())
    }

    @Test
    fun parsesUtf8PlainTextAndKeepsLineWrappedParagraphsTogether() {
        val file = File(temporaryFolder.root, "quiet-book.txt")
        val body = "A quiet book\nwith a wrapped line.\n\nA second paragraph."
        file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray(StandardCharsets.UTF_8))

        val book = BookFileParser.parse(file)

        assertEquals("quiet-book", BookFileParser.metadata(file)?.title)
        assertEquals(listOf("A quiet book with a wrapped line.", "A second paragraph."), book?.chapters?.single()?.paragraphs)
    }

    @Test
    fun plainTextCanBePickedButIsExcludedFromAutomaticDeviceScan() {
        assertTrue(BookFileParser.isSupportedFileName("book.TXT"))
        assertTrue(BookFileParser.isDeviceScanFileName("book.FB2"))
        assertFalse(BookFileParser.isDeviceScanFileName("notes.txt"))
        assertFalse(BookFileParser.isSupportedFileName("document.pdf"))
    }
}
