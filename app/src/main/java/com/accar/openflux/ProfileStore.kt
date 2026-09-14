package com.accar.openflux

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One encrypted, atomically replaced profile store; only the Activity writes it. */
class ProfileStore(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "profiles-v2.bin"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("paperflux-profiles-v2", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("paperflux-profiles-v2", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): JSONObject {
        if (!file.baseFile.exists()) return JSONObject().put("activeId", "").put("profiles", JSONArray())
        val bytes = file.readFully()
        require(bytes.size >= 28) { "Хранилище профилей повреждено" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8))
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
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") == data.optString("activeId")) return rows.getJSONObject(i)
        return null
    }
    fun migrate() {
        if (file.baseFile.exists()) return
        val prefs = context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("profile_id", "").orEmpty()
        val token = prefs.getString("profile_token", "").orEmpty()
        if (id.isBlank() || token.isBlank()) return
        save(JSONObject().put("id", id).put("token", token)
            .put("name", prefs.getString("profile_name", "PaperFlux"))
            .put("server", prefs.getString("server_ip", ""))
            .put("documentUrl", prefs.getString("document", ""))
            .put("clientIp", prefs.getString("client_ip", "")))
    }
    fun publicState(): String {
        val data = read()
        val rows = data.getJSONArray("profiles")
        for (i in 0 until rows.length()) { rows.getJSONObject(i).remove("token") }
        return data.toString()
    }
    fun save(profile: JSONObject) {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val next = JSONArray()
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") != profile.getString("id")) next.put(rows.getJSONObject(i))
        next.put(profile)
        data.put("profiles", next).put("activeId", profile.getString("id"))
        write(data)
        mirror(profile)
    }
    fun select(id: String) {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val profile = (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.getString("id") == id }
            ?: error("Профиль не найден")
        write(data.put("activeId", id))
        mirror(profile)
    }
    fun update(id: String, name: String, server: String, documentUrl: String, clientIp: String, replacementToken: String?) {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val next = JSONArray()
        var updated: JSONObject? = null
        for (i in 0 until rows.length()) {
            val current = rows.getJSONObject(i)
            if (current.getString("id") != id) {
                next.put(current)
                continue
            }
            val value = JSONObject(current.toString())
                .put("name", name).put("server", server)
                .put("documentUrl", documentUrl).put("clientIp", clientIp)
            replacementToken?.takeIf { it.isNotBlank() }?.let { value.put("token", it) }
            updated = value
            next.put(value)
        }
        require(updated != null) { "Профиль не найден" }
        data.put("profiles", next)
        write(data)
        if (data.optString("activeId") == id) mirror(updated)
    }
    fun delete(id: String) {
        val data = read()
        val rows = data.getJSONArray("profiles")
        val next = JSONArray()
        for (i in 0 until rows.length()) if (rows.getJSONObject(i).getString("id") != id) next.put(rows.getJSONObject(i))
        data.put("profiles", next)
        if (data.optString("activeId") == id) data.put("activeId", if (next.length() > 0) next.getJSONObject(0).getString("id") else "")
        write(data)
        mirror(active())
    }
    private fun mirror(profile: JSONObject?) {
        // Compatibility fields contain no secret. Native startup reads active() atomically.
        check(context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE).edit()
            .putString("document", profile?.optString("documentUrl").orEmpty())
            .putString("profile_id", profile?.optString("id").orEmpty())
            .putString("profile_name", profile?.optString("name").orEmpty())
            .putString("client_ip", profile?.optString("clientIp").orEmpty())
            .putString("server_ip", profile?.optString("server").orEmpty())
            .remove("profile_token").commit()) { "Не удалось сохранить выбор профиля" }
    }
}
