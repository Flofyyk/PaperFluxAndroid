package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun numericVersionOrderNeverOffersDowngrades() {
        assertTrue(AppUpdatePolicy.newer("v0.4.28", "0.4.27"))
        assertTrue(AppUpdatePolicy.newer("v0.4.100", "0.4.99"))
        assertTrue(AppUpdatePolicy.newer("v0.5.0", "0.4.99"))
        assertFalse(AppUpdatePolicy.newer("v0.4.27", "0.4.28-test.1"))
        assertFalse(AppUpdatePolicy.newer("v0.4.27", "0.4.27"))
        assertTrue(AppUpdatePolicy.newer("v0.4.28", "0.4.28-test.1"))
    }
    @Test fun betaMalformedTagsAndHugeNumbersAreNotOffered() {
        for (tag in listOf("v0.4.28-rc.1", "v0.4.28-beta", "latest", "0.4", "v0.4.9999999", "https://evil.test", "v00.4.28")) {
            assertFalse(tag, AppUpdatePolicy.newer(tag, "0.4.27"))
        }
    }
    @Test fun skipLastsExactly24Hours() {
        val now = 1_800_000_000_000L
        val until = now + AppUpdatePolicy.SNOOZE_MS
        assertFalse(AppUpdatePolicy.shouldOffer(now, until))
        assertFalse(AppUpdatePolicy.shouldOffer(until - 1, until))
        assertTrue(AppUpdatePolicy.shouldOffer(until, until))
    }
    @Test fun preferredAbiThenUniversal() {
        assertEquals(listOf("PaperFlux-v0.4.28-arm64-v8a.apk", "PaperFlux-v0.4.28-armeabi-v7a.apk", "PaperFlux-v0.4.28-universal.apk"),
            AppUpdatePolicy.assetNames("v0.4.28", listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(listOf("PaperFlux-v0.4.28-universal.apk"), AppUpdatePolicy.assetNames("v0.4.28", listOf("unknown")))
    }
}
