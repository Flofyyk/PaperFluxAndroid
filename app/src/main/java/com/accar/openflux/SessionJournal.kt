package com.accar.openflux

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Short, redacted event history for the current VPN session.
 *
 * The VPN service runs independently from the Activity/WebView, so keeping
 * this in an atomic file makes the journal survive closing and reopening the
 * app without treating it as long-term diagnostic telemetry.
 */
object SessionJournal {
    private const val FILE_NAME = "paperflux-session-journal.json"
    private const val MAX_EVENTS = 180
    private const val DUPLICATE_WINDOW_MS = 5_000L
    private val sequence = AtomicLong()

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    @Synchronized
    fun begin(context: Context) = write(context, JSONArray())

    @Synchronized
    fun clear(context: Context) = write(context, JSONArray())

    @Synchronized
    fun append(context: Context, message: String, level: String, category: String, stage: String) {
        if (message.isBlank()) return
        val now = System.currentTimeMillis()
        val events = readEvents(context)
        if (events.length() > 0) {
            val latest = events.optJSONObject(0)
            if (latest?.optString("message") == message && now - latest.optLong("at") < DUPLICATE_WINDOW_MS) return
        }
        val next = JSONArray()
        next.put(JSONObject()
            .put("id", "$now-${sequence.incrementAndGet()}")
            .put("at", now)
            .put("message", message.take(300))
            .put("level", level)
            .put("category", category)
            .put("stage", stage.take(48)))
        for (index in 0 until minOf(events.length(), MAX_EVENTS - 1)) next.put(events.getJSONObject(index))
        write(context, next)
    }

    @Synchronized
    fun read(context: Context): String = JSONObject().put("events", readEvents(context)).toString()

    private fun readEvents(context: Context): JSONArray = try {
        file(context).openRead().use { input ->
            JSONObject(input.readBytes().toString(Charsets.UTF_8)).optJSONArray("events") ?: JSONArray()
        }
    } catch (_: Exception) {
        JSONArray()
    }

    private fun write(context: Context, events: JSONArray) {
        val atomic = file(context)
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(JSONObject().put("events", events).toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
        }
    }
}
