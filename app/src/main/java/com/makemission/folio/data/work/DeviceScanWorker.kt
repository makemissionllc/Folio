package com.makemission.folio.data.work

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makemission.folio.data.cache.ParsedBookCache
import com.makemission.folio.data.cache.TruePageCache
import com.makemission.folio.data.db.FolioDatabase
import com.makemission.folio.data.db.entity.BookEntity
import com.makemission.folio.data.epub.BookFileParser
import com.makemission.folio.data.epub.EpubParser
import com.makemission.folio.data.logging.FolioLogger
import com.makemission.folio.data.scan.EpubScanner
import com.makemission.folio.data.scan.ScannedBookSource
import com.makemission.folio.data.settings.SettingsRepository
import com.makemission.folio.data.xray.XRayCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InterruptedIOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

/** One-book-at-a-time device scan; WorkManager pauses it when idle/battery constraints change. */
class DeviceScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        scanSemaphore.withPermit {
        val context = applicationContext
        if (!EpubScanner.hasStoragePermission(context)) return@withContext Result.success()
        val repo = SettingsRepository.get(context)
        val manual = inputData.getBoolean("manual", false)
        if (!manual && !repo.autoScanEnabled.first()) return@withContext Result.success()
        if (!manual && DeviceScanScheduler.wasScannedRecently(context)) return@withContext Result.success()
        val folder = repo.booksFolderUri.first()?.let(Uri::parse)
        var lastThermalCheck = 0L
        var warm = false
        val result = EpubScanner.scan(context, folder) {
            if (isStopped) true
            else {
                val now = SystemClock.elapsedRealtime()
                if (now - lastThermalCheck >= 2_000L) {
                    warm = thermalStatus(context) >= PowerManager.THERMAL_STATUS_MODERATE
                    lastThermalCheck = now
                }
                warm
            }
        }
        if (result.wasInterrupted) return@withContext Result.retry()
        val dao = FolioDatabase.get(context).bookDao()
        val existing = dao.getAll()
        val byPath = existing.mapNotNull { book -> book.importedFromPath?.let { it to book } }.toMap().toMutableMap()
        val byHash = existing.mapNotNull { book -> book.fileHash?.let { it to book } }.toMap().toMutableMap()
        val cacheFile = File(context.filesDir, "device_scan_fingerprints.json")
        val previousCache = runCatching { JSONObject(cacheFile.readText()) }.getOrDefault(JSONObject())
        val cache = JSONObject()
        var added = 0
        var skipped = 0
        var rejected = 0
        for ((index, source) in result.items.withIndex()) {
            if (isStopped) {
                saveCache(context, cache, previousCache)
                return@withContext Result.retry()
            }
            if (!manual && index % 10 == 0 && !repo.autoScanEnabled.first()) {
                saveCache(context, cache, previousCache)
                return@withContext Result.success()
            }
            if (thermalStatus(context) >= PowerManager.THERMAL_STATUS_MODERATE) {
                FolioLogger.i("Scan", "Pausing scan while device is warm")
                saveCache(context, cache, previousCache)
                return@withContext Result.retry()
            }
            if (index % 10 == 0) setProgress(workDataOf("found" to result.items.size, "checked" to index, "added" to added))
            val stored = byPath[source.originalPathOrUri]
            val fingerprint = if (source.fileSize >= 0 && source.lastModifiedMillis > 0) {
                "${source.fileSize}:${source.lastModifiedMillis}"
            } else null
            if (fingerprint != null) {
                val previous = previousCache.optString(source.originalPathOrUri)
                val previousFingerprint = previous.substringBeforeLast(':', "")
                val previousHash = previous.substringAfterLast(':', "")
                val cachedBook = stored ?: byHash[previousHash]
                if (previousFingerprint == fingerprint && previousHash == "rejected-v1") {
                    cache.put(source.originalPathOrUri, previous)
                    rejected++
                    continue
                }
                if (previousFingerprint == fingerprint && cachedBook != null && File(cachedBook.filePath).exists()) {
                    cache.put(source.originalPathOrUri, previous)
                    skipped++
                    continue
                }
            }
            val cleaned = source.displayName.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            val safeName = cleaned.substringBeforeLast('.').take(90) + "." + cleaned.substringAfterLast('.')
            if (safeName.isBlank() || !BookFileParser.isDeviceScanFileName(safeName)) {
                rejected++
                continue
            }
            val tmp = File(File(context.filesDir, "books").apply { mkdirs() }, "scan_${UUID.randomUUID()}_$safeName")
            try {
                val hash = copyAndHash(context, source, tmp)
                val hashMatch = byHash[hash]?.takeIf { File(it.filePath).exists() }
                if (hashMatch != null) {
                    skipped++
                    if (fingerprint != null) cache.put(source.originalPathOrUri, "$fingerprint:$hash")
                    continue
                }
                val metadata = BookFileParser.metadata(tmp)
                if (metadata == null || (tmp.extension.lowercase() in setOf("mobi", "fb2") && BookFileParser.parse(tmp) == null)) {
                    if (fingerprint != null) cache.put(source.originalPathOrUri, "$fingerprint:rejected-v1")
                    rejected++
                    continue
                }
                val id = stored?.id ?: UUID.randomUUID().toString()
                val dest = File(tmp.parentFile, "${id}_$safeName")
                if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)
                val cover = if (dest.extension.equals("epub", true)) EpubParser.extractCoverToFile(dest, context, id) else null
                val entity = BookEntity(
                    id = id,
                    title = metadata.title.ifBlank { safeName.substringBeforeLast('.') },
                    author = metadata.author,
                    filePath = dest.absolutePath,
                    coverImagePath = cover,
                    addedAt = stored?.addedAt ?: System.currentTimeMillis(),
                    fileHash = hash,
                    importedFromPath = source.originalPathOrUri,
                )
                dao.insert(entity)
                byPath[source.originalPathOrUri] = entity
                stored?.fileHash?.let { byHash.remove(it) }
                byHash[hash] = entity
                if (stored != null && stored.filePath != dest.absolutePath) File(stored.filePath).delete()
                if (stored?.coverImagePath != null && stored.coverImagePath != cover) File(stored.coverImagePath).delete()
                ParsedBookCache.invalidate(context, id)
                TruePageCache.invalidate(context, id)
                XRayCache.invalidate(context, id)
                BookProcessingScheduler.scheduleDeferred(context, id, hash)
                if (fingerprint != null) cache.put(source.originalPathOrUri, "$fingerprint:$hash")
                added++
            } catch (_: InterruptedIOException) {
                saveCache(context, cache, previousCache)
                return@withContext Result.retry()
            } catch (e: Exception) {
                FolioLogger.w("Scan", "Could not index ${source.displayName}: ${e.message}", e)
                rejected++
            } finally {
                tmp.delete()
            }
        }
        saveCache(context, cache)
        DeviceScanScheduler.recordCompleted(context)
        FolioLogger.i("Scan", "Idle scan: ${result.items.size} found, $added added, $skipped unchanged, $rejected unreadable/unsupported; truncated=${result.isTruncated}")
        Result.success(workDataOf("found" to result.items.size, "added" to added, "skipped" to skipped,
            "rejected" to rejected, "truncated" to result.isTruncated))
        }
    }

    private fun copyAndHash(context: Context, source: ScannedBookSource, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = source.file?.inputStream() ?: source.uri?.let { context.contentResolver.openInputStream(it) }
            ?: throw IllegalArgumentException("Source is not readable")
        input.use { stream ->
            DigestOutputStream(destination.outputStream(), digest).use { output ->
                val buffer = ByteArray(64 * 1024)
                var lastCheck = SystemClock.elapsedRealtime()
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    val now = SystemClock.elapsedRealtime()
                    if (isStopped || now - lastCheck >= 2_000L) {
                        if (isStopped || thermalStatus(context) >= PowerManager.THERMAL_STATUS_MODERATE) {
                            throw InterruptedIOException("Scan paused")
                        }
                        lastCheck = now
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun thermalStatus(context: Context): Int =
        context.getSystemService(PowerManager::class.java)?.currentThermalStatus
            ?: PowerManager.THERMAL_STATUS_NONE

    private fun saveCache(context: Context, cache: JSONObject, previous: JSONObject? = null) {
        val destination = File(context.filesDir, "device_scan_fingerprints.json")
        val temporary = File(context.filesDir, "device_scan_fingerprints.tmp")
        val content = if (previous == null) cache else JSONObject(previous.toString()).apply {
            val keys = cache.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, cache.getString(key))
            }
        }
        temporary.writeText(content.toString())
        if (!temporary.renameTo(destination)) temporary.copyTo(destination, overwrite = true)
        temporary.delete()
    }

    companion object {
        private val scanSemaphore = Semaphore(1)
    }
}
