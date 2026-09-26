package com.accar.openflux

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real bundled WebView without changing profiles or display settings. */
class ProfileLayoutTest {
    @Test fun simpleEditorFitsSmallViewportAndKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val originalProfile = ProfileStore(context).active()
        context.startService(Intent(context, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP))
        val stopDeadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (TunnelSnapshot.read(context).optString("state") != "DISCONNECTED" && android.os.SystemClock.elapsedRealtime() < stopDeadline) Thread.sleep(100)
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
            assertTrue("WebView response timed out", latch.await(5, TimeUnit.SECONDS))
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
        fun geometry(): JSONObject = JSONObject(js("""(function(){
            const f=document.querySelector('form[role=dialog]'), b=f.querySelector('button[type=submit]'), r=b.getBoundingClientRect();
            return {inputs:f.querySelectorAll('input').length,top:r.top,bottom:r.bottom,height:innerHeight,
              width:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth+1};
        })()"""))
        fun picture(name: String) {
            instrumentation.runOnMainSync {
                val bitmap = Bitmap.createBitmap(browser.width, browser.height, Bitmap.Config.ARGB_8888)
                browser.draw(Canvas(bitmap))
                val directory = activity.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)!!
                java.io.File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        try {
            waitFor("Array.from(document.querySelectorAll('button')).some(b=>b.textContent.trim()==='Профили')")
            js("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Профили').click()")
            waitFor("Array.from(document.querySelectorAll('button')).some(b=>b.textContent.trim()==='Добавить профиль')")
            js("Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='Добавить профиль').click()")
            waitFor("!!document.querySelector('form[role=dialog]')")
            assertEquals(3, geometry().getInt("inputs"))
            val originalWidth = browser.layoutParams.width
            val originalHeight = browser.layoutParams.height
            instrumentation.runOnMainSync {
                val density = activity.resources.displayMetrics.density
                browser.layoutParams = browser.layoutParams.apply { width = (320*density).toInt(); height = (480*density).toInt() }
            }
            Thread.sleep(500)
            val compact = geometry()
            assertFalse("Horizontal overflow on 320dp screen", compact.getBoolean("overflow"))
            assertTrue("Save is below viewport", compact.getDouble("bottom") <= compact.getDouble("height"))
            assertTrue("Save is above viewport", compact.getDouble("top") >= 0)
            picture("profile-compact.png")
            instrumentation.runOnMainSync { browser.settings.textZoom = 150 }
            Thread.sleep(300)
            val enlarged = geometry()
            assertTrue("Save is outside enlarged-text viewport", enlarged.getDouble("bottom") <= enlarged.getDouble("height"))
            picture("profile-large-text.png")
            instrumentation.runOnMainSync { browser.settings.textZoom = 100 }
            instrumentation.runOnMainSync { browser.layoutParams = browser.layoutParams.apply { width = originalWidth; height = originalHeight } }
            Thread.sleep(300)
            js("document.querySelector('input[type=password]').scrollIntoView({block:'center'})")
            Thread.sleep(150)
            val point = JSONObject(js("""(function(){const r=document.querySelector('input[type=password]').getBoundingClientRect();return {x:r.left+20,y:r.top+r.height/2,width:innerWidth};})()"""))
            val location = IntArray(2)
            instrumentation.runOnMainSync { browser.requestFocus(); browser.getLocationOnScreen(location) }
            val ratio = browser.width / point.getDouble("width")
            val x = (location[0] + point.getDouble("x")*ratio).toFloat()
            val y = (location[1] + point.getDouble("y")*ratio).toFloat()
            val touchTime = android.os.SystemClock.uptimeMillis()
            instrumentation.sendPointerSync(MotionEvent.obtain(touchTime, touchTime, MotionEvent.ACTION_DOWN, x, y, 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(touchTime, touchTime+80, MotionEvent.ACTION_UP, x, y, 0))
            js("document.querySelector('input[type=password]').focus()")
            waitFor("document.activeElement?.type==='password'")
            var imeVisible = false
            val imeDeadline = android.os.SystemClock.elapsedRealtime() + 5000
            while (!imeVisible && android.os.SystemClock.elapsedRealtime() < imeDeadline) {
                instrumentation.runOnMainSync {
                    imeVisible = ViewCompat.getRootWindowInsets(browser)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (!imeVisible) activity.getSystemService(InputMethodManager::class.java).showSoftInput(browser, InputMethodManager.SHOW_IMPLICIT)
                }
                Thread.sleep(150)
            }
            assertTrue("Keyboard did not open, so keyboard layout was not exercised", imeVisible)
            Thread.sleep(300)
            val keyboard = geometry()
            assertTrue("Save is obscured by keyboard", keyboard.getDouble("bottom") <= keyboard.getDouble("height"))
            assertTrue(keyboard.getDouble("top") >= 0)
            picture("profile-keyboard.png")
            if (originalProfile != null) {
                // Re-add the same real profile through the three visible fields.
                // Secrets are read only on-device; never embedded in test sources/logs.
                val values = org.json.JSONArray().put(originalProfile.getString("name"))
                    .put(originalProfile.getString("server")).put(originalProfile.getString("token"))
                val count = JSONObject(ProfileStore(context).publicState()).getJSONArray("profiles").length()
                js("""(function(){ const values=$values;
                    const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
                    document.querySelectorAll('form[role=dialog] input').forEach((input,i)=>{
                      setter.call(input,values[i]);input.dispatchEvent(new Event('input',{bubbles:true}));
                    }); })()""")
                Thread.sleep(150)
                js("document.querySelector('form[role=dialog] button[type=submit]').click()")
                waitFor("!document.querySelector('form[role=dialog]')")
                assertEquals("Re-adding created a duplicate profile", count, JSONObject(ProfileStore(context).publicState()).getJSONArray("profiles").length())
                assertTrue(ProfileStore(context).active()?.getString("token") == originalProfile.getString("token"))
            }
        } finally {
            instrumentation.runOnMainSync {
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(browser.windowToken, 0)
                activity.finish()
            }
            instrumentation.waitForIdleSync()
        }
    }
}
