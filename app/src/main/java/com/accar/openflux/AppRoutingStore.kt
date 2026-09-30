package com.accar.openflux

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File
import java.io.RandomAccessFile
import java.io.FileNotFoundException

/** Fresh reads across Activity/VPN processes; unrelated preferences cannot overwrite this list. */
class AppRoutingStore(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "app-routing.json"))

    private fun <T> locked(block: () -> T): T = synchronized(lock) {
        RandomAccessFile(File(context.noBackupFilesDir, "app-routing.lock"), "rw").channel.use { channel ->
            val held = channel.lock()
            try { block() } finally { held.release() }
        }
    }

    fun read(): Set<String> = locked {
        stored() ?: context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
            .getStringSet(OpenFluxVpnService.EXCLUDED_APPS, emptySet()).orEmpty().toSortedSet()
    }

    fun save(packages: Collection<String>): Boolean = locked {
        val next = normalize(packages)
        val previous = stored()
        if (previous == next) return@locked false
        write(next)
        true
    }

    fun migrate() = locked {
        if (stored() == null) write(context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
            .getStringSet(OpenFluxVpnService.EXCLUDED_APPS, emptySet()).orEmpty().toSortedSet())
    }

    // AtomicFile must recover a legacy .bak after an interrupted write before
    // deciding the list is absent (especially on older supported Android).
    private fun stored(): Set<String>? = try {
        decode(String(file.readFully(), Charsets.UTF_8))
    } catch (_: FileNotFoundException) { null }

    private fun write(packages: Set<String>) {
        val bytes = JSONArray(packages.toList()).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }

    companion object {
        private val lock = Any()
        private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
        fun decode(raw: String): Set<String> {
            val values = JSONArray(raw)
            require(values.length() <= 2048) { "Слишком много исключений" }
            return normalize((0 until values.length()).map { values.getString(it) })
        }
        private fun normalize(packages: Collection<String>): Set<String> {
            require(packages.size <= 2048) { "Слишком много исключений" }
            return packages.map { it.trim().also { pkg ->
                require(pkg.length <= 255 && packagePattern.matches(pkg)) { "Некорректное имя приложения" }
            } }.toSortedSet()
        }
    }
}
