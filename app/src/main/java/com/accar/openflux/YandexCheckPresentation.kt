package com.accar.openflux

/** A working VPN and a provider's pending verification are independent states. */
internal object YandexCheckPresentation {
    private val waiting = Regex("Яндекс.*(?:требует|требуется).*(?:провер|подтвержд)", RegexOption.IGNORE_CASE)
    fun isWaiting(message: String): Boolean =
        waiting.containsMatchIn(message) ||
            message.startsWith("Результат передан. Проверяем доступ к Яндексу")
}
