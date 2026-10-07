package com.accar.openflux

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class ProfileCountryCacheTest {
    @Test fun firstCountrySurvivesReopeningAndIsPublicWithoutProfileSecrets() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = "country-cache-test-${System.nanoTime()}"
        val directory = File(target.cacheDir, suffix).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$suffix-$name", mode)
        }
        try {
            val country = ProfileCountryCache(context)
            assertEquals("NL", country.remember("203.0.113.7", "203.0.113.7", "NL"))
            assertEquals("NL", ProfileCountryCache(context).forServer(" 203.0.113.7 "))
            assertEquals("NL", country.remember("203.0.113.7", "203.0.113.7", "DE"))
            assertNull(country.forServer("203.0.113.8"))
            val store = ProfileStore(context)
            store.save(JSONObject().put("id", "1").put("name", "Offline fixture")
                .put("server", "203.0.113.7").put("token", "private-test-key")
                .put("documentUrl", "https://disk.yandex.ru/i/offline-test"))
            val publicProfile = JSONObject(store.publicState()).getJSONArray("profiles").getJSONObject(0)
            assertEquals("NL", publicProfile.getString("countryCode"))
            assertFalse(publicProfile.has("token"))
            // Ping/lookup failure has no operation that can remove this cache.
            assertEquals("NL", ProfileCountryCache(context).forAddress("203.0.113.7"))
        } finally {
            directory.deleteRecursively()
            for (name in listOf("profile-country-cache", OpenFluxVpnService.PREFS)) {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }
}
