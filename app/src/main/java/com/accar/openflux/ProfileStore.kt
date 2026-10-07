package com.accar.openflux

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One encrypted, atomically replaced profile store; only the Activity writes it. */
class ProfileStore(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "profiles-v2.bin"))
    private fun <T> withStoreLock(action: () -> T): T = synchronized(storeLock) {
        val lockFile = File(context.noBackupFilesDir, "profiles-v2.lock")
        val path = lockFile.absolutePath
        val held = heldLocks.get()!!
        if (path in held) return@synchronized action()
        check(lockFile.parentFile!!.isDirectory || lockFile.parentFile!!.mkdirs())
        RandomAccessFile(lockFile, "rw").use { handle ->
            handle.channel.lock().use {
                held.add(path)
                try { action() } finally { held.remove(path) }
            }
        }
    }
    private fun storedBytes(): ByteArray? = try {
        // openRead also restores a legacy .bak after an interrupted replacement.
        // Checking baseFile.exists() first would incorrectly treat it as empty.
        file.readFully()
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
        null
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("paperflux-profiles-v2", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("paperflux-profiles-v2", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject = withStoreLock {
        val bytes = storedBytes() ?: return@withStoreLock JSONObject().put("activeId", "").put("profiles", JSONArray())
        require(bytes.size >= 28) { "Хранилище профилей повреждено" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val data = JSONObject(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
        val rows = data.getJSONArray("profiles")
        val oldActive = data.optString("activeId")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            row.put("key", profileKey(row))
            if (row.getString("id") == oldActive) data.put("activeId", row.getString("key"))
        }
        if (rows.length() > 0 && (0 until rows.length()).none {
                rows.getJSONObject(it).getString("key") == data.optString("activeId")
            }) {
            // An older selection key can survive an upgrade or profile replacement.
            // There is no explicit "none selected" action, so retain a usable profile.
            data.put("activeId", rows.getJSONObject(0).getString("key"))
        }
        data
    }
    private fun write(data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun active(): JSONObject? {
        val data = read()
        val rows = data.getJSONArray("profiles")
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("key") == data.optString("activeId")) return rows.getJSONObject(i)
        return null
    }
    fun find(id: String): JSONObject {
        val rows = read().getJSONArray("profiles")
        val values = (0 until rows.length()).map { rows.getJSONObject(it) }
        return values.firstOrNull { it.getString("key") == id }
            ?: values.filter { it.getString("id") == id }.singleOrNull() ?: error("Профиль не найден")
    }
    private fun documents(profile: JSONObject): JSONArray {
        val source = profile.optJSONArray("documentUrls")
        val result = JSONArray()
        val seen = linkedSetOf<String>()
        if (source != null) for (i in 0 until source.length()) source.optString(i).trim().takeIf { it.isNotEmpty() }?.let { seen += it }
        if (seen.isEmpty()) profile.optString("documentUrl").split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { seen += it }
        seen.forEach { result.put(it) }
        return result
    }
    private fun canonical(profile: JSONObject): JSONObject {
        val value = JSONObject(profile.toString())
        val docs = documents(value)
        value.put("documentUrls", docs)
        value.put("documentUrl", (0 until docs.length()).joinToString(",") { docs.getString(it) })
        value.put("transport", value.optString("transport", "yandex").ifBlank { "yandex" })
        value.put("key", profileKey(value))
        return value
    }
    private fun profileKey(profile: JSONObject) = profile.optString("server").trim().lowercase(java.util.Locale.ROOT) + "|" + profile.getString("id")
    fun migrate() = withStoreLock {
        if (storedBytes() != null) return@withStoreLock
        val prefs = context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("profile_id", "").orEmpty()
        val token = prefs.getString("profile_token", "").orEmpty()
        if (id.isBlank() || token.isBlank()) return@withStoreLock
        save(JSONObject().put("id", id).put("token", token)
            .put("name", prefs.getString("profile_name", "PaperFlux"))
            .put("server", prefs.getString("server_ip", ""))
            .put("documentUrl", prefs.getString("document", ""))
            .put("clientIp", prefs.getString("client_ip", "")))
    }
    fun publicState(): String {
        val data = read()
        val rows = data.getJSONArray("profiles")
        for (i in 0 until rows.length()) {
            rows.getJSONObject(i).apply {
                remove("token")
                val server = optString("server").trim().removePrefix("[").removeSuffix("]")
                val countries = ProfileCountryCache(context)
                (countries.forServer(server) ?: countries.forAddress(server))?.let { put("countryCode", it) }
                put("serverCount", 1 + (optJSONArray("alternatives")?.length() ?: 0))
                // Never expose standby credentials to the WebView either.
                remove("alternatives")
                put("profileId", getString("id")); put("id", getString("key"))
            }
        }
        return data.toString()
    }
    fun save(profile: JSONObject, replacing: String? = null) = withStoreLock {
        val canonical = canonical(profile)
        val data = read()
        val rows = data.getJSONArray("profiles")
        val next = JSONArray()
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("key") != canonical.getString("key") && rows.getJSONObject(i).getString("key") != replacing) next.put(rows.getJSONObject(i))
        next.put(canonical)
        data.put("profiles", next).put("activeId", canonical.getString("key"))
        write(data)
        mirror(canonical)
    }
    fun select(id: String) = withStoreLock {
        val data = read()
        val profile = find(id)
        write(data.put("activeId", profile.getString("key")))
        mirror(profile)
    }
    fun update(id: String, name: String, server: String, documentUrl: String, clientIp: String, provider: String, replacementToken: String?) = withStoreLock {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val next = JSONArray()
        var updated: JSONObject? = null
        for (i in 0 until rows.length()) {
            val current = rows.getJSONObject(i)
            if (current.getString("key") != id) {
                next.put(current)
                continue
            }
            val edited = JSONObject(current.toString()).put("name", name).put("server", server)
            if (server != current.optString("server") || documentUrl != current.optString("documentUrl") ||
                clientIp != current.optString("clientIp") || provider != current.optString("transport") || !replacementToken.isNullOrBlank()) {
                // Editing a node's credentials must not silently retain an old standby group.
                edited.remove("alternatives")
            }
            edited.remove("documentUrls")
            edited.put("documentUrl", documentUrl).put("clientIp", clientIp).put("transport", provider)
            val value = canonical(edited)
            replacementToken?.takeIf { it.isNotBlank() }?.let { value.put("token", it) }
            updated = value
            next.put(value)
        }
        require(updated != null) { "Профиль не найден" }
        data.put("profiles", next)
        val wasActive = data.optString("activeId") == id
        if (wasActive) data.put("activeId", updated!!.getString("key"))
        write(data)
        if (wasActive) mirror(updated)
    }
    fun delete(id: String) = withStoreLock {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val key = find(id).getString("key")
        val next = JSONArray()
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("key") != key) next.put(rows.getJSONObject(i))
        data.put("profiles", next)
        if (data.optString("activeId") == key) data.put("activeId", if (next.length() > 0) next.getJSONObject(0).getString("key") else "")
        write(data)
        mirror(active())
    }
    private fun mirror(profile: JSONObject?) {
        // Compatibility fields contain no secret. Native startup reads active() atomically.
        check(context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE).edit()
            .putString("document", profile?.let { documents(it).let { docs -> (0 until docs.length()).joinToString(",") { i -> docs.getString(i) } } }.orEmpty())
            .putString("profile_id", profile?.optString("id").orEmpty())
            .putString("profile_name", profile?.optString("name").orEmpty())
            .putString("client_ip", profile?.optString("clientIp").orEmpty())
            .putString("server_ip", profile?.optString("server").orEmpty())
            .remove("profile_token").commit()) { "Не удалось сохранить выбор профиля" }
    }
    companion object {
        private val storeLock = Any()
        private val heldLocks = object : ThreadLocal<MutableSet<String>>() {
            override fun initialValue(): MutableSet<String> = mutableSetOf()
        }
    }
}
