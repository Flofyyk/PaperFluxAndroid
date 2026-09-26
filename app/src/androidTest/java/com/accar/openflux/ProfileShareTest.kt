package com.accar.openflux

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ProfileShareTest {
    @Test fun nativeShareShowsQrAndAllActionsWithoutExposingSecretAsText() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val profile = requireNotNull(ProfileStore(instrumentation.targetContext).active())
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ProfileShareActivity::class.java)
            .putExtra("profile-key", profile.getString("key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        try {
            instrumentation.runOnMainSync {
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                val views = descendants(activity.window.decorView)
                assertTrue("QR is missing", views.filterIsInstance<ImageView>().any { it.drawable != null })
                val labels = views.filterIsInstance<TextView>().map { it.text.toString() }
                listOf("Скопировать ссылку", "Поделиться ссылкой", "Поделиться QR-кодом", "Готово").forEach { assertTrue("Sharing action is missing", labels.contains(it)) }
                assertFalse("Password exposed as visible text", labels.any { it.contains(profile.getString("token")) })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }
}
