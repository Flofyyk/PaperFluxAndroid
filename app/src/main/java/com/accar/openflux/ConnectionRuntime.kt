package com.accar.openflux

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

/** Both endpoints share the private engine process; only the VPN endpoint establishes TUN. */
object ConnectionRuntime {
    const val PROXY_ADDRESS = "127.0.0.1:1080"
    fun service(context: Context): Class<out OpenFluxVpnService> =
        if (NetworkSettingsStore(context).read().connectionMode == "proxy") OpenFluxProxyService::class.java
        else OpenFluxVpnService::class.java
    fun intent(context: Context, action: String): Intent = Intent(context, service(context)).setAction(action)
    @Suppress("DEPRECATION")
    fun running(context: Context): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE).any { it.service.className in setOf(
            OpenFluxVpnService::class.java.name, OpenFluxProxyService::class.java.name) }
}
