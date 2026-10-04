package com.accar.openflux

/** UI-only gate: never steal focus from another app or reopen a handled request. */
internal class YandexAuthAutoOpenPolicy(openedIds: List<String> = emptyList(), openedScopes: List<String> = emptyList()) {
    private val opened = LinkedHashSet(openedIds.takeLast(64))
    private val scopes = LinkedHashSet(openedScopes.takeLast(64))

    fun shouldOpen(id: String, created: Long, now: Long, foreground: Boolean,
                   vpnAlive: Boolean, dismissedUntil: Long, scope: String = "", scopePreviouslyOpened: Boolean = false,
                   vpnConnected: Boolean = false): Boolean =
        foreground && vpnAlive && !vpnConnected && id.isNotBlank() && id !in opened &&
            !scopePreviouslyOpened && (scope.isEmpty() || scope !in scopes) &&
            created > 0 && now >= created && now - created <= 1_800_000 && now >= dismissedUntil

    fun markOpened(id: String, scope: String = "") {
        opened.add(id)
        while (opened.size > 64) opened.remove(opened.first())
        if (scope.isNotEmpty()) scopes.add(scope)
        while (scopes.size > 64) scopes.remove(scopes.first())
    }

    fun openedIds(): ArrayList<String> = ArrayList(opened)
    fun openedScopes(): ArrayList<String> = ArrayList(scopes)

    companion object {
        fun scope(attempt: String, transport: String, remote: Boolean): String = "$attempt|$remote|$transport"
    }
}
