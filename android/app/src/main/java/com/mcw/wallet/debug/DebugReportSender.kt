package com.mcw.wallet.debug

import android.content.Context
import android.os.Build
import com.mcw.wallet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Packages and sends a debug report to the backend.
 * Returns a URL to view the report.
 *
 * Report includes:
 * - Device info (model, Android version, app version)
 * - Performance metrics (timings per operation)
 * - Recent log entries (last 500, sanitized)
 * - Timestamp
 *
 * Does NOT include: private keys, mnemonics, addresses (sanitized by DebugLogTree).
 */
object DebugReportSender {

    private const val ENDPOINT = "https://mcw2.wpmix.net/debug/upload"
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class SendResult(
        val success: Boolean,
        val url: String? = null,
        val error: String? = null,
    )

    /**
     * Build and send the debug report.
     * @param logTree the DebugLogTree instance planted in Application
     * @param context Android context for device info
     * @return SendResult with URL on success
     */
    suspend fun send(logTree: DebugLogTree, context: Context): SendResult =
        withContext(Dispatchers.IO) {
            try {
                val report = buildReport(logTree, context)
                val body = report.toString(2)
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .post(body)
                    .header("User-Agent", "MCW-Android/${BuildConfig.VERSION_NAME}")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext SendResult(
                        success = false,
                        error = "Server error: ${response.code}"
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext SendResult(success = false, error = "Empty response")

                val json = JSONObject(responseBody)
                val url = json.optString("url")
                if (url.isBlank()) {
                    return@withContext SendResult(success = false, error = "No URL in response")
                }

                SendResult(success = true, url = url)
            } catch (e: Exception) {
                SendResult(success = false, error = e.message ?: "Unknown error")
            }
        }

    private fun buildReport(logTree: DebugLogTree, context: Context): JSONObject {
        val report = JSONObject()

        // Meta
        report.put("timestamp", DATE_FORMAT.format(Date()))
        report.put("app_version", BuildConfig.VERSION_NAME)
        report.put("app_id", BuildConfig.APPLICATION_ID)
        report.put("build_type", BuildConfig.BUILD_TYPE)

        // Device info
        val device = JSONObject()
        device.put("manufacturer", Build.MANUFACTURER)
        device.put("model", Build.MODEL)
        device.put("android_version", Build.VERSION.RELEASE)
        device.put("sdk_int", Build.VERSION.SDK_INT)
        device.put("available_memory_mb", getAvailableMemoryMb(context))
        report.put("device", device)

        // Performance metrics
        val perfSummary = PerformanceTracker.getSummary()
        val perf = JSONObject()
        perfSummary.forEach { (op, summary) ->
            val opJson = JSONObject()
            opJson.put("count", summary.count)
            opJson.put("avg_ms", summary.avgMs)
            opJson.put("min_ms", summary.minMs)
            opJson.put("max_ms", summary.maxMs)
            opJson.put("success_rate", String.format("%.0f%%", summary.successRate * 100))
            perf.put(op, opJson)
        }
        report.put("performance", perf)

        // Recent perf entries (raw, last 50)
        val recentPerf = JSONArray()
        PerformanceTracker.getAll().take(50).forEach { entry ->
            val e = JSONObject()
            e.put("op", entry.operation)
            e.put("ms", entry.durationMs)
            e.put("ok", entry.success)
            if (entry.meta != null) e.put("meta", entry.meta)
            recentPerf.put(e)
        }
        report.put("perf_recent", recentPerf)

        // Log entries
        val logsArray = JSONArray()
        logTree.getAll().takeLast(300).forEach { entry ->
            logsArray.put(entry.format())
        }
        report.put("logs", logsArray)

        // Instructions for Claude
        report.put("_claude_hint",
            "Performance bottlenecks: check 'performance' object for high avg_ms. " +
            "Errors: grep 'E/' in logs array. " +
            "Slow ops (>500ms) are a problem. " +
            "balance_fetch_* ops should all complete in parallel (similar timestamps in perf_recent)."
        )

        return report
    }

    private fun getAvailableMemoryMb(context: Context): Long {
        val mi = android.app.ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.getMemoryInfo(mi)
        return mi.availMem / 1024 / 1024
    }
}
