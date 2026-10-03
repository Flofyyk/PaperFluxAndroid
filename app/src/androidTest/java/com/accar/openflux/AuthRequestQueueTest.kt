package com.accar.openflux

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class AuthRequestQueueTest {
    @Test fun preflightCookiesReachOnlyTheMatchingNativeDocumentStore() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "preflight-test-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        try {
            val doc = "https://disk.yandex.ru/i/example"
            val other = "https://disk.yandex.ru/i/other"
            val jar = JSONObject().put("test-cookie", "test-value")
            NativeAuthBridge.savePreflightCookies(context, doc, jar)
            NativeAuthBridge.applyPreflightCookies(context, "test", "yandex", listOf(other))
            val target = File(dir, "session-cookies-test.json")
            assertFalse(target.exists())
            NativeAuthBridge.applyPreflightCookies(context, "test", "yandex", listOf(doc))
            val saved = NativeAuthBridge.read(target)!!
            assertEquals("test-value", saved.getJSONObject(NativeAuthBridge.cookieKey("yandex", doc)).getString("test-cookie"))
            assertFalse(saved.has(NativeAuthBridge.cookieKey("yandex", other)))
            assertFalse(NativeAuthBridge.read(File(dir,"preflight-cookies.json"))!!.has(NativeAuthBridge.cookieKey("yandex",doc)))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun matchingDocumentCookiesSurviveProfileIdChangeWithoutCopyingOthers() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "cookie-migration-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        try {
            val shared = "https://disk.yandex.ru/i/shared"
            val unrelated = "https://disk.yandex.ru/i/unrelated"
            val old = JSONObject()
                .put(NativeAuthBridge.cookieKey("yandex", shared), JSONObject().put("session", "valid"))
                .put(NativeAuthBridge.cookieKey("yandex", unrelated), JSONObject().put("other", "private"))
                .put(NativeAuthBridge.cookieKey("vyandex", shared), JSONObject().put("volga", "private"))
            NativeAuthBridge.write(File(dir, "session-cookies-7.json"), old)
            NativeAuthBridge.applyPreflightCookies(context, "9", "yandex", listOf(shared))
            val migrated = requireNotNull(NativeAuthBridge.read(File(dir, "session-cookies-9.json")))
            assertEquals(1, migrated.length())
            assertEquals("valid", migrated.getJSONObject(NativeAuthBridge.cookieKey("yandex", shared)).getString("session"))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    private fun request(id: String, remote: Boolean) = JSONObject().put("requestId", id).put("remote", remote)
    @Test fun lateDuplicateCannotReopenCompletedCheck() {
        val queue = AuthRequestQueue()
        assertTrue(queue.accept(request("check", false)))
        assertTrue(queue.acknowledge("check"))
        assertFalse(queue.accept(request("check", false)))
        assertNull(queue.current())
        assertTrue(queue.accept(request("new-check", false)))
    }
    @Test fun checksDoNotOverwriteEachOtherAndStaleAcksCannotClearThem() {
        val queue = AuthRequestQueue()
        assertTrue(queue.accept(request("local-1", false)))
        assertFalse(queue.accept(request("server-1", true)))
        assertFalse(queue.accept(request("local-2", false)))
        assertEquals("local-1", queue.current()?.getString("requestId"))
        assertFalse(queue.acknowledge("stale"))
        assertEquals("local-1", queue.current()?.getString("requestId"))
        assertTrue(queue.acknowledge("local-1"))
        assertEquals("server-1", queue.current()?.getString("requestId"))
        assertFalse(queue.accept(request("server-1", true)))
        assertTrue(queue.acknowledge("server-1"))
        assertEquals("local-2", queue.current()?.getString("requestId"))
        assertTrue(queue.acknowledge("local-2"))
        assertNull(queue.current())
    }
}
