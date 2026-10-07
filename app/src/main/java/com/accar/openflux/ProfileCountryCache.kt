package com.accar.openflux

import android.content.Context
import java.util.Locale

/** A successful country lookup is display metadata, not live network health. */
internal class ProfileCountryCache(context: Context) {
    private val cache = context.getSharedPreferences("profile-country-cache", Context.MODE_PRIVATE)
    fun forServer(server: String): String? = valid(cache.getString("server-${server.trim().lowercase(Locale.ROOT)}", null))
    fun forAddress(address: String): String? = valid(cache.getString("country-$address", null))
    fun remember(server: String, address: String, code: String): String {
        val country = forServer(server) ?: requireNotNull(valid(code))
        cache.edit().putString("server-${server.trim().lowercase(Locale.ROOT)}", country)
            .putString("country-$address", country).apply()
        return country
    }
    private fun valid(code: String?) = code?.takeIf { it.matches(Regex("[A-Z]{2}")) }
}
