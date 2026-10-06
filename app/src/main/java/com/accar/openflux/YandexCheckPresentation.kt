package com.accar.openflux

/** A working VPN and a provider's pending verification are independent states. */
internal object YandexCheckPresentation {
    private val waiting = Regex("Яндекс.*(?:требует|требуется|ожидает).*(?:провер|подтвержд)", RegexOption.IGNORE_CASE)
    fun isWaiting(message: String): Boolean =
        waiting.containsMatchIn(message) ||
            message.startsWith("Результат передан. Проверяем доступ к Яндексу")

    const val HEALTHY = "Шифрованный туннель: DNS и TCP подтверждены"
    fun required(carrier: String, side: String, tunnelReady: Boolean) = if (tunnelReady)
        "Яндекс: дополнительный канал ожидает проверки — $carrier, $side. VPN работает"
    else "Яндекс: требуется проверка — $carrier, $side"
    fun accepted(carrier: String, side: String) = "Доступ к документу подтверждён — $carrier, $side"

    // A recovered alternative lane is not permission to discard a completed
    // browser check before its cookies have been handed to the native process.
    fun shouldDeferAutomaticDismissal(checkingPage: Boolean, submitted: Boolean,
        hasRequest: Boolean, pageComplete: Boolean, pageFailed: Boolean, checkpoint: Boolean): Boolean =
        !submitted && (checkingPage || (hasRequest && pageComplete && !pageFailed && !checkpoint))
}
