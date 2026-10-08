package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class VpnTilePolicyTest {
    @Test fun tileCanCancelStartupAndRecoveryNotJustConnectedVpn() {
        for (state in listOf("CONNECTED", "CONNECTING", "RECONNECTING", "WAITING_NETWORK", "TUN", "TRANSPORT", "AUTH", "DNS")) assertTrue(state, VpnTilePolicy.active(state))
        for (state in listOf("DISCONNECTED", "ERROR", "")) assertFalse(state, VpnTilePolicy.active(state))
    }
    @Test fun captionsDoNotClaimRecoveryIsConnected() {
        assertEquals("Подключён", VpnTilePolicy.subtitle("CONNECTED"))
        assertEquals("Переподключение", VpnTilePolicy.subtitle("RECONNECTING"))
        assertEquals("Отключён", VpnTilePolicy.subtitle("DISCONNECTED"))
    }
}
