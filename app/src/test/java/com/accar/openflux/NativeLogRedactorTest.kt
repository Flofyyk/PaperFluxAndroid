package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class NativeLogRedactorTest {
    @Test fun hidesPrivateLinksAddressesAndStructuredSecrets() {
        val cases = listOf(
            "dial wss://docs.datacloudmail.ru/doc/private-document/c/?token=secret" to "private-document",
            "open https://disk.yandex.ru/i/private-document" to "private-document",
            "profile-token=private-secret" to "private-secret",
            "{\"token\":\"private-secret\",\"cookie\":\"session=private-cookie\"}" to "private-cookie",
            "password: 'private password'" to "private password",
            "peer 203.0.113.19:443 disconnected" to "203.0.113.19",
            "jwt abcdefghijkl.abcdefghijkl.abcdefghijkl" to "abcdefghijkl.abcdefghijkl",
            "cookie=session=private-secret; queued" to "private-secret"
        )
        for ((input, privateValue) in cases) {
            assertFalse(input, NativeLogRedactor.redact(input).contains(privateValue))
        }
    }
    @Test fun keepsUsefulCountersAndBoundsOutput() {
        val counters = "[PAPERFLUX_STATS] rx=123 tx=456 ping=100 queue=0"
        assertEquals(counters, NativeLogRedactor.redact(counters))
        assertEquals(240, NativeLogRedactor.redact("x".repeat(1000)).length)
    }
}
