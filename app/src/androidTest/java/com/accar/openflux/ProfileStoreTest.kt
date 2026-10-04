package com.accar.openflux

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class ProfileStoreTest {
    private fun withIsolatedStore(test: (Context, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = "profile-recovery-test-${System.nanoTime()}"
        val directory = File(target.cacheDir, suffix).apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$suffix-$name", mode)
        }
        try { test(context, directory) } finally {
            directory.deleteRecursively()
            context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun recoveryProfile(id: String) = JSONObject().put("id", id).put("token", "offline-secret-$id")
        .put("name", "Profile $id").put("server", "test.invalid")
        .put("documentUrl", "https://disk.yandex.ru/i/test$id").put("clientIp", "10.10.10.$id")

    @Test fun migrationRestoresBackupBeforeLookingAtLegacyPreferences() = withIsolatedStore { context, directory ->
        ProfileStore(context).apply { save(recoveryProfile("2")); save(recoveryProfile("3")); select("2") }
        val base = File(directory, "profiles-v2.bin")
        assertTrue(base.renameTo(File(directory, "profiles-v2.bin.bak")))
        // A stale legacy credential must not replace the existing encrypted profiles.
        context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE).edit()
            .putString("profile_id", "9").putString("profile_token", "stale-legacy-secret").commit()
        val reopened = ProfileStore(context)
        reopened.migrate()
        assertEquals("2", reopened.active()!!.getString("id"))
        assertEquals("offline-secret-2", reopened.active()!!.getString("token"))
        assertEquals("offline-secret-3", reopened.find("3").getString("token"))
        assertTrue(base.exists())
        assertFalse(File(directory, "profiles-v2.bin.bak").exists())
    }

    @Test fun incompleteReplacementRestoresPreviousEncryptedSelection() = withIsolatedStore { context, directory ->
        ProfileStore(context).save(recoveryProfile("2"))
        val base = File(directory, "profiles-v2.bin")
        assertTrue(base.renameTo(File(directory, "profiles-v2.bin.bak")))
        base.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals("offline-secret-2", ProfileStore(context).active()!!.getString("token"))
        ProfileStore(context).save(recoveryProfile("3"))
        assertEquals("offline-secret-2", ProfileStore(context).find("2").getString("token"))
    }

    @Test fun corruptedStoreWithoutBackupIsNotSilentlyTreatedAsEmpty() = withIsolatedStore { context, directory ->
        File(directory, "profiles-v2.bin").writeBytes(byteArrayOf(1, 2, 3))
        try {
            ProfileStore(context).active()
            fail("Corruption must not erase the visible profile list")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun concurrentStoreInstancesKeepEverySavedProfile() = withIsolatedStore { context, _ ->
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val gate = java.util.concurrent.CountDownLatch(1)
        val workers = (2..9).map { id -> Thread {
            try { gate.await(); ProfileStore(context).save(recoveryProfile(id.toString())) }
            catch (error: Throwable) { failures.add(error) }
        }.apply { start() } }
        gate.countDown()
        workers.forEach { it.join(10_000); assertFalse("Writer stuck", it.isAlive) }
        assertTrue("Concurrent writes failed: $failures", failures.isEmpty())
        val reopened = ProfileStore(context)
        for (id in 2..9) assertEquals("offline-secret-$id", reopened.find(id.toString()).getString("token"))
    }

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
