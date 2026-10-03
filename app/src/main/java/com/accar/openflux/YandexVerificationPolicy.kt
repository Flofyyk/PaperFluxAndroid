package com.accar.openflux

import java.net.URI
import java.util.Locale

/** Verification-only policy; it does not widen profile/document import rules. */
internal object YandexVerificationPolicy {
    private val roots = listOf("yandex.ru", "yandex.com", "yandex.by", "yandex.kz", "yandex.uz", "yandex.com.tr")

    fun isCheckpoint(raw: String): Boolean = runCatching {
        if (!allows(raw)) return true
        val uri = URI(raw)
        val host = uri.host.lowercase(Locale.ROOT)
        val path = uri.path.orEmpty().lowercase(Locale.ROOT)
        host == "smartcaptcha.yandexcloud.net" || host.startsWith("passport.") ||
            host.startsWith("captcha.") || host.startsWith("smartcaptcha.") || path.contains("captcha")
    }.getOrDefault(true)

    fun carrierLabel(name: String): String = when (name) {
        "yandex-1" -> "документ 1"
        "yandex-2" -> "документ 2"
        "volga-1", "vyandex-1" -> "резерв Volga"
        else -> "Яндекс"
    }

    fun allows(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            (host == "smartcaptcha.yandexcloud.net" || roots.any { host == it || host.endsWith(".$it") })
    }.getOrDefault(false)

    fun cookieDomain(raw: String): String? {
        if (!allows(raw)) return null
        val host = URI(raw).host.lowercase(Locale.ROOT)
        return roots.firstOrNull { host == it || host.endsWith(".$it") }
    }

    /** CookieManager omits domain/path. Prefer final document URL, then initial
     * URL, then root fallbacks. Within a header, keep the first same-name cookie
     * (the more specific path is normally returned first). Never let a root
     * overwrite the document cookie merely because it was visited last. */
    fun collect(start: String, final: String, read: (String) -> String?): Map<String, String> {
        val urls = linkedSetOf(final, start)
        for (raw in listOf(final, start)) {
            if (!allows(raw)) continue
            val host = URI(raw).host.lowercase(Locale.ROOT)
            urls.add("https://$host/")
            roots.firstOrNull { host == it || host.endsWith(".$it") }?.let { urls.add("https://$it/") }
        }
        urls.addAll(listOf("https://disk.yandex.ru/", "https://docs.yandex.ru/", "https://yandex.ru/"))
        val values = linkedMapOf<String, String>()
        val scope = cookieDomain(final) ?: cookieDomain(start)
        for (url in urls.filter { allows(it) && (scope == null || cookieDomain(it) == scope) }) {
            for (part in read(url).orEmpty().split(';')) {
                val split = part.indexOf('=')
                if (split <= 0) continue
                val name = part.substring(0, split).trim()
                val value = part.substring(split + 1).trim()
                if (name.isNotEmpty() && name.none { it.isWhitespace() || it == ',' } &&
                    value.none { it == '\r' || it == '\n' }) values.putIfAbsent(name, value)
            }
        }
        return values
    }
}
