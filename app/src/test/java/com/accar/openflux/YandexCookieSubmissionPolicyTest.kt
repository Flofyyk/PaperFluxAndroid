package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class YandexCookieSubmissionPolicyTest {
    private val initial = mapOf("check" to "initial")
    @Test fun firstResultAndLateAjaxCookiesCanBeSent() {
        assertTrue(YandexCookieSubmissionPolicy.shouldSend(null, initial, false, 0))
        assertTrue(YandexCookieSubmissionPolicy.shouldSend(initial, mapOf("check" to "verified"), false, 5000))
    }
    @Test fun unchangedCookiesDoNotCauseReconnectStormsButManualRetryWorks() {
        assertFalse(YandexCookieSubmissionPolicy.shouldSend(initial, initial, false, 30000))
        assertTrue(YandexCookieSubmissionPolicy.shouldSend(initial, initial, true, 5000))
        assertFalse(YandexCookieSubmissionPolicy.shouldSend(initial, initial, true, 4999))
        assertFalse(YandexCookieSubmissionPolicy.shouldSend(null, emptyMap(), true, 30000))
    }
    @Test fun manualSubmittedWindowClosesForAWorkingTunnelWithoutClaimingVerificationPassed() {
        assertTrue(YandexCheckPresentation.canDismissForReadyTunnel(false, true, true))
        assertTrue(YandexCheckPresentation.canDismissForReadyTunnel(true, false, true))
        assertFalse(YandexCheckPresentation.canDismissForReadyTunnel(false, false, true))
        assertFalse(YandexCheckPresentation.canDismissForReadyTunnel(true, true, false))
    }
}
