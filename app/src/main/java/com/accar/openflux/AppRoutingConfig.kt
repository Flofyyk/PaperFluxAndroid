package com.accar.openflux

data class AppRoutingConfig(
    val mode: String = EXCLUDE,
    val excluded: Set<String> = emptySet(),
    val included: Set<String> = emptySet(),
) {
    init { require(mode == EXCLUDE || mode == INCLUDE) { "Неизвестный режим приложений" } }
    val selected: Set<String> get() = if (mode == INCLUDE) included else excluded
    fun usableSelection(ownPackage: String, installed: (String) -> Boolean): Set<String> =
        selected.filter { it != ownPackage && installed(it) }.toSortedSet()
    companion object {
        const val EXCLUDE = "exclude"
        const val INCLUDE = "include"
    }
}
