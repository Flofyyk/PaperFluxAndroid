package com.accar.openflux

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProfileConfigTest {
    private fun profile(server: String = "example.invalid") = JSONObject().put("id", "7").put("name", "Тест & профиль")
        .put("server", server).put("token", "x".repeat(43)).put("clientIp", "10.10.10.2").put("transport", "yandex")
        .put("documentUrls", JSONArray(listOf("https://disk.yandex.ru/i/example", "https://disk.yandex.ru/i/other")))
    @Test fun exportedQrRoundTripsAllFields() {
        val canonical = ProfileConfig.parse(profile().toString())
        val uri = ProfileConfig.export(canonical)
        val bitmap = ProfileQr.bitmap(uri)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
        assertEquals(uri, decoded)
        val imported = ProfileConfig.parse(decoded)
        for (field in listOf("id", "name", "server", "token", "clientIp", "documentUrl", "transport")) assertEquals(field, canonical.getString(field), imported.getString(field))
        bitmap.recycle()
    }
    @Test fun sameIdOnDifferentServersDoesNotOverwriteProfiles() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "store-test-${System.nanoTime()}").apply { mkdir() }
        val prefs = "profile-test-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefs, mode)
        }
        try {
            val store = ProfileStore(context)
            store.save(ProfileConfig.parse(profile().toString()))
            store.save(ProfileConfig.parse(profile("second.invalid").toString()))
            val state = JSONObject(store.publicState())
            assertEquals(2, state.getJSONArray("profiles").length())
            assertFalse(state.toString().contains("token"))
            store.select("example.invalid|7")
            assertEquals("example.invalid", store.active()!!.getString("server"))
            val selected = store.active()!!
            store.update("example.invalid|7", "Переименован", selected.getString("server"), selected.getString("documentUrl"), selected.getString("clientIp"), selected.getString("transport"), null)
            assertEquals("x".repeat(43), store.find("example.invalid|7").getString("token"))
            store.delete("second.invalid|7")
            assertEquals(1, JSONObject(store.publicState()).getJSONArray("profiles").length())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete(); base.deleteSharedPreferences(prefs) }
    }
    @Test fun malformedImportIsRejected() {
        val invalid = profile().put("token", "short")
        assertTrue(runCatching { ProfileConfig.parse(invalid.toString()) }.isFailure)
        assertTrue(runCatching { ProfileConfig.parse("https://example.invalid/") }.isFailure)
    }
}
