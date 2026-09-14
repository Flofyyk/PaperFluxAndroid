package com.accar.openflux

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class ProfileStoreTest {
    @Test fun atomicSelectionDeletionAndEncryptedStorage() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = "profile-test-${System.nanoTime()}"
        val directory = File(target.cacheDir, suffix).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$suffix-$name", mode)
        }
        try {
            val store = ProfileStore(context)
            val token = "test-secret-0123456789abcdef0123456789"
            fun profile(id:String) = JSONObject().put("id",id).put("token","$token-$id")
                .put("name","Profile $id").put("server","test.invalid").put("documentUrl","https://disk.yandex.ru/i/test$id").put("clientIp","10.10.10.$id")
            store.save(profile("2"));store.save(profile("3"));store.select("2")
            val reopened = ProfileStore(context)
            assertEquals("2", reopened.active()!!.getString("id"))
            assertEquals("$token-2", reopened.active()!!.getString("token"))
            assertEquals("10.10.10.2", reopened.active()!!.getString("clientIp"))
            assertFalse(reopened.publicState().contains(token))
            assertFalse(File(directory,"profiles-v2.bin").readBytes().toString(Charsets.ISO_8859_1).contains(token))
            reopened.delete("2")
            assertEquals("3",reopened.active()!!.getString("id"))
            assertEquals("$token-3",reopened.active()!!.getString("token"))
            reopened.delete("3");assertNull(reopened.active())
            assertEquals("",context.getSharedPreferences(OpenFluxVpnService.PREFS,Context.MODE_PRIVATE).getString("document",null))
        } finally { directory.deleteRecursively(); context.getSharedPreferences(OpenFluxVpnService.PREFS,Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
