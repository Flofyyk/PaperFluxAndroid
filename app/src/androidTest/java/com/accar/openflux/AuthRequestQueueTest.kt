package com.accar.openflux

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class AuthRequestQueueTest {
    @Test fun publicWarningIsIndependentOfTunnelStateAndContainsNoPrivateRequestFields() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "verification-state-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        val request = JSONObject().put("requestId", "test-id").put("attempt", "private-attempt")
            .put("transport", "yandex-2").put("remote", true).put("created", System.currentTimeMillis())
            .put("url", "https://disk.yandex.ru/i/test-only").put("proxy", "127.0.0.1:1234")
        try {
            NativeAuthBridge.write(File(dir, "auth-request.json"), request)
            val visible = requireNotNull(NativeAuthBridge.verificationState(context, true))
            assertEquals(setOf("carrier", "side", "automatic", "checking", "retry"), visible.keys().asSequence().toSet())
            assertEquals("документ 2", visible.getString("carrier"))
            assertEquals("VPS", visible.getString("side"))
            assertTrue(visible.getBoolean("automatic"))
            assertFalse(visible.getBoolean("checking"))
            assertFalse(visible.getBoolean("retry"))
            assertFalse(NativeAuthBridge.verificationState(context, true, vpnConnected = true)!!.getBoolean("automatic"))
            assertNull(NativeAuthBridge.verificationState(context, false))
            NativeAuthBridge.rememberAutoOpen(context, request)
            assertFalse(NativeAuthBridge.verificationState(context, true)!!.getBoolean("automatic"))
            request.put("created", System.currentTimeMillis() - 1_800_001)
            NativeAuthBridge.write(File(dir, "auth-request.json"), request)
            assertNull(NativeAuthBridge.verificationState(context, true))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun publicSubmissionWaitsForConfirmationAndThenOffersRetryWithoutAutomaticLoop() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "verification-submit-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        val request = JSONObject().put("requestId", "test-id").put("attempt", "test-attempt")
            .put("transport", "yandex-1").put("created", System.currentTimeMillis())
            .put("url", "https://disk.yandex.ru/i/test-only").put("submitted", true)
            .put("submittedAt", System.currentTimeMillis())
        try {
            NativeAuthBridge.write(File(dir, "auth-request.json"), request)
            val checking = requireNotNull(NativeAuthBridge.verificationState(context, true))
            assertTrue(checking.getBoolean("checking"))
            assertFalse(checking.getBoolean("retry"))
            assertFalse(checking.getBoolean("automatic"))
            request.put("submittedAt", System.currentTimeMillis() - 31_000)
            NativeAuthBridge.write(File(dir, "auth-request.json"), request)
            val retry = requireNotNull(NativeAuthBridge.verificationState(context, true))
            assertFalse(retry.getBoolean("checking"))
            assertTrue(retry.getBoolean("retry"))
            assertFalse(retry.getBoolean("automatic"))
            File(dir, "auth-request.json").delete()
            assertNull(NativeAuthBridge.verificationState(context, true))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun journalPersistsVerificationAsWarningEvenWhenVpnIsConnected() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "verification-journal-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        try {
            SessionJournal.append(context, "Яндекс: требуется проверка — документ 2, телефон", "success", "connection", "CONNECTED")
            assertEquals("warning", JSONObject(SessionJournal.read(context)).getJSONArray("events").getJSONObject(0).getString("level"))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun acceptedDocumentAndRemainingAuxiliaryCheckCannotReplaceHealthyTunnelStatus() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "verification-lifecycle-${System.nanoTime()}").apply { mkdir() }
        val context = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        try {
            SessionJournal.append(context, YandexCheckPresentation.accepted("документ 1", "телефон"), "info", "system", "Система")
            SessionJournal.append(context, YandexCheckPresentation.required("документ 2", "телефон", true), "info", "system", "Система")
            SessionJournal.append(context, YandexCheckPresentation.HEALTHY, "success", "connection", "CONNECTED")
            val events = JSONObject(SessionJournal.read(context)).getJSONArray("events")
            assertEquals("success", events.getJSONObject(0).getString("level"))
            assertFalse(events.getJSONObject(0).getString("message").contains("проверка"))
            assertEquals("warning", events.getJSONObject(1).getString("level"))
            assertTrue(events.getJSONObject(1).getString("message").contains("документ 2"))
            assertTrue(events.getJSONObject(1).getString("message").contains("VPN работает"))
            assertEquals("info", events.getJSONObject(2).getString("level"))
            assertTrue(events.getJSONObject(2).getString("message").contains("документ 1"))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun newIdsStaySuppressedAcrossReadersButOtherChannelsAndAttemptsDoNot() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "auth-opened-${System.nanoTime()}").apply { mkdir() }
        fun reader() = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        fun pending(id: String, transport: String = "yandex-1", remote: Boolean = false, attempt: String = "attempt") =
            JSONObject().put("requestId", id).put("transport", transport).put("remote", remote).put("attempt", attempt)
        try {
            NativeAuthBridge.rememberAutoOpen(reader(), pending("first-id"))
            assertTrue(NativeAuthBridge.wasAutoOpened(reader(), pending("different-id")))
            assertFalse(NativeAuthBridge.wasAutoOpened(reader(), pending("other", transport = "yandex-2")))
            assertFalse(NativeAuthBridge.wasAutoOpened(reader(), pending("other", remote = true)))
            assertFalse(NativeAuthBridge.wasAutoOpened(reader(), pending("other", attempt = "new-attempt")))
            // Reading history does not acknowledge/delete a pending request.
            val requestFile = File(dir, "auth-request.json")
            NativeAuthBridge.write(requestFile, pending("different-id"))
            assertTrue(NativeAuthBridge.wasAutoOpened(reader(), NativeAuthBridge.read(requestFile)!!))
            assertEquals("different-id", NativeAuthBridge.read(requestFile)!!.getString("requestId"))
            val file = File(dir, "auth-auto-opened.json")
            assertTrue(file.renameTo(File(dir, "auth-auto-opened.json.bak")))
            assertTrue(NativeAuthBridge.wasAutoOpened(reader(), pending("different-id")))
            repeat(100) { NativeAuthBridge.rememberAutoOpen(reader(), pending("id-$it", attempt = "attempt-$it")) }
            assertEquals(64, NativeAuthBridge.read(file)!!.length())
            assertTrue(NativeAuthBridge.wasAutoOpened(reader(), pending("new-id", attempt = "attempt-99")))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun cancellationCooldownIsFreshAcrossReadersAndScopedToChannelSideAndAttempt() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(base.cacheDir, "auth-dismissal-${System.nanoTime()}").apply { mkdir() }
        fun reader() = object : ContextWrapper(base) { override fun getNoBackupFilesDir(): File = dir }
        fun pending(transport: String, remote: Boolean = false, attempt: String = "test-attempt") =
            JSONObject().put("transport", transport).put("remote", remote).put("attempt", attempt)
        try {
            NativeAuthBridge.rememberAutoOpenDismissal(reader(), pending("yandex-1"))
            NativeAuthBridge.rememberAutoOpenDismissal(reader(), pending("volga-1"))
            val context = reader()
            assertTrue(NativeAuthBridge.autoOpenDismissedUntil(context, pending("yandex-1")) > System.currentTimeMillis())
            assertTrue(NativeAuthBridge.autoOpenDismissedUntil(context, pending("volga-1")) > System.currentTimeMillis())
            assertEquals(0L, NativeAuthBridge.autoOpenDismissedUntil(context, pending("yandex-2")))
            assertEquals(0L, NativeAuthBridge.autoOpenDismissedUntil(context, pending("yandex-1", remote = true)))
            assertEquals(0L, NativeAuthBridge.autoOpenDismissedUntil(context, pending("yandex-1", attempt = "new-attempt")))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
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
    @Test fun submittedCheckRemainsPendingAndRepeatedRequestPreservesPhase() {
        val queue = AuthRequestQueue()
        queue.accept(request("check", false))
        queue.accept(request("next", true))
        assertNull(queue.submitted("stale"))
        assertTrue(queue.submitted("check")!!.optBoolean("submitted"))
        queue.accept(request("check", false))
        assertEquals("next", queue.current()!!.getString("requestId"))
        queue.acknowledge("next")
        assertEquals("check", queue.current()!!.getString("requestId"))
        assertTrue(queue.current()!!.optBoolean("submitted"))
        queue.acknowledge("check")
        assertNull(queue.current())
    }
    @Test fun freshRequestPreemptsSubmittedButDoesNotAcknowledgeIt() {
        val queue = AuthRequestQueue()
        queue.accept(request("waiting", false))
        queue.submitted("waiting")
        assertTrue(queue.accept(request("fresh", true)))
        assertEquals("fresh", queue.current()!!.getString("requestId"))
        queue.acknowledge("fresh")
        assertEquals("waiting", queue.current()!!.getString("requestId"))
    }
    @Test fun onePhoneServerDocumentPairHasPriorityOverEveryLocalDocument() {
        val queue = AuthRequestQueue()
        queue.accept(request("local-1", false).put("transport", "yandex-1"))
        queue.accept(request("local-2", false).put("transport", "yandex-2"))
        queue.accept(request("server-1", true).put("transport", "yandex-1"))
        queue.acknowledge("local-1")
        assertEquals("server-1", queue.current()!!.getString("requestId"))
        queue.acknowledge("server-1")
        assertEquals("local-2", queue.current()!!.getString("requestId"))
    }
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
    @Test fun everyAcceptedDocumentIsReportedBeforeAdvancingAndOnlyOnce() {
        val queue = AuthRequestQueue()
        queue.accept(request("document-1", false))
        queue.accept(request("volga", false))
        queue.accept(request("document-2", true))
        val accepted = mutableListOf<String>()
        val active = mutableListOf<String?>()
        fun ack(id: String) = queue.acknowledgeResult(id,
            { accepted.add(it.getString("requestId")) },
            { active.add(queue.current()?.getString("requestId")) })
        ack("document-1")
        assertEquals(listOf("document-1"), accepted)
        assertEquals(listOf("volga"), active)
        ack("document-1"); ack("stale")
        assertEquals(1, accepted.size)
        assertEquals(1, active.size)
        ack("document-2") // A non-visible document may also finish first.
        assertEquals(listOf("document-1", "document-2"), accepted)
        assertEquals(1, active.size)
        ack("volga")
        assertEquals(listOf("document-1", "document-2", "volga"), accepted)
        assertEquals(listOf("volga", null), active)
        assertNull(queue.current())
    }
}
