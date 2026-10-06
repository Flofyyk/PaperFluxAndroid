package com.accar.openflux

internal object ProfileAddress {
    fun valid(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (parts.zip(octets).any { (text, number) -> text != number.toString() }) return false
        return octets[0] == 10 && octets[1] == 10 && octets[2] in 10..89 && octets[3] in 2..254 &&
            (octets[2] - 10) * 253 + octets[3] - 2 < 20000
    }
}
