package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class NetworkSettingsTest {
    @Test fun customDnsDoesNotResetOtherSettings() {
        val settings = NetworkSettings().update("dnsPrimary", "1.1.1.1").update("dnsSecondary", "8.8.8.8")
            .update("mtu", "1280").update("autoReconnect", "false").update("autoConnect", "true").update("connectTimeoutSec", "30")
        assertEquals("1.1.1.1", settings.dnsPrimary)
        assertEquals("8.8.8.8", settings.dnsSecondary)
        assertEquals(1280, settings.mtu)
        assertFalse(settings.autoReconnect)
        assertTrue(settings.autoConnect)
        assertEquals(30, settings.connectTimeoutSec)
    }
    @Test fun strictDnsDoesNotResolveHostnamesOrAcceptPartialEdits() {
        for (raw in listOf("", "1.1.1", "1.1.1.", "256.1.1.1", "https://1.1.1.1", "dns.google", "::1", "1.1.1.1:53", "127.0.0.1", "0.0.0.0", "224.0.0.1", "1.1.-1.1")) {
            assertThrows(raw, IllegalArgumentException::class.java) { NetworkSettings.dns(raw) }
        }
        assertEquals("1.1.1.1", NetworkSettings.dns(" 001.001.001.001 "))
    }
    @Test fun retainedTunMustChangeAfterDnsMtuOrExclusionsEdit() {
        val defaults = NetworkSettings()
        val key = defaults.tunnelKey("10.0.0.2", emptySet())
        assertNotEquals(key, defaults.update("dnsPrimary", "1.1.1.1").tunnelKey("10.0.0.2", emptySet()))
        assertNotEquals(key, defaults.update("dnsSecondary", "8.8.4.4").tunnelKey("10.0.0.2", emptySet()))
        assertNotEquals(key, defaults.update("mtu", "1280").tunnelKey("10.0.0.2", emptySet()))
        assertNotEquals(key, defaults.tunnelKey("10.0.0.2", setOf("com.example.app")))
        assertEquals(defaults.tunnelKey("10.0.0.2", linkedSetOf("b", "a")), defaults.tunnelKey("10.0.0.2", linkedSetOf("a", "b")))
    }
    @Test fun invalidValuesCannotReplaceSavedSettings() {
        val settings = NetworkSettings().update("dnsPrimary", "9.9.9.9")
        for ((key, value) in listOf("mtu" to "0", "connectTimeoutSec" to "121", "autoConnect" to "yes", "token" to "no", "dnsPrimary" to "bad")) {
            assertThrows(IllegalArgumentException::class.java) { settings.update(key, value) }
        }
        assertEquals("9.9.9.9", settings.dnsPrimary)
    }
}
