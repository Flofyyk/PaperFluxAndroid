package com.accar.openflux

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** UI-only events; never writes pending requests, cookies, profiles or snapshots. */
class VerificationUiTest {
    @Test fun onlyWaitingConnectionShowsYellowCheckAndConnectedJournalStillUsesWarning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        var web: WebView? = null
        instrumentation.runOnMainSync { web = find(activity.window.decorView) }
        val browser = requireNotNull(web)
        fun js(source: String): String {
            val latch = CountDownLatch(1)
            var result = "null"
            instrumentation.runOnMainSync { browser.evaluateJavascript(source) { result = it; latch.countDown() } }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            return result
        }
        fun waitFor(source: String) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 8000
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (js(source) == "true") return
                Thread.sleep(100)
            }
            fail("UI condition timed out: $source")
        }
        try {
            waitFor("typeof window.__paperFluxOnState === 'function' && document.body.textContent.includes('Главная')")
            Thread.sleep(700)
            instrumentation.waitForIdleSync()
            js("window.__paperFluxOnState(JSON.stringify({state:'CONNECTED',detail:'Яндекс: требуется проверка — документ 2, телефон',verification:{carrier:'документ 2',side:'телефон',automatic:false}}))")
            waitFor("document.body.textContent.includes('Подключено')")
            assertEquals("true", js("document.querySelector('[data-verification-card]') === null"))
            js("window.__paperFluxOnState(JSON.stringify({state:'TRANSPORT',detail:'Яндекс: требуется проверка — документ 2, телефон',verification:{carrier:'документ 2',side:'телефон',automatic:true}}))")
            waitFor("document.querySelector('[data-verification-card]') !== null")
            assertEquals("true", js("document.querySelector('[data-verification-card]').textContent.includes('Окно проверки откроется автоматически')"))
            assertEquals("true", js("document.querySelector('[data-verification-card]').textContent.includes('документ 2 · телефон')"))
            assertEquals("true", js("getComputedStyle(document.querySelector('[data-verification-card] p')).color === 'rgb(255, 185, 92)'"))
            assertEquals("true", js("typeof PaperFluxNative.openVerification === 'function'"))
            js("window.__paperFluxOnState(JSON.stringify({rxBytes:1024,txBytes:512}))")
            assertEquals("true", js("document.querySelector('[data-verification-card]') !== null"))
            instrumentation.runOnMainSync {
                val bitmap = Bitmap.createBitmap(browser.width, browser.height, Bitmap.Config.ARGB_8888)
                browser.draw(Canvas(bitmap))
                File(activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "verification-warning-ui.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            js("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Журнал').click()")
            waitFor("document.body.textContent.includes('Журнал событий')")
            assertEquals("true", js("Array.from(document.querySelectorAll('.pf-log-row')).some(row=>row.textContent.includes('Яндекс: требуется проверка') && row.textContent.includes('WARN') && row.querySelector('p').classList.contains('text-warning'))"))
            js("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Главная').click()")
            js("window.__paperFluxOnState(JSON.stringify({state:'CONNECTED',verification:null}))")
            waitFor("document.querySelector('[data-verification-card]') === null")
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }
}
