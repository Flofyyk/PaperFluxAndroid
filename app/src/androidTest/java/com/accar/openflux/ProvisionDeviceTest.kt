package com.accar.openflux

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

/** Explicit device-test fixture only; not part of the shipped application. */
class ProvisionDeviceTest {
    @Test fun installPrivateFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.filesDir, "provision-profile.json")
        if (!fixture.exists()) return
        try {
            val profile = JSONObject(fixture.readText())
            require(profile.optString("token").length >= 32)
            val store = ProfileStore(context)
            store.save(profile)
            assertTrue(store.active()?.optString("id") == profile.optString("id"))
            assertTrue(!store.publicState().contains(profile.optString("token")))
        } finally { fixture.delete() }
    }
}
