package com.accar.openflux

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** SharedPreferences is cached separately in :vpn and Activity. Use a fresh
 * atomic file for cross-process status, without persisting journal secrets. */
object TunnelSnapshot {
    @Synchronized fun write(context: Context, state: String, detail: String) {
        val file = AtomicFile(File(context.noBackupFilesDir, "tunnel-state.json"))
        // Keep the session counters while the service changes only its
        // connection state. Activity and :vpn are separate processes, so
        // SharedPreferences is not a reliable source of live statistics.
        val data = load(file).put("state", state).put("detail", detail).put("elapsed", SystemClock.elapsedRealtime())
        write(file, data)
    }
    @Synchronized fun startSession(context: Context) {
        val file = AtomicFile(File(context.noBackupFilesDir, "tunnel-state.json"))
        val data = load(file)
            .put("rxBytes", 0L).put("txBytes", 0L).put("ping", 0L)
            .put("sessionStartedElapsed", SystemClock.elapsedRealtime())
        write(file, data)
    }
    @Synchronized fun updateStats(context: Context, rxBytes: Long, txBytes: Long, ping: Long) {
        val file = AtomicFile(File(context.noBackupFilesDir, "tunnel-state.json"))
        val data = load(file)
            .put("rxBytes", rxBytes.coerceAtLeast(0L))
            .put("txBytes", txBytes.coerceAtLeast(0L))
            .put("ping", ping.coerceAtLeast(0L))
        write(file, data)
    }
    private fun load(file: AtomicFile): JSONObject = runCatching { JSONObject(String(file.readFully())) }.getOrElse { JSONObject() }
    private fun write(file: AtomicFile, data: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(data.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun read(context: Context): JSONObject = runCatching {
        val data = JSONObject(String(AtomicFile(File(context.noBackupFilesDir, "tunnel-state.json")).readFully()))
        val alive = context.getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses?.any { it.processName == "${context.packageName}:vpn" } == true
        if (!alive && data.optString("state") != "ERROR") {
            data.put("state", "DISCONNECTED").put("detail", "Туннель отключён")
        }
        val started = data.optLong("sessionStartedElapsed", 0L)
        if (started > 0L) data.put("durationSec", ((SystemClock.elapsedRealtime() - started) / 1000L).coerceAtLeast(0L))
        data
    }.getOrElse { JSONObject().put("state", "DISCONNECTED").put("detail", "Туннель отключён") }
}
