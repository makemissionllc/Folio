package com.makemission.folio.data.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Local debug logging for bug-fixing — never auto-transmits, on-device only.
 *
 * Focus: errors, exceptions, failed operations (import/parsing/scan/crash)
 * not every UI interaction. Privacy: technical events only — never logs
 * full book text, highlight content, or bookmark preview. Messages are
 * truncated/sanitized if suspiciously long.
 *
 * Storage: rolling log file `filesDir/logs/folio.log`, capped at
 * [MAX_FILE_BYTES] (~256 KB ≈ 1–2k entries). When exceeded, keeps the
 * most recent half (trim, not unbounded growth). No network.
 *
 * Thread-safe: single-thread executor for file I/O so callers (main thread)
 * never block on disk. Synchronous [logSync] also available for crash handler.
 *
 * Structural inspiration from book-story-master's crash/layout layering only
 * — no code copied.
 */
object FolioLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "folio.log"
    private const val MAX_FILE_BYTES = 256 * 1024 // 256 KB rolling cap
    private const val MAX_MESSAGE_LEN = 2000
    private const val TAG_MAX_LEN = 32

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FolioLogger").apply { isDaemon = true }
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var appContext: Context? = null
    @Volatile private var installedCrashHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            getLogDir(context).mkdirs()
            getLogFile(context).apply { if (!exists()) createNewFile() }
        } catch (_: Exception) {}
        installCrashHandler(context)
        i("FolioLogger", "Logger initialized")
    }

    // ---- Public API ----

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val safeTag = tag.take(TAG_MAX_LEN).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeMsg = sanitize(message)
        val entry = formatEntry(level, safeTag, safeMsg, throwable)
        // Async to avoid blocking caller (esp. main thread)
        try {
            executor.execute { writeEntry(entry) }
        } catch (_: Exception) {
            // Fallback sync if executor rejected
            try { writeEntry(entry) } catch (_: Exception) {}
        }
        // Also echo to logcat for local debugging (no privacy content anyway)
        try {
            when (level) {
                Level.DEBUG -> android.util.Log.d(safeTag, safeMsg, throwable)
                Level.INFO -> android.util.Log.i(safeTag, safeMsg, throwable)
                Level.WARN -> android.util.Log.w(safeTag, safeMsg, throwable)
                Level.ERROR -> android.util.Log.e(safeTag, safeMsg, throwable)
            }
        } catch (_: Exception) {}
    }

    /** Synchronous write — used by crash handler (must flush before kill). */
    fun logSync(level: Level, tag: String, message: String, throwable: Throwable?) {
        val safeTag = tag.take(TAG_MAX_LEN).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val safeMsg = sanitize(message)
        val entry = formatEntry(level, safeTag, safeMsg, throwable)
        try { writeEntry(entry) } catch (_: Exception) {}
    }

    // ---- File helpers ----

    fun getLogDir(context: Context): File = File(context.filesDir, LOG_DIR)
    fun getLogFile(context: Context): File = File(getLogDir(context), LOG_FILE)

    fun fileSizeBytes(context: Context): Long = try { getLogFile(context).length() } catch (_: Exception) { 0L }

    fun readRecent(context: Context, maxLines: Int = 400): List<String> {
        return try {
            val f = getLogFile(context)
            if (!f.exists()) emptyList()
            else {
                val lines = f.readLines()
                if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun readAllText(context: Context): String {
        return try {
            val f = getLogFile(context)
            if (!f.exists()) "" else f.readText()
        } catch (_: Exception) { "" }
    }

    fun clear(context: Context) {
        try {
            executor.execute {
                try {
                    val f = getLogFile(context)
                    if (f.exists()) f.writeText("")
                    logSync(Level.INFO, "FolioLogger", "Logs cleared by user", null)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun clearSync(context: Context) {
        try {
            val f = getLogFile(context)
            if (f.exists()) f.writeText("")
        } catch (_: Exception) {}
    }

    // ---- Internals ----

    private fun sanitize(message: String): String {
        var m = message.trim()
        if (m.length > MAX_MESSAGE_LEN) m = m.take(MAX_MESSAGE_LEN) + " …[truncated ${message.length}]"
        // Defensive: never log raw book bodies — caller must not pass them. If a caller
        // accidentally does, truncation + this heuristic avoids leaking long prose.
        // We do not attempt to detect book text beyond length cap; responsibility is on callers.
        return m.replace("\n", " ").replace("\r", " ")
    }

    private fun formatEntry(level: Level, tag: String, message: String, throwable: Throwable?): String {
        val ts = synchronized(dateFormat) { dateFormat.format(Date()) }
        val base = "$ts [${level.name}] [$tag] $message"
        if (throwable == null) return base
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        // Keep stack compact: first 18 lines max to stay within cap
        val stack = sw.toString().lines().take(18).joinToString(" | ")
        return "$base | $stack"
    }

    @Synchronized
    private fun writeEntry(entry: String) {
        val ctx = appContext ?: return
        try {
            val dir = getLogDir(ctx)
            if (!dir.exists()) dir.mkdirs()
            val file = getLogFile(ctx)
            if (!file.exists()) file.createNewFile()
            // Rolling cap: if over limit, keep most recent half
            if (file.length() > MAX_FILE_BYTES) {
                try {
                    val lines = file.readLines()
                    val keep = lines.takeLast((lines.size * 0.5).toInt().coerceAtLeast(200))
                    file.writeText(keep.joinToString("\n") + "\n")
                } catch (_: Exception) {
                    // If trim fails, truncate
                    try { file.writeText("") } catch (_: Exception) {}
                }
            }
            file.appendText(entry + "\n")
        } catch (_: Exception) {}
    }

    private fun installCrashHandler(context: Context) {
        if (installedCrashHandler != null) return
        val default = Thread.getDefaultUncaughtExceptionHandler()
        installedCrashHandler = default
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logSync(Level.ERROR, "Crash", "Uncaught on ${thread.name}: ${throwable.message}", throwable)
                // Give writer a moment to flush (logSync is sync, so immediate)
            } catch (_: Exception) {}
            // Chain to default (system will show crash dialog / kill)
            try { default?.uncaughtException(thread, throwable) } catch (_: Exception) {}
            if (default == null) {
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Exception) {}
            }
        }
    }
}
