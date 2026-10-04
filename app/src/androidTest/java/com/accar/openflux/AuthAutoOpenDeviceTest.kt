package com.accar.openflux

import android.app.Instrumentation
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Uses the device's pending request, without editing documents, cookies or profiles.
 * The monitor intercepts opening WebView so the synthetic state cannot solve or cancel it. */
class AuthAutoOpenDeviceTest {
    @Test fun liveRequestDoesNotReopenAfterMemoryResetOrRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(ProfileStore(context).active()?.optString("transport", "yandex") == "yandex") {
            "Select the Yandex profile requiring verification for this device regression"
        }
        val began = System.currentTimeMillis()
        context.startForegroundService(Intent(context, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START))
        val deadline = android.os.SystemClock.elapsedRealtime() + 45_000
        var pending = false
        while (!pending && android.os.SystemClock.elapsedRealtime() < deadline) {
            pending = NativeAuthBridge.read(File(context.noBackupFilesDir, "auth-request.json"))?.optLong("created")?.let { it >= began } == true
            if (!pending) Thread.sleep(100)
        }
        assertTrue("A fresh real pending Yandex verification is required for this regression", pending)
        org.junit.Assume.assumeFalse("A working tunnel deliberately suppresses auxiliary checks",
            TunnelSnapshot.read(context).optString("state") == "CONNECTED")
        val opens = AtomicInteger()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != YandexAuthActivity::class.java.name) return null
                opens.incrementAndGet()
                return Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        fun resetGate(activity: MainActivity) {
            for ((field, value) in listOf("authAutoOpen" to YandexAuthAutoOpenPolicy(), "authLaunching" to false)) {
                MainActivity::class.java.getDeclaredField(field).apply { isAccessible = true }.set(activity, value)
            }
        }
        fun broadcast() = context.sendBroadcast(Intent(OpenFluxVpnService.ACTION_STATE).setPackage(context.packageName)
            .putExtra("state", "CONNECTED").putExtra("detail", "Device regression: healthy lane with pending verification"))
        fun awaitCount(expected: Int) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (opens.get() < expected && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(50)
            assertEquals("Verification launch count", expected, opens.get())
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(700)
                instrumentation.waitForIdleSync()
                awaitCount(1)
                // Even losing the Activity's in-memory gate must not allow
                // another window for this channel/side/connection attempt.
                scenario.onActivity { activity ->
                    assertTrue("Main window must have focus", activity.hasWindowFocus())
                    resetGate(activity)
                    broadcast()
                }
                broadcast(); instrumentation.waitForIdleSync(); Thread.sleep(150)
                assertEquals("Duplicate broadcast reopened verification", 1, opens.get())

                scenario.onActivity { resetGate(it) }
                scenario.moveToState(Lifecycle.State.STARTED)
                val backgroundCount = opens.get()
                broadcast(); instrumentation.waitForIdleSync(); Thread.sleep(150)
                assertEquals("Paused UI opened verification", backgroundCount, opens.get())
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                instrumentation.waitForIdleSync(); Thread.sleep(150)
                assertEquals("Resume/recreation reopened verification", backgroundCount, opens.get())
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
