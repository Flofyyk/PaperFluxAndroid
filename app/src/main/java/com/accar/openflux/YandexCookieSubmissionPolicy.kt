package com.accar.openflux

internal object YandexCookieSubmissionPolicy {
    fun shouldSend(previous: Map<String, String>?, current: Map<String, String>,
        forceRetry: Boolean, elapsedMs: Long): Boolean = current.isNotEmpty() &&
        (previous == null || (elapsedMs >= 5000 && (forceRetry || previous != current)))
}
