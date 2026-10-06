package com.accar.openflux

import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Opt-in, same-signature check of an in-place release update. No profile data is logged. */
class ReleaseUpgradeSmokeTest {
    @Test fun releaseRetainsProfilesAndPackagesExpectedCore() {
        val expected = InstrumentationRegistry.getArguments().getString("paperflux.profileStoreSha256") ?: ""
        assumeTrue("Explicit pre-update profile-store digest required", expected.matches(Regex("[0-9a-f]{64}")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = File(context.noBackupFilesDir, "profiles-v2.bin").readBytes()
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertTrue("Encrypted profile store changed during update", actual == expected)
        assertNotNull("Selected profile was not retained", ProfileStore(context).active())
        assertEquals("Release permits debugging", 0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("0.4.22", info.versionName)
        val native = File(context.applicationInfo.nativeLibraryDir, "libopenflux.so")
        val process = ProcessBuilder(native.absolutePath, "--version").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals("Native version command failed", 0, process.waitFor())
        assertTrue("Unexpected embedded core", output.lineSequence().any { it == "PaperFlux 0.5.13" })
    }
}
