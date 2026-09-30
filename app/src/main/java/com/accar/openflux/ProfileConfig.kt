package com.accar.openflux

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object ProfileConfig {
    fun parse(rawValue: String): JSONObject {
        require(rawValue.length <= 64 * 1024) { "Конфигурация слишком большая" }
        val raw = rawValue.trim()
        val poolStart = raw.indexOf("paperflux://pool?")
        if (poolStart >= 0) {
            val uri = Uri.parse(raw.substring(poolStart).lineSequence().first().trim())
            val encoded = uri.getQueryParameter("data") ?: error("В наборе нет конфигураций")
            val data = String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            require(data.length <= 48 * 1024) { "Набор серверов слишком большой" }
            val pool = JSONObject(data)
            require(pool.optInt("v") == 1) { "Неизвестная версия набора серверов" }
            val nodes = pool.getJSONArray("profiles")
            require(nodes.length() in 2..8) { "Нужно от 2 до 8 серверов" }
            val primary = parse(nodes.getJSONObject(0).toString())
            require(!primary.has("alternatives")) { "Вложенные наборы не поддерживаются" }
            val alternatives = JSONArray()
            for (i in 1 until nodes.length()) alternatives.put(nodes.getJSONObject(i))
            primary.put("alternatives", alternatives)
            primary.put("name", pool.optString("name", primary.getString("name")).take(80))
            return parse(primary.toString())
        }
        val uriStart = raw.indexOf("paperflux://config?")
        val source = if (uriStart >= 0) {
            val uri = Uri.parse(raw.substring(uriStart).lineSequence().first().trim())
            check(uri.scheme == "paperflux" && uri.host == "config") { "Нужна ссылка paperflux://config" }
            JSONObject().put("id", uri.getQueryParameter("id")).put("token", uri.getQueryParameter("token"))
                .put("clientIp", uri.getQueryParameter("ip")).put("documentUrl", uri.getQueryParameter("doc"))
                .put("transport", uri.getQueryParameter("transport") ?: "yandex").put("server", uri.getQueryParameter("server"))
                .put("volgaUrl", uri.getQueryParameter("volga") ?: "")
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
        val volgaUrl = source.optString("volgaUrl").trim()
        check(id.matches(Regex("[1-9][0-9]*"))) { "В конфиге нет ID профиля" }
        check(token.length in 32..128) { "Нужен пароль профиля с сервера (ключ доступа), не SSH-пароль" }
        check(validResource(provider, docs)) { "Проверьте ссылку или комнаты выбранного транспорта" }
        if (volgaUrl.isNotEmpty()) {
            check(provider == "yandex" && volgaUrl.matches(Regex("https://disk\\.yandex\\.ru/i/[A-Za-z0-9_-]+")) && volgaUrl !in docs) {
                "Для Volga нужен отдельный пустой документ Яндекса"
            }
        }
        check(server.isNotBlank() && server.length <= 253 && !server.any { it.isWhitespace() || it in "/@?#\\" }) { "В конфиге неверный адрес сервера" }
        check(ip.matches(Regex("10\\.10\\.10\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])"))) { "Некорректный виртуальный IP" }
        val result = JSONObject().put("id", id).put("token", token).put("clientIp", ip)
            .put("documentUrl", docs.joinToString(",")).put("documentUrls", JSONArray(docs)).put("server", server).put("name", name).put("transport", provider)
        if (volgaUrl.isNotEmpty()) result.put("volgaUrl", volgaUrl)
        source.optJSONArray("alternatives")?.let { alternatives ->
            require(alternatives.length() in 1..7) { "Нужно от 2 до 8 серверов" }
            val seen = mutableSetOf(server.lowercase(java.util.Locale.ROOT) + "|" + id)
            val servers = mutableSetOf(server.lowercase(java.util.Locale.ROOT))
            fun resourceFamily(value: String) = if (value == "vyandex") "yandex" else value
            val resources = docs.map { resourceFamily(provider) + "|" + it }.toMutableSet()
            if (volgaUrl.isNotEmpty()) resources.add("yandex|$volgaUrl")
            val canonical = JSONArray()
            for (i in 0 until alternatives.length()) {
                val node = alternatives.getJSONObject(i)
                require(!node.has("alternatives")) { "Вложенные наборы не поддерживаются" }
                val p = parse(node.toString())
                require(seen.add(p.getString("server").lowercase(java.util.Locale.ROOT) + "|" + p.getString("id"))) { "Сервер в наборе повторяется" }
                require(servers.add(p.getString("server").lowercase(java.util.Locale.ROOT))) { "Для резерва нужны разные серверы" }
                val documents = p.getJSONArray("documentUrls")
                for (j in 0 until documents.length()) require(resources.add(resourceFamily(p.getString("transport")) + "|" + documents.getString(j))) {
                    "Серверы должны использовать отдельные документы или комнаты"
                }
                p.optString("volgaUrl").takeIf { it.isNotEmpty() }?.let { require(resources.add("yandex|$it")) {
                    "Серверы должны использовать отдельные документы Volga"
                } }
                canonical.put(p)
            }
            result.put("alternatives", canonical)
        }
        return result
    }
    fun export(profile: JSONObject): String {
        val p = parse(profile.toString())
        p.optJSONArray("alternatives")?.let { alternatives ->
            val primary = JSONObject(p.toString()).apply { remove("alternatives") }
            val nodes = JSONArray().put(primary)
            for (i in 0 until alternatives.length()) nodes.put(alternatives.getJSONObject(i))
            val bundle = JSONObject().put("v", 1).put("name", p.getString("name")).put("profiles", nodes)
            return Uri.Builder().scheme("paperflux").authority("pool").appendQueryParameter("data",
                Base64.encodeToString(bundle.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)).build().toString()
        }
        val builder = Uri.Builder().scheme("paperflux").authority("config")
            .appendQueryParameter("v", "1").appendQueryParameter("id", p.getString("id"))
            .appendQueryParameter("name", p.getString("name")).appendQueryParameter("server", p.getString("server"))
            .appendQueryParameter("ip", p.getString("clientIp")).appendQueryParameter("transport", p.getString("transport"))
            .appendQueryParameter("doc", p.getString("documentUrl")).appendQueryParameter("token", p.getString("token"))
        p.optString("volgaUrl").takeIf { it.isNotEmpty() }?.let { builder.appendQueryParameter("volga", it) }
        return builder.build().toString()
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
