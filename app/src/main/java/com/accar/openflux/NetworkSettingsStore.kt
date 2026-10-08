package com.accar.openflux

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile

/** Synchronous atomic commits and fresh reads in Activity and :vpn processes. */
class NetworkSettingsStore(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "network-settings.json"))

    private fun <T> locked(block: () -> T): T = synchronized(lock) {
        RandomAccessFile(File(context.noBackupFilesDir, "network-settings.lock"), "rw").channel.use { channel ->
            val held = channel.lock()
            try { block() } finally { held.release() }
        }
    }

    fun read(): NetworkSettings = locked { stored() }
    fun update(key: String, value: String): NetworkSettings = locked {
        stored().update(key, value).also { write(it) }
    }
    fun reset(): NetworkSettings = locked { NetworkSettings().also { write(it) } }

    private fun stored(): NetworkSettings {
        val raw = try { String(file.readFully(), Charsets.UTF_8) } catch (_: FileNotFoundException) {
            check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) { "Не удалось прочитать сохранённые настройки" }
            return NetworkSettings(autoReconnect = context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(OpenFluxVpnService.AUTO_RECONNECT, true))
        }
        val json = JSONObject(raw)
        var settings = NetworkSettings()
        for (key in keys) if (json.has(key)) settings = settings.update(key, json.get(key).toString())
        return settings
    }

    private fun write(settings: NetworkSettings) {
        val output = file.startWrite()
        try {
            output.write(json(settings).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output) // fsync before the bridge reports success
        } catch (error: Exception) { file.failWrite(output); throw error }
    }

    companion object {
        private val lock = Any()
        private val keys = listOf("dnsPrimary", "dnsSecondary", "mtu", "connectTimeoutSec", "autoReconnect", "autoConnect")
        fun json(settings: NetworkSettings): JSONObject = JSONObject()
            .put("dnsPrimary", settings.dnsPrimary).put("dnsSecondary", settings.dnsSecondary)
            .put("mtu", settings.mtu).put("connectTimeoutSec", settings.connectTimeoutSec)
            .put("autoReconnect", settings.autoReconnect).put("autoConnect", settings.autoConnect)
    }
}
