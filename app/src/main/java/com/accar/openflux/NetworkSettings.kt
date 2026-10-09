package com.accar.openflux

/** Only literal IPv4 DNS addresses: the tunnel currently routes IPv4. No DNS lookup. */
data class NetworkSettings(
    val dnsPrimary: String = "77.88.8.8",
    val dnsSecondary: String = "77.88.8.1",
    val mtu: Int = 1400,
    val connectTimeoutSec: Int = 15,
    val autoReconnect: Boolean = true,
    val autoConnect: Boolean = false,
    val connectionMode: String = "vpn",
) {
    fun update(key: String, value: String): NetworkSettings = when (key) {
        "dnsPrimary" -> copy(dnsPrimary = dns(value))
        "dnsSecondary" -> copy(dnsSecondary = dns(value))
        "mtu" -> copy(mtu = number(value, 576..1500))
        "connectTimeoutSec" -> copy(connectTimeoutSec = number(value, 5..120))
        "autoReconnect" -> copy(autoReconnect = boolean(value))
        "autoConnect" -> copy(autoConnect = boolean(value))
        "connectionMode" -> copy(connectionMode = value.also { require(it in setOf("vpn", "proxy")) { "Неизвестный режим подключения" } })
        else -> throw IllegalArgumentException("Неизвестная настройка")
    }

    /** Include DNS/MTU in retained TUN identity, not just the IP/exclusions. */
    fun tunnelKey(clientIp: String, exclusions: Set<String>): String =
        "$clientIp|$mtu|$dnsPrimary|$dnsSecondary|${exclusions.sorted().joinToString(",")}"

    companion object {
        fun dns(raw: String): String {
            val value = raw.trim()
            val parts = value.split('.')
            require(parts.size == 4 && parts.all { it.length in 1..3 && it.all { c -> c in '0'..'9' } && it.toInt() in 0..255 }) {
                "Укажите IPv4-адрес DNS, например 1.1.1.1"
            }
            val octets = parts.map { it.toInt() }
            require(octets[0] in 1..223 && octets[0] != 127) { "Укажите адрес доступного DNS-сервера" }
            return octets.joinToString(".")
        }
        private fun number(raw: String, range: IntRange): Int = raw.toIntOrNull().also {
            require(it != null && it in range) { "Значение должно быть от ${range.first} до ${range.last}" }
        }!!
        private fun boolean(raw: String): Boolean {
            require(raw == "true" || raw == "false") { "Некорректное значение переключателя" }
            return raw == "true"
        }
    }
}
