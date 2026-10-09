package com.accar.openflux

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppRoutingStoreTest {
    @Test fun independentReadersSeeChangesDespitePreferenceRewrites() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "routing-test-${System.nanoTime()}").apply { mkdir() }
        val preferences = "routing-test-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferences, mode)
        }
        try {
            val prefs = context.getSharedPreferences("ignored", 0)
            prefs.edit().putStringSet(OpenFluxVpnService.EXCLUDED_APPS, setOf("ru.ozon.app.android")).commit()
            val writer = AppRoutingStore(context)
            val reader = AppRoutingStore(context)
            writer.migrate()
            assertEquals(setOf("ru.ozon.app.android"), reader.read())
            prefs.edit().clear().putString("state", "CONNECTED").commit()
            assertEquals(setOf("ru.ozon.app.android"), reader.read())
            writer.save(setOf("com.paperflux.speedprobe"))
            reader.migrate()
            assertEquals(setOf("com.paperflux.speedprobe"), reader.read())
            val saved = File(directory, "app-routing.json")
            assertTrue(saved.renameTo(File(directory, "app-routing.json.bak")))
            reader.migrate()
            assertEquals(setOf("com.paperflux.speedprobe"), reader.read())
            writer.save(emptySet())
            assertTrue(reader.read().isEmpty())
            assertTrue(runCatching { AppRoutingStore.decode("[\"invalid/package\"]") }.isFailure)
            saved.writeText("[\"app.legacy\"]")
            assertEquals(AppRoutingConfig.EXCLUDE, reader.config().mode)
            assertEquals(setOf("app.legacy"), reader.config().excluded)
            writer.saveConfig(reader.config().copy(mode = AppRoutingConfig.INCLUDE, included = setOf("app.vpn")))
            assertEquals(setOf("app.vpn"), reader.config().selected)
            assertEquals(setOf("app.legacy"), reader.config().excluded)
            writer.saveConfig(reader.config().copy(mode = AppRoutingConfig.EXCLUDE))
            assertEquals(setOf("app.legacy"), reader.config().selected)
            assertEquals(setOf("app.vpn"), reader.config().included)
        } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
            base.deleteSharedPreferences(preferences)
        }
    }
}
