package com.accar.openflux

object VpnTilePolicy {
    fun active(state: String): Boolean = state in setOf(
        "CONNECTED", "CONNECTING", "RECONNECTING", "WAITING_NETWORK", "TUN", "TRANSPORT", "AUTH", "DNS")
    fun subtitle(state: String): String = when (state) {
        "CONNECTED" -> "Подключён"
        "WAITING_NETWORK" -> "Ожидаем сеть"
        "RECONNECTING" -> "Переподключение"
        "ERROR" -> "Ошибка подключения"
        else -> if (active(state)) "Подключение" else "Отключён"
    }
}
