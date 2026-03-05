package com.mcw.wallet.debug

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Timber tree that stores last 500 log entries in memory for debug report export.
 * Planted in addition to DebugTree in debug builds.
 * Strips sensitive data using SecureLogSanitizer before storing.
 */
class DebugLogTree : Timber.Tree() {

    companion object {
        private const val MAX_LOGS = 500
        private val DATE_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private val logs = ConcurrentLinkedDeque<LogEntry>()

    data class LogEntry(
        val timestampMs: Long,
        val priority: Int,
        val tag: String?,
        val message: String,
    ) {
        fun format(): String {
            val time = DATE_FORMAT.format(Date(timestampMs))
            val level = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG   -> "D"
                Log.INFO    -> "I"
                Log.WARN    -> "W"
                Log.ERROR   -> "E"
                else        -> "?"
            }
            return "$time $level/${tag ?: "App"}: $message"
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val safeMsg = com.mcw.wallet.logging.SecureLogSanitizer.sanitize(message) ?: return
        val fullMsg = if (t != null) "$safeMsg\n${t.stackTraceToString().take(300)}" else safeMsg
        logs.addLast(LogEntry(System.currentTimeMillis(), priority, tag, fullMsg))
        while (logs.size > MAX_LOGS) logs.pollFirst()
    }

    fun getAll(): List<LogEntry> = logs.toList()

    fun clear() = logs.clear()
}
