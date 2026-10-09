package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class ConnectionWaitBudgetTest {
    @Test fun verificationDoesNotConsumeRecoveryBudget() {
        val budget = ConnectionWaitBudget(0, 20_000)
        assertFalse(budget.expired(10_000, false))
        assertFalse(budget.expired(120_000, true))
        assertFalse(budget.expired(129_000, false))
        assertTrue(budget.expired(130_000, false))
    }
    @Test fun abandonedVerificationIsBounded() {
        val budget = ConnectionWaitBudget(0, 60_000)
        assertFalse(budget.expired(1_799_999, true))
        assertTrue(budget.expired(1_800_000, true))
    }
    @Test fun healthDetailsNeverCopyArbitraryNativeContent() {
        assertNull(TunnelHealthDiagnostics.message("private stuff"))
        val result = TunnelHealthDiagnostics.message("TUNNEL_PROBE_FAILED: resolver=1.1.1.1:53 stage=tcp reason=timeout secret=PRIVATE")!!
        assertTrue(result.contains("1.1.1.1:53"))
        assertTrue(result.contains("тайм-аут"))
        assertFalse(result.contains("PRIVATE"))
    }
}
