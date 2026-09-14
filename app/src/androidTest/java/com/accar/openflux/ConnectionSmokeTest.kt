package com.accar.openflux

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Explicit device smoke test using the device's existing selected profile. */
class ConnectionSmokeTest {
    @Test fun repeatedConnectionsReachReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = requireNotNull(ProfileStore(context).active()) { "Select a device profile first" }
        repeat(3) {
            context.startService(Intent(context, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP))
            Thread.sleep(1500)
            context.startForegroundService(Intent(context, OpenFluxVpnService::class.java)
                .setAction(OpenFluxVpnService.START)
                .putExtra(OpenFluxVpnService.EXTRA_DOCUMENT_URL, profile.getString("documentUrl")))
            val deadline = android.os.SystemClock.elapsedRealtime() + 110_000
            var state = ""
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(500)
                state = TunnelSnapshot.read(context).optString("state")
                if (state == "CONNECTED" || state == "ERROR") break
            }
            assertEquals("Connection attempt ${it + 1}", "CONNECTED", state)
        }
    }
}
