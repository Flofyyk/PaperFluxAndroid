package com.accar.openflux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRecoveryPolicyTest {
    @Test fun primaryProfileKeepsRouteThroughoutAutomaticRecovery() {
        // Neither spare nodes, a usable underlying network nor CAPTCHA
        // completion are prerequisites for retaining an already established TUN.
        assertTrue(VpnRecoveryPolicy.retainInterface(true, true, false))
    }
    @Test fun explicitStopAndDisabledRecoveryReleaseRoute() {
        assertFalse(VpnRecoveryPolicy.retainInterface(false, true, false))
        assertFalse(VpnRecoveryPolicy.retainInterface(true, false, false))
        assertFalse(VpnRecoveryPolicy.retainInterface(true, true, true))
    }
}
