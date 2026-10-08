package com.accar.openflux

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** UI fixture never requests a fake download or changes the installed package. */
class AppUpdateUiDeviceTest {
    @Test fun dialogShowsProgressAndPersists24hSkip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("paperflux_app_updates", 0)
        val oldAttempt = if (prefs.contains("last_attempt")) prefs.getLong("last_attempt", 0L) else null
        val oldSnooze = if (prefs.contains("snoozed_until")) prefs.getLong("snoozed_until", 0L) else null
        // Suppress real network checks during the synthetic UI fixture.
        prefs.edit().putLong("last_attempt", System.currentTimeMillis()).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(1500)
                fun send(phase: String, percent: Int? = null) {
                    val event = JSONObject().put("phase", phase).put("version", "0.4.99").put("percent", percent ?: JSONObject.NULL)
                    js(scenario, "window.__paperFluxOnAppUpdate(${JSONObject.quote(event.toString())});'ok'")
                    Thread.sleep(100)
                }
                send("available")
                assertEquals("Доступно обновление", js(scenario, "document.getElementById('app-update-title').textContent"))
                assertTrue(js(scenario, "document.querySelector('[aria-labelledby=app-update-title]').textContent").contains("24 часа"))
                send("downloading", 42)
                assertEquals("42", js(scenario, "document.querySelector('[role=progressbar]').getAttribute('aria-valuenow')"))
                send("available")
                val started = System.currentTimeMillis()
                js(scenario, "Array.from(document.querySelectorAll('[aria-labelledby=app-update-title] button')).find(b=>b.textContent==='Пропустить').click();'ok'")
                Thread.sleep(100)
                val until = prefs.getLong("snoozed_until", 0L)
                assertTrue(until >= started + AppUpdatePolicy.SNOOZE_MS)
                assertTrue(until <= System.currentTimeMillis() + AppUpdatePolicy.SNOOZE_MS)
                assertEquals("closed", js(scenario, "document.getElementById('app-update-title')?'open':'closed'"))
                scenario.recreate(); Thread.sleep(1000)
                assertEquals(until, context.getSharedPreferences("paperflux_app_updates", 0).getLong("snoozed_until", 0L))
                assertEquals("closed", js(scenario, "document.getElementById('app-update-title')?'open':'closed'"))
            }
        } finally {
            prefs.edit().apply {
                if (oldAttempt == null) remove("last_attempt") else putLong("last_attempt", oldAttempt)
                if (oldSnooze == null) remove("snoozed_until") else putLong("snoozed_until", oldSnooze)
            }.commit()
        }
    }

    private fun js(scenario: ActivityScenario<MainActivity>, expression: String): String {
        val done = CountDownLatch(1)
        var result = ""
        scenario.onActivity { activity ->
            val field = MainActivity::class.java.getDeclaredField("designWeb").apply { isAccessible = true }
            (field.get(activity) as WebView).evaluateJavascript(expression) { raw -> result = JSONArray("[$raw]").getString(0); done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result
    }
}
