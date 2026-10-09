package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class YandexCheckPresentationTest {
    @Test fun returnToAppNeedsNativeHandoffNotJustDocumentLoad() {
        assertTrue(YandexCheckPresentation.canReturnAfterHandoff(true, true, true, false, false))
        assertFalse(YandexCheckPresentation.canReturnAfterHandoff(false, true, true, false, false))
        assertFalse(YandexCheckPresentation.canReturnAfterHandoff(true, false, true, false, false))
        assertFalse(YandexCheckPresentation.canReturnAfterHandoff(true, true, false, false, false))
        assertFalse(YandexCheckPresentation.canReturnAfterHandoff(true, true, true, true, false))
        assertFalse(YandexCheckPresentation.canReturnAfterHandoff(true, true, true, false, true))
    }
    @Test fun bothVerificationPhrasesAndCookieDeliveryAreWarnings() {
        for (message in listOf("Яндекс: требуется проверка — документ 2, телефон",
            "Яндекс требует подтверждение доступа на сервере",
            "Результат передан. Проверяем доступ к Яндексу")) assertTrue(YandexCheckPresentation.isWaiting(message))
    }
    @Test fun healthyTunnelIsNotAWarning() {
        assertFalse(YandexCheckPresentation.isWaiting("Шифрованный туннель: DNS и TCP подтверждены"))
        assertFalse(YandexCheckPresentation.isWaiting("Защищённый туннель подтверждён. DNS и TCP готовы"))
    }
    @Test fun auxiliaryCheckDoesNotClaimTheWorkingTunnelIsBlocked() {
        val waiting = YandexCheckPresentation.required("документ 2", "телефон", false)
        val auxiliary = YandexCheckPresentation.required("документ 2", "телефон", true)
        assertTrue(waiting.contains("требуется проверка"))
        assertTrue(auxiliary.contains("дополнительный канал"))
        assertTrue(auxiliary.contains("VPN работает"))
        assertTrue(YandexCheckPresentation.isWaiting(auxiliary))
        assertFalse(YandexCheckPresentation.isWaiting(YandexCheckPresentation.HEALTHY))
        // Cookie delivery is not proof of provider acceptance.
        val accepted = YandexCheckPresentation.accepted("резерв Volga", "VPS")
        assertTrue(accepted.contains("резерв Volga, VPS"))
        assertFalse(accepted.contains("пройдена"))
        assertFalse(YandexCheckPresentation.isWaiting(accepted))
    }
    @Test fun aCompletedOrInFlightBrowserCheckIsNotDiscardedOnTunnelRecovery() {
        fun defer(checking: Boolean = false, submitted: Boolean = false,
            request: Boolean = true, complete: Boolean = true, failed: Boolean = false,
            checkpoint: Boolean = false) = YandexCheckPresentation.shouldDeferAutomaticDismissal(
                checking, submitted, request, complete, failed, checkpoint)
        assertTrue(defer())
        assertTrue(defer(checking = true, complete = false))
        assertFalse(defer(submitted = true))
        assertFalse(defer(request = false))
        assertFalse(defer(complete = false))
        assertFalse(defer(failed = true))
        assertFalse(defer(checkpoint = true))
    }
}
