package com.accar.openflux

/** Monotonic active-network budget with a bounded human-verification pause. */
internal class ConnectionWaitBudget(private val started: Long, private val budget: Long) {
    private var last = started
    private var spent = 0L
    fun expired(now: Long, verifying: Boolean): Boolean {
        if (!verifying) spent += (now - last).coerceAtLeast(0)
        last = now
        return spent >= budget || now - started >= 1_800_000L
    }
}

internal object TunnelHealthDiagnostics {
    private val failure = Regex("resolver=([0-9a-fA-F:.\\[\\]]+) stage=(tcp|dns) reason=(timeout|failed)")
    fun message(line: String): String? {
        if (!line.contains("TUNNEL_PROBE_FAILED:")) return null
        val details = failure.findAll(line).take(2).map {
            val stage = if (it.groupValues[2] == "tcp") "TCP-соединение" else "ответ DNS"
            val reason = if (it.groupValues[3] == "timeout") "тайм-аут" else "ошибка"
            "${it.groupValues[1]}: $stage — $reason"
        }.toList()
        return if (details.isEmpty()) "Проверка DNS/TCP через туннель не прошла" else details.joinToString("; ")
    }
}
