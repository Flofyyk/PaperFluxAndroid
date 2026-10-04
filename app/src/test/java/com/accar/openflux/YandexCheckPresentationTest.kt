package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class YandexCheckPresentationTest {
    @Test fun bothVerificationPhrasesAndCookieDeliveryAreWarnings() {
        for (message in listOf("Яндекс: требуется проверка — документ 2, телефон",
            "Яндекс требует подтверждение доступа на сервере",
            "Результат передан. Проверяем доступ к Яндексу")) assertTrue(YandexCheckPresentation.isWaiting(message))
    }
    @Test fun healthyTunnelIsNotAWarning() {
        assertFalse(YandexCheckPresentation.isWaiting("Шифрованный туннель: DNS и TCP подтверждены"))
        assertFalse(YandexCheckPresentation.isWaiting("Защищённый туннель подтверждён. DNS и TCP готовы"))
    }
}
