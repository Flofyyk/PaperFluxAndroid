package com.accar.openflux

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object ProfileConfig {
    fun parse(rawValue: String): JSONObject {
        require(rawValue.length <= 64 * 1024) { "Конфигурация слишком большая" }
        val raw = rawValue.trim()
        val uriStart = raw.indexOf("paperflux://config?")
        val source = if (uriStart >= 0) {
            val uri = Uri.parse(raw.substring(uriStart).lineSequence().first().trim())
            check(uri.scheme == "paperflux" && uri.host == "config") { "Нужна ссылка paperflux://config" }
            JSONObject().put("id", uri.getQueryParameter("id")).put("token", uri.getQueryParameter("token"))
                .put("clientIp", uri.getQueryParameter("ip")).put("documentUrl", uri.getQueryParameter("doc"))
                .put("transport", uri.getQueryParameter("transport") ?: "yandex").put("server", uri.getQueryParameter("server"))
                .put("name", uri.getQueryParameter("name") ?: "PaperFlux")
        } else JSONObject(raw)
        val id = source.optString("id").trim()
        val token = source.optString("token").trim()
        val docs = source.optJSONArray("documentUrls")?.let { values ->
            (0 until values.length()).map { values.optString(it).trim() }.filter { it.isNotEmpty() }.distinct()
        } ?: documents(source.optString("documentUrl", source.optString("doc")))
        val ip = source.optString("clientIp", source.optString("ip")).trim()
        val server = source.optString("server").trim()
        val name = source.optString("name", "PaperFlux").trim().ifBlank { "PaperFlux" }
        val provider = source.optString("transport", "yandex").ifBlank { "yandex" }
        check(id.matches(Regex("[1-9][0-9]*"))) { "В конфиге нет ID профиля" }
        check(token.length in 32..128) { "Нужен пароль профиля с сервера (ключ доступа), не SSH-пароль" }
        check(validResource(provider, docs)) { "Проверьте ссылку или комнаты выбранного транспорта" }
        check(server.isNotBlank() && server.length <= 253 && !server.any { it.isWhitespace() || it in "/@?#\\" }) { "В конфиге неверный адрес сервера" }
        check(ip.matches(Regex("10\\.10\\.10\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])"))) { "Некорректный виртуальный IP" }
        return JSONObject().put("id", id).put("token", token).put("clientIp", ip)
            .put("documentUrl", docs.joinToString(",")).put("documentUrls", JSONArray(docs)).put("server", server).put("name", name).put("transport", provider)
    }
    fun export(profile: JSONObject): String {
        val p = parse(profile.toString())
        return Uri.Builder().scheme("paperflux").authority("config")
            .appendQueryParameter("v", "1").appendQueryParameter("id", p.getString("id"))
            .appendQueryParameter("name", p.getString("name")).appendQueryParameter("server", p.getString("server"))
            .appendQueryParameter("ip", p.getString("clientIp")).appendQueryParameter("transport", p.getString("transport"))
            .appendQueryParameter("doc", p.getString("documentUrl")).appendQueryParameter("token", p.getString("token")).build().toString()
    }
    fun documents(value: String) = value.split(Regex("[;,\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun validDocuments(docs: List<String>) = docs.isNotEmpty() && docs.size <= 2 && docs.all { value ->
        runCatching { val uri = Uri.parse(value); uri.scheme == "https" && uri.host == "disk.yandex.ru" && uri.userInfo == null && uri.port == -1 && !uri.path.isNullOrBlank() }.getOrDefault(false)
    }
    fun validResource(provider: String, docs: List<String>): Boolean = when (provider) {
        "yandex" -> validDocuments(docs)
        "vyandex" -> docs.size == 1 && validDocuments(docs)
        "mailru" -> docs.size == 1 && runCatching { val u = Uri.parse(docs[0]); u.scheme == "https" && u.host == "cloud.mail.ru" && u.path?.startsWith("/public/") == true && u.userInfo == null && u.port == -1 }.getOrDefault(false)
        "cupsonline" -> docs.size == 1 && runCatching {
            val rooms = JSONArray(String(Base64.decode(docs[0], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
            rooms.length() in 1..8 && (0 until rooms.length()).all { rooms.getString(it).matches(Regex("[0-9a-fA-F-]{36}")) }
        }.getOrDefault(false)
        else -> false
    }
}
