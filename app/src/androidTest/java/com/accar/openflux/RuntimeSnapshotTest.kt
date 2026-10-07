package com.accar.openflux

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Read-only device report: no profile names, addresses, documents or keys. */
class RuntimeSnapshotTest {
    @Test fun reportSanitizedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val data = File(context.noBackupFilesDir, "profiles-v2.bin").readBytes()
        val snapshot = TunnelSnapshot.read(context)
        val active = ProfileStore(context).active()
        val request = NativeAuthBridge.read(File(context.noBackupFilesDir, "auth-request.json"))
        val report = JSONObject()
            .put("profilesSha256", MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) })
            .put("transport", active?.optString("transport"))
            .put("state", snapshot.optString("state"))
            .put("pendingCheck", request != null)
            .put("submittedCheck", request?.optBoolean("submitted") == true)
            .put("rxBytes", snapshot.optLong("rxBytes"))
            .put("txBytes", snapshot.optLong("txBytes"))
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("paperfluxReport", report.toString())
        })
    }
}
