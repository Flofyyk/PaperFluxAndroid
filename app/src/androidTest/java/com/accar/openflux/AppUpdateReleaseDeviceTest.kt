package com.accar.openflux

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateReleaseDeviceTest {
    private fun release(name: String = "PaperFlux-v0.4.28-arm64-v8a.apk", tag: String = "v0.4.28") = JSONObject()
        .put("tag_name", tag).put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", name).put("size", 1024)
            .put("digest", "sha256:" + "a".repeat(64))
            .put("browser_download_url", "https://github.com/Flofyyk/PaperFluxAndroid/releases/download/$tag/$name")))

    @Test fun selectsOfficialMatchingAbiAndIgnoresOldOrBeta() {
        assertEquals("PaperFlux-v0.4.28-arm64-v8a.apk", GitHubAppUpdater.parseRelease(release(), "0.4.27", listOf("arm64-v8a"))?.name)
        assertNull(GitHubAppUpdater.parseRelease(release(), "0.4.29", listOf("arm64-v8a")))
        assertNull(GitHubAppUpdater.parseRelease(release().put("prerelease", true), "0.4.27", listOf("arm64-v8a")))
        assertNull(GitHubAppUpdater.parseRelease(release().put("draft", true), "0.4.27", listOf("arm64-v8a")))
        assertEquals("PaperFlux-v0.4.28-universal.apk", GitHubAppUpdater.parseRelease(release("PaperFlux-v0.4.28-universal.apk"), "0.4.27", listOf("x86_64"))?.name)
    }
    @Test fun rejectsForeignUrlMissingHashOversizedAndDuplicateAssets() {
        val foreign = release().apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.test/file.apk") }
        val missingHash = release().apply { getJSONArray("assets").getJSONObject(0).remove("digest") }
        val huge = release().apply { getJSONArray("assets").getJSONObject(0).put("size", GitHubAppUpdater.MAX_APK_SIZE + 1) }
        val duplicate = release().apply { getJSONArray("assets").put(getJSONArray("assets").getJSONObject(0)) }
        for (json in listOf(foreign, missingHash, huge, duplicate)) {
            assertThrows(Exception::class.java) { GitHubAppUpdater.parseRelease(json, "0.4.27", listOf("arm64-v8a")) }
        }
    }
}
