package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class MailruConnectionDiagnosticsTest {
    @Test fun verificationStatusShowsOnlySafeDetails() {
        assertEquals("Mail.ru пока не подтвердил доступ. Следующая попытка через 60 с",
            MailruConnectionDiagnostics.message("[M-DOCS] browser verification limited; retry in 60 seconds"))
        val passed = MailruConnectionDiagnostics.message("[M-DOCS] browser verification completed; retrying document request")!!
        assertTrue(passed.contains("автоматически"))
        assertFalse(passed.contains("VPN подключён"))
        assertNull(MailruConnectionDiagnostics.message("[M-DOCS] browser verification limited; retry in 60 seconds private-token"))
    }
    @Test fun failuresReachJournalWithoutPrivateDetails() {
        for (event in listOf(
            "cannot open the document; retrying", "WebSocket dial failed (http 403); retrying",
            "Socket.IO handshake failed; retrying", "Socket.IO connection rejected; reconnecting",
            "editor authentication rejected (code=0); reconnecting",
            "editor rejected connection (event=disconnectReason code=4002); reconnecting",
            "connection to the document dropped; reconnecting"
        )) assertNotNull(MailruConnectionDiagnostics.message("2026/10/08 [M-DOCS] $event"))
        val output = MailruConnectionDiagnostics.message("[M-DOCS] WebSocket dial failed (http 403) private-document-token")!!
        assertFalse(output.contains("private-document-token"))
        assertFalse(output.contains("403"))
    }
    @Test fun packetAndHeartbeatNoiseStaysHidden() {
        for (event in listOf("cursor: private-packet", "Engine.IO open", "pong", "unknown error https://private-document"))
            assertNull(MailruConnectionDiagnostics.message("[M-DOCS] $event"))
        assertNull(MailruConnectionDiagnostics.message("[OTHER] connection to the document dropped; reconnecting"))
    }
    @Test fun editorAuthenticationDoesNotClaimWorkingVpn() {
        val output = MailruConnectionDiagnostics.message("[M-DOCS] Editor authentication completed")!!
        assertTrue(output.contains("Проверяем"))
        assertFalse(output.contains("VPN подключён"))
    }
}
