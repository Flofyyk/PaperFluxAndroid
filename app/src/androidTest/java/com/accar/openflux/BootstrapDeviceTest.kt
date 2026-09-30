package com.accar.openflux

import androidx.test.platform.app.InstrumentationRegistry
import android.net.VpnService
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit live test; uses private device state, never a checked-in credential. */
class BootstrapDeviceTest {
    @Test fun vpnPermissionIsGrantedBeforeConnectionSmokeTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull("Android VPN consent is required", VpnService.prepare(context))
    }
    @Test fun inspectReusableYandexCookiesWhenExplicitlyRequested() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("paperflux.inspectCookies") == "1")
        val context = instrumentation.targetContext
        val profile = requireNotNull(ProfileStore(context).active())
        val urls = profile.optString("documentUrl").split(',').filter { it.isNotBlank() }
        val keys = urls.map { NativeAuthBridge.cookieKey(profile.optString("transport"), it) }
        val files = context.noBackupFilesDir.listFiles().orEmpty().filter { it.name.startsWith("session-cookies-") && it.name.endsWith(".json") }
        val matches = files.sumOf { file ->
            val cookieStore = NativeAuthBridge.read(file)
            keys.count { cookieStore?.optJSONObject(it) != null }
        }
        println("PaperFlux reusable document-cookie matches: $matches across ${files.size} private stores")
    }
    @Test fun selectedProfileCanBeDiscoveredUsingAddressAndPasswordOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val existing = requireNotNull(ProfileStore(context).active())
        val discovered = ProfileBootstrapClient().fetch(existing.getString("server"), existing.getString("token"), existing.getString("name"))
        for (field in listOf("id", "token", "server", "clientIp", "documentUrl", "transport")) assertEquals(field, existing.getString(field), discovered.getString(field))
        assertTrue(runCatching { ProfileBootstrapClient().fetch(existing.getString("server"), "wrong".repeat(9), "Rejected") }.isFailure)
    }

    @Test fun refreshSelectedProfileWhenExplicitlyRequested() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("paperflux.refreshSelectedProfile") == "1")
        val context = instrumentation.targetContext
        val store = ProfileStore(context)
        val existing = requireNotNull(store.active())
        arguments.getString("paperflux.expectedProfileId")?.let {
            assertEquals("Unexpected selected profile; refusing to replace it", it, existing.getString("id"))
        }
        val discovered = ProfileBootstrapClient().fetch(existing.getString("server"), existing.getString("token"), existing.getString("name"))
        if (existing.optString("transport") == "yandex") {
            assertTrue("The server did not deliver a Volga document", discovered.optString("volgaUrl").isNotBlank())
        }
        store.save(discovered, existing.getString("key"))
        assertEquals(discovered.optString("volgaUrl"), requireNotNull(store.active()).optString("volgaUrl"))
    }
}
