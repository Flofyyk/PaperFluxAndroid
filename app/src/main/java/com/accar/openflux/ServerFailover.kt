package com.accar.openflux

/** Sticky selection, bounded cooldowns, and no periodic chasing of a faster IP.
 * Called only for a failed WHOLE path; one document rotation is not a failure.
 * Moving to another exit creates new TCP connections, not live NAT migration.
 */
internal class ServerFailover {
    private val failures = mutableMapOf<String, Int>()
    private val unavailableUntil = mutableMapOf<String, Long>()
    private var selected: String? = null

    @Synchronized fun choose(keys: List<String>, now: Long): String? {
        require(keys.isNotEmpty())
        val current = selected
        if (current in keys && (unavailableUntil[current] ?: 0) <= now) return current
        val next = keys.firstOrNull { (unavailableUntil[it] ?: 0) <= now } ?: return null
        selected = next
        return next
    }
    @Synchronized fun failed(key: String, now: Long, definitive: Boolean = false) {
        val count = (failures[key] ?: 0) + 1
        failures[key] = count
        if (definitive || count >= 2) {
            unavailableUntil[key] = now + minOf(120_000L, 30_000L * minOf(count, 4))
            if (selected == key) selected = null
        }
    }
    @Synchronized fun healthy(key: String) { failures.remove(key); unavailableUntil.remove(key); selected = key }
    @Synchronized fun reset() { failures.clear(); unavailableUntil.clear(); selected = null }
}
