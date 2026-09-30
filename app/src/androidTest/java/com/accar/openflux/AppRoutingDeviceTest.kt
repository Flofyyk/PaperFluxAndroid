package com.accar.openflux

import android.webkit.WebView
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in smoke test of the real WebView -> separate VPN process route update. */
class AppRoutingDeviceTest {
    @Test fun changeThroughWebBridge() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val packages = requireNotNull(args.getString("paperflux.routingPackages")) { "Supply the packages to change" }
            .split(',').filter { it.isNotBlank() }
        val context = instrumentation.targetContext
        context.startForegroundService(Intent(context, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START))
        Thread.sleep(1000)
        awaitConnected(context)
        val previousRoutes = routingEvents()
        val next = AppRoutingStore(context).read().toMutableSet()
        if (args.getString("paperflux.routingRemove") == "true") next.removeAll(packages.toSet()) else next.addAll(packages)
        val saved = CountDownLatch(1)
        var result = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("designWeb").apply { isAccessible = true }
                val web = field.get(activity) as WebView
                val json = JSONObject.quote(JSONArray(next.toList()).toString())
                web.evaluateJavascript("PaperFluxNative.setExcludedApps($json)") {
                    result = runCatching { JSONArray("[$it]").getString(0) }.getOrDefault(it)
                    saved.countDown()
                }
            }
            assertTrue("Web bridge did not answer", saved.await(10, TimeUnit.SECONDS))
            assertTrue(result, result.contains("Исключения сохранены"))
            assertEquals(next, AppRoutingStore(context).read())
            Thread.sleep(1500)
            awaitConnected(context)
            assertTrue("Active tunnel did not apply new routes", routingEvents() > previousRoutes)
            // Keep the instrumented package alive while an independent UID
            // checks its route; ending instrumentation stops package processes.
            val holdMs = args.getString("paperflux.holdMs")?.toLongOrNull()?.coerceIn(0, 90_000) ?: 0
            if (holdMs > 0) Thread.sleep(holdMs)
        }
    }

    private fun awaitConnected(context: android.content.Context) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 90_000
        while (TunnelSnapshot.read(context).optString("state") != "CONNECTED" && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(500)
        assertEquals(TunnelSnapshot.read(context).optString("detail"), "CONNECTED", TunnelSnapshot.read(context).optString("state"))
    }

    private fun routingEvents(): Int = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("logcat -d -s OpenFluxVpn:I").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
                reader.lineSequence().count { it.contains("Application routing applied: excluded=") }
            }
        }
}
