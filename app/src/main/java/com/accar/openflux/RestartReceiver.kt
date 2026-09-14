package com.accar.openflux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the foreground VPN after an OEM removes the recents task. */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(OpenFluxVpnService.PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("state", "") != "CONNECTED") return
        val service = Intent(context, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START)
            .putExtra(OpenFluxVpnService.EXTRA_DOCUMENT_URL, prefs.getString("document", ""))
            .putExtra(OpenFluxVpnService.EXTRA_RESUME, true)
        ContextCompat.startForegroundService(context, service)
    }
}
