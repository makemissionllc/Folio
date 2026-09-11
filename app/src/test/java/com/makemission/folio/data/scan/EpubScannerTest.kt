package com.makemission.folio.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun maxwellCollection_foundWithMaxDepth8_andVerifiedViaBothMethods() {
        // Recreate the exact structure from the log:
        // /storage/emulated/0/Books/John C. Maxwell Collection (36 Books)/John Maxwell/[book folder]/[file].epub
        // Depth 0: /storage/emulated/0 (simulated root)
        val root = tempFolder.newFolder("emulated_0")
        val booksDir = File(root, "Books").apply { mkdir() } // Depth 1 from storage root
        val maxwellCollDir = File(booksDir, "John C. Maxwell Collection (36 Books)").apply { mkdir() } // Depth 2
        val johnMaxwellDir = File(maxwellCollDir, "John Maxwell").apply { mkdir() } // Depth 3

        // 36 books in their own folders
        // If files are inside subfolder:
        // Depth 0: /storage/emulated/0
        // Depth 1: Books
        // Depth 2: John C. Maxwell Collection (36 Books)
        // Depth 3: John Maxwell
        // Depth 4: [book folder]
        // Depth 5: [sub folder or file at depth 5]
        for (i in 1..36) {
            val bookFolder = File(johnMaxwellDir, "Book $i").apply { mkdir() } // Depth 4
            val subFolder = File(bookFolder, "content").apply { mkdir() } // Depth 5
            val epubFile = File(subFolder, "Book_$i.epub")
            epubFile.writeText("simulated epub content $i")
        }

        // Method 1: Raw file walk from storage root with MAX_DEPTH = 8
        val outFromRoot = mutableListOf<ScannedBookSource>()
        val seenFromRoot = mutableSetOf<String>()
        var skippedRoot = 0

        EpubScanner.walkDir(root, 0, outFromRoot, seenFromRoot) { skippedRoot++ }

        assertEquals("All 36 Maxwell books found by Method 1 (file walk with MAX_DEPTH=8)", 36, outFromRoot.size)
        assertEquals("No folders skipped with MAX_DEPTH=8", 0, skippedRoot)

        // Method 2: SAF folder grant directly on Books folder (/storage/emulated/0/Books)
        // When user chooses Books via SAF, Books is the root (depth 0)
        // Depth 0: Books
        // Depth 1: John C. Maxwell Collection (36 Books)
        // Depth 2: John Maxwell
        // Depth 3: Book $i
        // Depth 4: content
        // Depth 4/5: Book_$i.epub
        val outFromSaf = mutableListOf<ScannedBookSource>()
        val seenFromSaf = mutableSetOf<String>()
        var skippedSaf = 0

        EpubScanner.walkDir(booksDir, 0, outFromSaf, seenFromSaf) { skippedSaf++ }

        assertEquals("All 36 Maxwell books found by Method 2 (SAF Books folder)", 36, outFromSaf.size)
        assertEquals("No folders skipped when scanning from SAF Books folder", 0, skippedSaf)

        // Verification of Old Bug: With MAX_DEPTH = 4, depth 5 was skipped!
        fun walkDirOld(dir: File, depth: Int, out: MutableList<File>, onSkip: () -> Unit) {
            if (depth > 4) {
                onSkip()
                return
            }
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    walkDirOld(f, depth + 1, out, onSkip)
                } else if (f.isFile && f.extension.equals("epub", ignoreCase = true)) {
                    out.add(f)
                }
            }
        }

        val outOld = mutableListOf<File>()
        var skippedOld = 0
        walkDirOld(root, 0, outOld) { skippedOld++ }

        assertEquals("Old code with MAX_DEPTH 4 skipped all depth 5 files (0 found)", 0, outOld.size)
        assertEquals("Old code skipped 36 folders at depth 5", 36, skippedOld)
    }

    @Test
    fun walkDir_countsDeepFoldersExceedingMaxDepth() {
        val root = tempFolder.newFolder("deep_root")
        var current = root
        // Create 10 levels of directories
        for (i in 1..10) {
            current = File(current, "level_$i").apply { mkdir() }
        }
        val deepEpub = File(current, "too_deep.epub")
        deepEpub.writeText("too deep")

        val out = mutableListOf<ScannedBookSource>()
        val seen = mutableSetOf<String>()
        var skippedCount = 0

        EpubScanner.walkDir(root, 0, out, seen) { skippedCount++ }

        // level_9 is at depth 9, level_10 is at depth 10 -> depth > MAX_DEPTH (8)
        assertEquals(0, out.size)
        assertTrue("Skipped deep folders should be > 0", skippedCount > 0)
    }

    @Test
    fun maxDepth_isAtLeast8() {
        assertTrue(EpubScanner.MAX_DEPTH >= 8)
    }
}
