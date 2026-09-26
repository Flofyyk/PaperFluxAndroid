package com.accar.openflux

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Explicit live test; uses private device state, never a checked-in credential. */
class BootstrapDeviceTest {
    @Test fun selectedProfileCanBeDiscoveredUsingAddressAndPasswordOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val existing = requireNotNull(ProfileStore(context).active())
        val discovered = ProfileBootstrapClient().fetch(existing.getString("server"), existing.getString("token"), existing.getString("name"))
        for (field in listOf("id", "token", "server", "clientIp", "documentUrl", "transport")) assertEquals(field, existing.getString(field), discovered.getString(field))
        assertTrue(runCatching { ProfileBootstrapClient().fetch(existing.getString("server"), "wrong".repeat(9), "Rejected") }.isFailure)
    }
}
