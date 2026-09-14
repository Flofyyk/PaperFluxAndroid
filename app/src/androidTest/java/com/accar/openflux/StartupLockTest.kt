package com.accar.openflux

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartupLockTest {
    @Test fun nativeStatsDoNotWaitForStartupMonitor() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir() = java.io.File(target.cacheDir, "startup-lock-test").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("startup-lock-test-$name", mode)
        }
        val service = OpenFluxVpnService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
            invoke(service, context)
        }
        val accept = OpenFluxVpnService::class.java.getDeclaredMethod(
            "acceptNativeStats", Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val executor = Executors.newSingleThreadExecutor()
        try {
            synchronized(service) {
                // Simulate startup waiting for READY while a stats line arrives.
                executor.submit { accept.invoke(service, 1L, 2L, 3L) }.get(3, TimeUnit.SECONDS)
            }
        } finally { executor.shutdownNow() }
    }
}
