package com.makemission.folio.data.scan

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.documentfile.provider.DocumentFile
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.epub.BookFileParser
import java.io.File

/**
 * Representation of an EPUB, MOBI or FB2 found during storage scanning.
 * Supports both direct [File] references (from raw filesystem walk or MediaStore DATA column)
 * and [Uri] references (from Storage Access Framework tree grants or MediaStore ID).
 */
data class ScannedBookSource(
    val uri: Uri? = null,
    val displayName: String,
    val originalPathOrUri: String,
    val file: File? = null,
    /** File size in bytes when known, used with modification time for change detection. */
    val fileSize: Long = -1L,
    val lastModifiedMillis: Long = -1L,
)

/**
 * Result of a device storage scan.
 * Includes discovered book sources and a count of folders skipped due to exceeding [MAX_DEPTH].
 * [isTruncated] is true only if the scan hit the generous [MAX_FILES] safety cap and
 * stopped early. Background work reports this flag in its result data.
 */
data class ScanResult(
    val items: List<ScannedBookSource>,
    val deepFoldersSkipped: Int = 0,
    val isTruncated: Boolean = false,
    val wasInterrupted: Boolean = false,
) {
    /** Files found directly on disk (raw files only). Kept for backward compatibility. */
    val files: List<File> get() = items.mapNotNull { it.file }
}

/**
 * Automatic shared-storage discovery for EPUB, MOBI and FB2 files. All-files access enables
 * walking mounted shared volumes; SAF folder grants provide an optional narrower path.
 *
 * Handles permission denial gracefully (returns empty, no crash).
 * Tracks folders that exceed [MAX_DEPTH] so the user can be informed.
 *
 * PDF plug-in point:
 *   • [walkDir] / [walkSafDoc] extension check: `|| f.extension.equals("pdf", ignoreCase = true)`
 *   • [queryMediaStore] selection: `DISPLAY_NAME LIKE %.pdf`
 *   • Route to a future PdfParser in LibraryViewModel
 */
object EpubScanner {

    /** Safety limits for pathological directory trees; a capped result is reported explicitly. */
    const val MAX_FILES = 50000
    const val MAX_DEPTH = 128
    private const val TAG = "EpubScanner"

    /** A tree grant supports a partial scan; all-files access supports shared-storage volumes. */
    fun hasWholeDeviceAccess(): Boolean = Environment.isExternalStorageManager()

    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun hasStoragePermission(context: Context): Boolean {
        val hasAllFiles = Environment.isExternalStorageManager()
        val hasSaf = try {
            context.contentResolver.persistedUriPermissions.any { it.isReadPermission }
        } catch (_: Exception) { false }

        val result = hasAllFiles || hasSaf

        FolioLogger.i(TAG, "hasStoragePermission check: allFiles=$hasAllFiles saf=$hasSaf -> $result (SDK ${Build.VERSION.SDK_INT})")
        return result
    }

    /** Scan mounted shared volumes, optional SAF folders, and MediaStore for supported ebook files. */
    fun scan(
        context: Context,
        configuredTreeUri: Uri? = null,
        shouldStop: () -> Boolean = { false },
    ): ScanResult {
        FolioLogger.i(TAG, "scan start: hasPermission=${hasStoragePermission(context)} maxFiles=$MAX_FILES maxDepth=$MAX_DEPTH")

        if (!hasStoragePermission(context)) {
            FolioLogger.w(TAG, "scan: permission not granted at scan time — returning empty")
            return ScanResult(emptyList(), 0)
        }

        val out = mutableListOf<ScannedBookSource>()
        val seenKeys = mutableSetOf<String>()
        val seenDirectories = mutableSetOf<String>()
        // Exact source keys avoid losing distinct books with the same name and size.
        var deepFoldersSkipped = 0

        // 1) Shared-storage volumes
        if (out.size < MAX_FILES) {
            val volumes = try {
                context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }
            } catch (_: Exception) { emptyList() }
            val dirs = (volumes + Environment.getExternalStorageDirectory()).distinctBy { it.absolutePath }

            val beforeFileWalk = out.size
            for (dir in dirs) {
                if (out.size >= MAX_FILES || shouldStop()) break
                try {
                    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
                    walkDir(dir, 0, out, seenKeys, seenDirectories, shouldStop) { deepFoldersSkipped++ }
                } catch (e: Exception) {
                    FolioLogger.w(TAG, "walkDir error for ${dir.absolutePath}: ${e.message}", e)
                }
            }
            FolioLogger.i(TAG, "scan: file walk added ${out.size - beforeFileWalk} items (total ${out.size})")
        }

        // 2) Persisted SAF folder grants (DocumentFile APIs)
        val safUris = mutableListOf<Uri>()
        if (configuredTreeUri != null) {
            safUris.add(configuredTreeUri)
        }
        val persisted = try {
            context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        } catch (e: Exception) {
            FolioLogger.w(TAG, "Failed to query persistedUriPermissions: ${e.message}", e)
            emptyList()
        }
        for (p in persisted) {
            if (p.uri !in safUris) {
                safUris.add(p.uri)
            }
        }

        if (safUris.isNotEmpty()) {
            FolioLogger.i(TAG, "scan: SAF search with ${safUris.size} folder grant(s)")
            for (uri in safUris) {
                if (out.size >= MAX_FILES || shouldStop()) break
                try {
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile == null || !docFile.exists() || !docFile.canRead()) {
                        FolioLogger.w(TAG, "SAF tree URI not accessible: $uri")
                        continue
                    }
                    val before = out.size
                    walkSafDoc(context, docFile, 0, out, seenKeys, shouldStop) { deepFoldersSkipped++ }
                    FolioLogger.i(TAG, "SAF walk for $uri added ${out.size - before} items (total ${out.size})")
                } catch (e: Exception) {
                    FolioLogger.w(TAG, "SAF walk error for $uri: ${e.message}", e)
                }
            }
        } else {
            FolioLogger.i(TAG, "scan: no SAF grants present, proceeding to fallbacks")
        }

        // 3) FALLBACK: MediaStore query
        if (out.size < MAX_FILES && !shouldStop()) {
            val beforeMedia = out.size
            try {
                queryMediaStore(context, out, seenKeys, shouldStop)
                FolioLogger.i(TAG, "scan: MediaStore query added ${out.size - beforeMedia} items (total ${out.size})")
            } catch (e: Exception) {
                FolioLogger.w(TAG, "queryMediaStore error: ${e.message}", e)
            }
        }

        val isTruncated = out.size >= MAX_FILES
        if (isTruncated) {
            FolioLogger.w(TAG, "scan final: ${out.size} items found, $deepFoldersSkipped folders too deep — TRUNCATED at safety cap $MAX_FILES (not all files shown)")
        } else {
            FolioLogger.i(TAG, "scan final: ${out.size} items found, $deepFoldersSkipped folders too deep")
        }
        return ScanResult(out, deepFoldersSkipped, isTruncated, shouldStop())
    }

    /**
     * Backward-compatible findEpubFiles returning List<File>.
     */
    fun findEpubFiles(context: Context): List<File> = scan(context).files

    internal fun walkDir(
        dir: File,
        depth: Int,
        out: MutableList<ScannedBookSource>,
        seenKeys: MutableSet<String>,
        seenDirectories: MutableSet<String> = mutableSetOf(),
        shouldStop: () -> Boolean = { false },
        onDepthExceeded: () -> Unit = {},
    ) {
        if (depth > MAX_DEPTH) {
            onDepthExceeded()
            FolioLogger.w(TAG, "walkDir depth $depth > MAX_DEPTH $MAX_DEPTH for ${dir.absolutePath}, skipping deeper")
            return
        }
        if (out.size >= MAX_FILES) return
        if (!seenDirectories.add(dir.canonicalPath)) return

        val files = try { dir.listFiles() } catch (e: Exception) {
            FolioLogger.w(TAG, "walkDir listFiles failed for ${dir.absolutePath}: ${e.message}", e)
            null
        } ?: return

        for (f in files) {
            if (out.size >= MAX_FILES || shouldStop()) break
            try {
                if (f.isDirectory && f.canRead()) {
                    if (dir.name == "Android" && (f.name == "data" || f.name == "obb")) {
                        continue
                    }
                    walkDir(f, depth + 1, out, seenKeys, seenDirectories, shouldStop, onDepthExceeded)
                } else if (f.isFile && isBookName(f.name)) {
                    val path = f.absolutePath
                    val size = try { f.length() } catch (_: Exception) { -1L }
                    if (seenKeys.add(path)) {
                        out.add(
                            ScannedBookSource(
                                uri = null,
                                displayName = f.name,
                                originalPathOrUri = path,
                                file = f,
                                fileSize = size,
                                lastModifiedMillis = f.lastModified(),
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                FolioLogger.w(TAG, "walkDir entry error for ${f.absolutePath}: ${e.message}", e)
            }
        }
    }

    internal fun walkSafDoc(
        context: Context,
        doc: DocumentFile,
        depth: Int,
        out: MutableList<ScannedBookSource>,
        seenKeys: MutableSet<String>,
        shouldStop: () -> Boolean = { false },
        onDepthExceeded: () -> Unit = {},
    ) {
        if (depth > MAX_DEPTH) {
            onDepthExceeded()
            FolioLogger.w(TAG, "walkSafDoc depth $depth > MAX_DEPTH $MAX_DEPTH for ${doc.name}, skipping deeper")
            return
        }
        if (out.size >= MAX_FILES) return

        val children = try { doc.listFiles() } catch (e: Exception) {
            FolioLogger.w(TAG, "walkSafDoc listFiles failed for ${doc.name} depth $depth: ${e.message}", e)
            null
        } ?: return

        for (child in children) {
            if (out.size >= MAX_FILES || shouldStop()) break
            try {
                val name = child.name.orEmpty()
                if (child.isDirectory && child.canRead()) {
                    if (doc.name == "Android" && (name == "data" || name == "obb")) {
                        continue
                    }
                    walkSafDoc(context, child, depth + 1, out, seenKeys, shouldStop, onDepthExceeded)
                } else if (child.isFile && isBookName(name)) {
                    val uriKey = child.uri.toString()
                    val size = try { child.length() } catch (_: Exception) { -1L }
                    if (seenKeys.add(uriKey)) {
                        out.add(
                            ScannedBookSource(
                                uri = child.uri,
                                displayName = name,
                                originalPathOrUri = uriKey,
                                file = null,
                                fileSize = size,
                                lastModifiedMillis = child.lastModified(),
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                FolioLogger.w(TAG, "walkSafDoc entry error for ${child.name}: ${e.message}", e)
            }
        }
    }

    private fun queryMediaStore(
        context: Context,
        out: MutableList<ScannedBookSource>,
        seenKeys: MutableSet<String>,
        shouldStop: () -> Boolean = { false },
    ) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
        )
        val selection = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)"
        val args = arrayOf("%.epub", "%.mobi", "%.fb2")
        val uri = MediaStore.Files.getContentUri("external")
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

                while (cursor.moveToNext() && out.size < MAX_FILES && !shouldStop()) {
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    val size = if (sizeIdx >= 0) try { cursor.getLong(sizeIdx) } catch (_: Exception) { -1L } else -1L

                    if (displayName == null || !isBookName(displayName)) {
                        continue
                    }
                    if (path != null && isBookName(path)) {
                        val file = File(path)
                        if (file.exists() && file.canRead()) {
                            val actualSize = try { file.length() } catch (_: Exception) { size }
                            if (seenKeys.add(file.absolutePath)) {
                                out.add(
                                    ScannedBookSource(
                                        uri = null,
                                        displayName = displayName,
                                        originalPathOrUri = file.absolutePath,
                                        file = file,
                                        fileSize = actualSize,
                                        lastModifiedMillis = file.lastModified(),
                                    )
                                )
                            }
                            continue
                        }
                    }

                    if (id != -1L) {
                        val contentUri = ContentUris.withAppendedId(uri, id)
                        val uriStr = contentUri.toString()
                        if (seenKeys.add(uriStr)) {
                            out.add(
                                ScannedBookSource(
                                    uri = contentUri,
                                    displayName = displayName,
                                    originalPathOrUri = uriStr,
                                    file = null,
                                    fileSize = size,
                                    lastModifiedMillis = -1L,
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isBookName(name: String): Boolean = BookFileParser.isDeviceScanFileName(name)
}
