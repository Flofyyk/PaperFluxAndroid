package com.accar.openflux

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.net.InetAddress
import java.net.Inet4Address
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Public-IP geolocation and a bounded ICMP echo to the server, never to the VPN tunnel. */
internal class ProfileDiagnostics(private val context: Context) {
    fun inspect(id: String, ping: Boolean): JSONObject {
        val result = JSONObject().put("id", id)
        val server = ProfileStore(context).find(id).optString("server").trim().removePrefix("[").removeSuffix("]")
        val network = underlyingNetwork() ?: return result.put("error", "Нет доступной сети")
        val address = runCatching {
            val resolved = network.getAllByName(server)
            resolved.firstOrNull { it is Inet4Address } ?: resolved.firstOrNull()
        }.getOrNull()
            ?: return result.put("error", "Адрес сервера не найден")
        result.put("address", address.hostAddress)
        countryCode(network, address)?.let { result.put("countryCode", it) }
        if (!ping) return result
        result.put("latencyMs", 0)
        val echo = runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-n", "-c", "1", "-W", "2", address.hostAddress).redirectErrorStream(true).start()
            if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); return@runCatching null }
            if (process.exitValue() != 0) return@runCatching null
            Regex("time[=<]([0-9]+(?:\\.[0-9]+)?)\\s*ms").find(process.inputStream.bufferedReader().use { it.readText().take(4096) })
                ?.groupValues?.get(1)?.toDoubleOrNull()?.let { kotlin.math.round(it).toLong().coerceAtLeast(1L) }
        }.getOrNull()
        if (echo != null) result.put("latencyMs", echo)
        else result.put("error", "ICMP-пинг не ответил; сервер может запрещать ping")
        return result
    }

    private fun underlyingNetwork(): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return manager.allNetworks.filter { network ->
            val caps = manager.getNetworkCapabilities(network)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }.maxByOrNull { network ->
            if (manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) 1 else 0
        }
    }

    private fun countryCode(network: Network, address: InetAddress): String? {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress ||
            address.isLinkLocalAddress || address.isMulticastAddress) return null
        val ip = address.hostAddress ?: return null
        val cache = context.getSharedPreferences("profile-country-cache", Context.MODE_PRIVATE)
        val key = "country-$ip"
        val saved = cache.getString(key, null)
        if (saved != null && System.currentTimeMillis() - cache.getLong("time-$ip", 0L) < 30L * 24 * 3600 * 1000) {
            return saved.takeIf { it.matches(Regex("[A-Z]{2}")) }
        }
        // Only the public server IP is sent; no profile ID, name or credential.
        val connection = network.openConnection(URL("https://ipwho.is/$ip?fields=success,country_code")) as? HttpsURLConnection ?: return null
        return try {
            connection.connectTimeout = 1500
            connection.readTimeout = 1500
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { it.readText().take(256) }
            val response = JSONObject(body)
            if (!response.optBoolean("success")) return null
            val code = response.optString("country_code").uppercase()
            if (!code.matches(Regex("[A-Z]{2}"))) return null
            cache.edit().putString(key, code).putLong("time-$ip", System.currentTimeMillis()).apply()
            code
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }
}
