package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class YandexAuthAutoOpenPolicyTest {
    private val now = 2_000_000L
    private fun eligible(policy: YandexAuthAutoOpenPolicy, id: String = "request", foreground: Boolean = true,
                         alive: Boolean = true, created: Long = now, dismissed: Long = 0) =
        policy.shouldOpen(id, created, now, foreground, alive, dismissed)

    @Test fun newRequestArrivingInForegroundIsEligibleWithoutAResume() {
        val policy = YandexAuthAutoOpenPolicy()
        assertTrue(eligible(policy))
        policy.markOpened("request")
        assertFalse(eligible(policy))
        assertTrue(eligible(policy, "next-channel"))
    }
    @Test fun backgroundRequestWaitsForForegroundAndIsNotConsumed() {
        val policy = YandexAuthAutoOpenPolicy()
        assertFalse(eligible(policy, foreground = false))
        assertTrue(eligible(policy))
        assertFalse(eligible(policy, alive = false))
    }
    @Test fun seenRequestsSurviveActivityRecreation() {
        val policy = YandexAuthAutoOpenPolicy()
        policy.markOpened("first"); policy.markOpened("second")
        val recreated = YandexAuthAutoOpenPolicy(policy.openedIds())
        assertFalse(eligible(recreated, "first"))
        assertFalse(eligible(recreated, "second"))
        assertTrue(eligible(recreated, "third"))
    }
    @Test fun dismissalDefersRetriesButNotOtherScopes() {
        val policy = YandexAuthAutoOpenPolicy()
        assertFalse(eligible(policy, dismissed = now + 600_000))
        assertTrue(eligible(policy, dismissed = now))
        assertNotEquals(YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", false),
            YandexAuthAutoOpenPolicy.scope("attempt", "yandex-2", false))
        assertNotEquals(YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", false),
            YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", true))
        assertNotEquals(YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", false),
            YandexAuthAutoOpenPolicy.scope("new-attempt", "yandex-1", false))
    }
    @Test fun rejectsBlankStaleFutureAndUndatedRequests() {
        val policy = YandexAuthAutoOpenPolicy()
        assertFalse(eligible(policy, id = " "))
        assertFalse(eligible(policy, created = 0))
        assertFalse(eligible(policy, created = now + 1))
        assertFalse(eligible(policy, created = now - 1_800_001))
        assertTrue(eligible(policy, created = now - 1_800_000))
    }
    @Test fun deduplicationMemoryIsBounded() {
        val policy = YandexAuthAutoOpenPolicy()
        repeat(100) { policy.markOpened("request-$it") }
        assertEquals(64, policy.openedIds().size)
        assertFalse(eligible(policy, "request-99"))
    }
    @Test fun changedRequestIdCannotReopenSameChannelSideAndAttempt() {
        val scope = YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", false)
        val policy = YandexAuthAutoOpenPolicy()
        policy.markOpened("old-id", scope)
        assertFalse(policy.shouldOpen("new-id", now, now, true, true, 0, scope))
        val restored = YandexAuthAutoOpenPolicy(policy.openedIds(), policy.openedScopes())
        assertFalse(restored.shouldOpen("another-id", now, now, true, true, 0, scope))
        assertFalse(YandexAuthAutoOpenPolicy().shouldOpen("new-id", now, now, true, true, 0, scope, true))
        for (other in listOf(YandexAuthAutoOpenPolicy.scope("attempt", "yandex-2", false),
            YandexAuthAutoOpenPolicy.scope("attempt", "yandex-1", true),
            YandexAuthAutoOpenPolicy.scope("new-attempt", "yandex-1", false))) {
            assertTrue(restored.shouldOpen("new-id", now, now, true, true, 0, other))
        }
    }
    @Test fun openedScopeMemoryIsBounded() {
        val policy = YandexAuthAutoOpenPolicy()
        repeat(100) { policy.markOpened("request-$it", "scope-$it") }
        assertEquals(64, policy.openedScopes().size)
        assertFalse(policy.shouldOpen("new-id", now, now, true, true, 0, "scope-99"))
    }
    @Test fun workingTunnelDoesNotOpenAuxiliaryCheckButWaitingConnectionDoes() {
        val policy = YandexAuthAutoOpenPolicy()
        assertFalse(policy.shouldOpen("new-id", now, now, true, true, 0, vpnConnected = true))
        // Suppression is not consumption: still eligible if it becomes needed.
        assertTrue(policy.shouldOpen("new-id", now, now, true, true, 0, vpnConnected = false))
    }
}
