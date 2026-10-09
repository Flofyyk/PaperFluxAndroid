package com.accar.openflux

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Uses only the instrumentation package's sandbox, never the user's settings. */
class ConnectionModeDeviceTest {
    @Test fun persistedModeSelectsSeparateNonVpnEndpoint() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assertNotEquals(context.packageName, InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        val store = NetworkSettingsStore(context)
        try {
            store.reset()
            assertEquals(OpenFluxVpnService::class.java, ConnectionRuntime.service(context))
            store.update("connectionMode", "proxy")
            assertEquals("proxy", NetworkSettingsStore(context).read().connectionMode)
            assertEquals(OpenFluxProxyService::class.java, ConnectionRuntime.service(context))
            assertEquals(OpenFluxProxyService::class.java.name,
                ConnectionRuntime.intent(context, OpenFluxVpnService.START).component?.className)
            store.update("dnsPrimary", "1.1.1.1")
            assertEquals("proxy", NetworkSettingsStore(context).read().connectionMode)
            store.update("connectionMode", "vpn")
            assertEquals(OpenFluxVpnService::class.java, ConnectionRuntime.service(context))
        } finally { store.reset() }
    }
}
