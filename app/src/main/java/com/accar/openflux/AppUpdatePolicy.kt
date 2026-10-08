package com.accar.openflux

object AppUpdatePolicy {
    const val SNOOZE_MS = 24 * 60 * 60 * 1000L
    private val stable = Regex("v?(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})")
    fun version(raw: String): List<Int>? = stable.matchEntire(raw)?.groupValues?.drop(1)?.map { it.toInt() }
    fun newer(tag: String, current: String): Boolean {
        val next = version(tag) ?: return false
        val previous = version(current.substringBefore('-')) ?: return false
        for (i in next.indices) if (next[i] != previous[i]) return next[i] > previous[i]
        // An approved stable build supersedes a local test of the same version.
        return '-' in current
    }
    fun shouldOffer(now: Long, snoozedUntil: Long) = snoozedUntil <= now
    fun assetNames(tag: String, abis: List<String>): List<String> {
        require(version(tag) != null)
        val prefix = "PaperFlux-v${tag.removePrefix("v")}-"
        return (abis.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }.map { prefix + it + ".apk" } + (prefix + "universal.apk")).distinct()
    }
}
