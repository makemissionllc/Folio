package com.makemission.folio.data.scan

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.makemission.folio.data.logging.FolioLogger
import java.io.File

/**
 * Representation of an EPUB found during storage scanning.
 * Supports both direct [File] references (from raw filesystem walk or MediaStore DATA column)
 * and [Uri] references (from Storage Access Framework tree grants or MediaStore ID).
 */
data class ScannedBookSource(
    val uri: Uri? = null,
    val displayName: String,
    val originalPathOrUri: String,
    val file: File? = null,
)

/**
 * Result of a device storage scan.
 * Includes discovered book sources and a count of folders skipped due to exceeding [MAX_DEPTH].
 * [isTruncated] is true only if the scan hit the generous [MAX_FILES] safety cap and
 * stopped early — callers must surface this to the user instead of silently dropping files.
 */
data class ScanResult(
    val items: List<ScannedBookSource>,
    val deepFoldersSkipped: Int = 0,
    val isTruncated: Boolean = false,
) {
    /** Files found directly on disk (raw files only). Kept for backward compatibility. */
    val files: List<File> get() = items.mapNotNull { it.file }
}

/**
 * Automatic device storage scanning for EPUB files (§6).
 *
 * Discovery hierarchy:
 * 1. Primary: Storage Access Framework (SAF) folder grant(s) (via ACTION_OPEN_DOCUMENT_TREE),
 *    searched recursively using [DocumentFile] APIs (bypasses scoped-storage restrictions).
 * 2. Fallback: Raw filesystem walk of common locations (Downloads, Documents, external root)
 *    up to [MAX_DEPTH] (8) for deeply nested collections.
 * 3. Fallback: [MediaStore.Files] query for `.epub` entries.
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

    /**
     * Safety cap — generous enough that a normal library (even 300 EPUBs) never hits it,
     * but prevents a truly pathological folder (e.g. 10k files) from exhausting memory.
     * Previously 80, which silently hid books. Now 5000 and *visible*: [ScanResult.isTruncated]
     * is true when hit, so the UI can inform the user instead of silently dropping files.
     * The scan collects only lightweight [ScannedBookSource] refs (path/Uri + displayName),
     * not parsed book contents, so holding 300 (or even 2000) in memory is trivial (~KBs).
     * Heavy work (full ZIP/Jsoup parse + X-Ray) is *not* done during scan — it is
     * deferred per-book to [com.makemission.folio.data.work.BookProcessingWorker],
     * which throttles to 2 concurrent parses via Semaphore(2) and queues the rest
     * (300 queued = queue, not burst — verified for 300-book scenario, see Worker docs).
     */
    const val MAX_FILES = 5000
    const val MAX_DEPTH = 8
    private const val TAG = "EpubScanner"

    fun hasStoragePermission(context: Context): Boolean {
        val hasLegacy = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasImages = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val hasVideo = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasSaf = try {
            context.contentResolver.persistedUriPermissions.any { it.isReadPermission }
        } catch (_: Exception) { false }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasSaf || hasLegacy || hasImages || hasVideo || hasAudio
        } else {
            hasLegacy || hasSaf
        }

        FolioLogger.i(TAG, "hasStoragePermission check: legacy=$hasLegacy images=$hasImages saf=$hasSaf -> $result (SDK ${Build.VERSION.SDK_INT})")
        return result
    }

    /**
     * Scan device storage for EPUB files.
     * Searches SAF tree grant first (primary), then file walk and MediaStore as fallbacks.
     */
    fun scan(context: Context, configuredTreeUri: Uri? = null): ScanResult {
        FolioLogger.i(TAG, "scan start: hasPermission=${hasStoragePermission(context)} maxFiles=$MAX_FILES maxDepth=$MAX_DEPTH")

        if (!hasStoragePermission(context)) {
            FolioLogger.w(TAG, "scan: permission not granted at scan time — returning empty")
            return ScanResult(emptyList(), 0)
        }

        val out = mutableListOf<ScannedBookSource>()
        val seenKeys = mutableSetOf<String>()
        var deepFoldersSkipped = 0

        // 1) PRIMARY: SAF grants (DocumentFile APIs)
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
            FolioLogger.i(TAG, "scan: primary SAF search with ${safUris.size} folder grant(s)")
            for (uri in safUris) {
                if (out.size >= MAX_FILES) break
                try {
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile == null || !docFile.exists() || !docFile.canRead()) {
                        FolioLogger.w(TAG, "SAF tree URI not accessible: $uri")
                        continue
                    }
                    val before = out.size
                    walkSafDoc(context, docFile, 0, out, seenKeys) { deepFoldersSkipped++ }
                    FolioLogger.i(TAG, "SAF walk for $uri added ${out.size - before} items (total ${out.size})")
                } catch (e: Exception) {
                    FolioLogger.w(TAG, "SAF walk error for $uri: ${e.message}", e)
                }
            }
        } else {
            FolioLogger.i(TAG, "scan: no SAF grants present, proceeding to fallbacks")
        }

        // 2) FALLBACK: Raw filesystem walk
        if (out.size < MAX_FILES) {
            val dirs = listOfNotNull(
                Environment.getExternalStorageDirectory(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            ).distinctBy { it.absolutePath }

            val beforeFileWalk = out.size
            for (dir in dirs) {
                if (out.size >= MAX_FILES) break
                try {
                    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
                    walkDir(dir, 0, out, seenKeys) { deepFoldersSkipped++ }
                } catch (e: Exception) {
                    FolioLogger.w(TAG, "walkDir error for ${dir.absolutePath}: ${e.message}", e)
                }
            }
            FolioLogger.i(TAG, "scan: file walk fallback added ${out.size - beforeFileWalk} items (total ${out.size})")
        }

        // 3) FALLBACK: MediaStore query
        if (out.size < MAX_FILES) {
            val beforeMedia = out.size
            try {
                queryMediaStore(context, out, seenKeys)
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
        return ScanResult(out, deepFoldersSkipped, isTruncated)
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
        onDepthExceeded: () -> Unit = {},
    ) {
        if (depth > MAX_DEPTH) {
            onDepthExceeded()
            FolioLogger.w(TAG, "walkDir depth $depth > MAX_DEPTH $MAX_DEPTH for ${dir.absolutePath}, skipping deeper")
            return
        }
        if (out.size >= MAX_FILES) return

        val files = try { dir.listFiles() } catch (e: Exception) {
            FolioLogger.w(TAG, "walkDir listFiles failed for ${dir.absolutePath}: ${e.message}", e)
            null
        } ?: return

        for (f in files) {
            if (out.size >= MAX_FILES) break
            try {
                if (f.isDirectory && f.canRead() && !f.name.startsWith(".")) {
                    if (f.name == "Android" && depth == 0) {
                        continue
                    }
                    walkDir(f, depth + 1, out, seenKeys, onDepthExceeded)
                } else if (f.isFile && f.extension.equals("epub", ignoreCase = true)) {
                    val path = f.absolutePath
                    if (seenKeys.add(path)) {
                        out.add(
                            ScannedBookSource(
                                uri = null,
                                displayName = f.name,
                                originalPathOrUri = path,
                                file = f,
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
            if (out.size >= MAX_FILES) break
            try {
                val name = child.name.orEmpty()
                if (child.isDirectory && child.canRead() && !name.startsWith(".")) {
                    if (name == "Android" && depth == 0) {
                        continue
                    }
                    walkSafDoc(context, child, depth + 1, out, seenKeys, onDepthExceeded)
                } else if (child.isFile && name.endsWith(".epub", ignoreCase = true)) {
                    val uriKey = child.uri.toString()
                    if (seenKeys.add(uriKey)) {
                        out.add(
                            ScannedBookSource(
                                uri = child.uri,
                                displayName = name,
                                originalPathOrUri = uriKey,
                                file = null,
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
    ) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%.epub")
        val uri = MediaStore.Files.getContentUri("external")
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)

                while (cursor.moveToNext() && out.size < MAX_FILES) {
                    val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else -1L

                    if (displayName == null || !displayName.endsWith(".epub", ignoreCase = true)) {
                        continue
                    }

                    if (path != null && path.endsWith(".epub", ignoreCase = true)) {
                        val file = File(path)
                        if (file.exists() && file.canRead()) {
                            if (seenKeys.add(file.absolutePath)) {
                                out.add(
                                    ScannedBookSource(
                                        uri = null,
                                        displayName = displayName,
                                        originalPathOrUri = file.absolutePath,
                                        file = file,
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
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
