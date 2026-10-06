package com.accar.openflux

/** Native diagnostics may contain private links even when they have no token query. */
internal object NativeLogRedactor {
    private val url = Regex("(?i)\\b(?:https?|wss?)://[^\\s<>\"']+")
    private val secret = Regex("(?i)([\"']?\\b(?:token|profile-token|access_token|sign|cookie|password|authorization)[\"']?\\s*[:=]\\s*)(?:\"[^\"]*\"|'[^']*'|[^\\s&,;}\\]]+)")
    private val jwt = Regex("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b")
    private val ipv4 = Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w.])")

    fun redact(value: String): String = value
        .replace(url, "<ссылка скрыта>")
        .replace(secret) { it.groupValues[1] + "<скрыто>" }
        .replace(jwt, "<ключ скрыт>")
        .replace(ipv4, "<IP скрыт>")
        .take(240)
}
