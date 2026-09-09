package com.makemission.folio.data.scan

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Automatic device storage scanning for EPUB files (§6).
 * Searches common external locations (Downloads, Documents, general external storage)
 * for .epub files, reusing storage permission already requested during onboarding.
 * Only EPUB is implemented for now — PDF parsing doesn't exist yet, but this is
 * the plug-in point:
 *   • [walkDir] extension check: `|| f.extension.equals("pdf", ignoreCase = true)` (+ EpubParser PDF branch)
 *   • [queryMediaStore] selection: `DISPLAY_NAME LIKE %.pdf`
 *   • [findEpubFiles] filter + pipeline in LibraryViewModel's `importFileInternal` (reuse EpubParser)
 *
 * Pure on-device, no network. Handles permission denial gracefully (returns empty, no crash).
 * Structural inspiration from book-story-master's BrowseScanOption / FileSystemRepository only.
 */
object EpubScanner {

    private const val MAX_FILES = 80
    private const val MAX_DEPTH = 4

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            // For EPUB (non-media) on 33+, READ_MEDIA_* won't cover it, but we still treat any of them as "has storage"
            // If none granted, also check legacy READ_EXTERNAL_STORAGE (some devices still grant)
            val legacy = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            images || video || audio || legacy
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Find .epub files on device. Returns distinct files, capped at MAX_FILES.
     * If permission denied, returns empty (feature disabled, no crash, no prompt).
     * Pure on-device, no network. PDF would plug in here as an additional extension check
     * (see class KDoc — add `pdf` to the filter/walk/selection and add a PdfParser branch).
     */
    fun findEpubFiles(context: Context): List<File> {
        if (!hasStoragePermission(context)) return emptyList()

        val out = mutableListOf<File>()
        val seenPaths = mutableSetOf<String>()

        // 1) Walk common file-system locations (best-effort, may be restricted by scoped storage on 33+)
        val dirs = listOfNotNull(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            // On some devices general external storage is the parent of Downloads/Documents; walk it shallowly
        ).filter { it.exists() && it.isDirectory && it.canRead() }

        for (dir in dirs) {
            try {
                walkDir(dir, 0, out, seenPaths)
                if (out.size >= MAX_FILES) break
            } catch (_: Exception) {}
        }

        // 2) MediaStore query for epub — works when File walk is blocked by scoped storage.
        //    PDF plug-in: selection `(_display_name LIKE ? OR _display_name LIKE ?)` with args `%.epub`, `%.pdf`
        try {
            queryMediaStore(context, out, seenPaths)
        } catch (_: Exception) {}

        return out
            .filter { it.exists() && it.isFile && it.canRead() && it.extension.equals("epub", ignoreCase = true) }
            .distinctBy { it.absolutePath }
            .take(MAX_FILES)
    }

    private fun walkDir(dir: File, depth: Int, out: MutableList<File>, seen: MutableSet<String>) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            if (out.size >= MAX_FILES) break
            try {
                if (f.isDirectory && f.canRead() && !f.name.startsWith(".")) {
                    // Avoid Android/data and similar deep system dirs to keep lightweight
                    if (f.name == "Android" && depth == 0) {
                        // Only walk one level into Android/media etc, not deep
                        continue
                    }
                    walkDir(f, depth + 1, out, seen)
                } else if (f.isFile && f.extension.equals("epub", ignoreCase = true)
                    // PDF plug-in: || f.extension.equals("pdf", ignoreCase = true) — route to PdfParser when it exists
                ) {
                    val path = f.absolutePath
                    if (seen.add(path)) out.add(f)
                }
            } catch (_: Exception) {}
        }
    }

    private fun queryMediaStore(context: Context, out: MutableList<File>, seen: MutableSet<String>) {
        // Query MediaStore.Files for epub display names. Works when File walk is blocked by scoped storage.
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME)
        // Selection: _display_name LIKE %.epub — PDF plug-in adds OR _display_name LIKE %.pdf
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%.epub") // PDF plug-in: arrayOf("%.epub", "%.pdf") with `selection = "... LIKE ? OR ... LIKE ?"`
        val uri = MediaStore.Files.getContentUri("external")
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val dataIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext() && out.size < MAX_FILES) {
                    val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    if (path != null && path.endsWith(".epub", ignoreCase = true)) {
                        val file = File(path)
                        if (file.exists() && file.canRead() && seen.add(file.absolutePath)) {
                            out.add(file)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
