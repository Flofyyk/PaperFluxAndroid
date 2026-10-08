package com.accar.openflux

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Use an isolated directory: never modify the owner's actual settings. */
class NetworkSettingsStoreDeviceTest {
    @Test fun synchronousCommitSurvivesNewInstanceAndRejectedEdit() = isolated { context ->
        val store = NetworkSettingsStore(context)
        store.update("dnsPrimary", "1.1.1.1")
        store.update("dnsSecondary", "8.8.8.8")
        store.update("mtu", "1280")
        store.update("autoReconnect", "false")
        store.update("autoConnect", "true")
        store.update("connectTimeoutSec", "30")
        val saved = store.read()
        assertEquals(saved, NetworkSettingsStore(context).read())
        assertThrows(IllegalArgumentException::class.java) { store.update("dnsPrimary", "1.1.") }
        assertEquals(saved, NetworkSettingsStore(context).read())
        assertEquals("1.1.1.1", saved.dnsPrimary)
        assertEquals("8.8.8.8", saved.dnsSecondary)
        assertFalse(saved.autoReconnect)
        assertTrue(saved.autoConnect)
        assertEquals(1280, saved.mtu)
        assertEquals(30, saved.connectTimeoutSec)
        store.reset()
        assertEquals(NetworkSettings(), NetworkSettingsStore(context).read())
    }

    @Test fun concurrentUpdatesDoNotLoseUnrelatedSettings() = isolated { context ->
        val executor = Executors.newFixedThreadPool(2)
        try {
            val one = executor.submit { repeat(10) { NetworkSettingsStore(context).update("dnsPrimary", "9.9.9.9") } }
            val two = executor.submit { repeat(10) { NetworkSettingsStore(context).update("mtu", "1280") } }
            one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS)
            assertEquals("9.9.9.9", NetworkSettingsStore(context).read().dnsPrimary)
            assertEquals(1280, NetworkSettingsStore(context).read().mtu)
        } finally { executor.shutdownNow() }
    }

    private fun isolated(test: (ContextWrapper) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(target.cacheDir, "settings-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir(): File = folder }
        try { test(context) } finally { folder.deleteRecursively() }
    }
}
