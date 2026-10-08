package com.accar.openflux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/** Non-active tile: receive status only while the shade is visible. */
class PaperFluxTileService : TileService() {
    private var listening = false
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            if (!listening) return
            updateTile()
            handler.postDelayed(this, 1500) // also detects an abruptly killed VPN process
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateTile() }
    }
    override fun onStartListening() {
        super.onStartListening()
        if (!listening) {
            listening = true
            ContextCompat.registerReceiver(this, receiver, IntentFilter(OpenFluxVpnService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            handler.post(refresh)
        }
    }
    override fun onStopListening() {
        if (listening) {
            listening = false
            handler.removeCallbacks(refresh)
            unregisterReceiver(receiver)
        }
        super.onStopListening()
    }
    override fun onDestroy() { onStopListening(); super.onDestroy() }
    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }
    private fun updateTile() {
        val tile = qsTile ?: return
        val state = TunnelSnapshot.read(this).optString("state")
        tile.label = "PaperFlux"
        tile.state = if (VpnTilePolicy.active(state)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = VpnTilePolicy.subtitle(state)
        tile.contentDescription = "PaperFlux: ${VpnTilePolicy.subtitle(state)}"
        tile.updateTile()
    }
    private fun toggle() {
        val state = TunnelSnapshot.read(this).optString("state")
        val stop = VpnTilePolicy.active(state)
        if (!stop && (VpnService.prepare(this) != null || runCatching { ProfileStore(this).active() }.getOrNull() == null)) {
            openPermissionActivity()
            return
        }
        runCatching {
            // START fulfils the foreground-service deadline; STOP is a command
            // to an already running service, not a new foreground-service start.
            val intent = Intent(this, OpenFluxVpnService::class.java)
                .setAction(if (stop) OpenFluxVpnService.STOP else OpenFluxVpnService.START)
            if (stop) startService(intent) else ContextCompat.startForegroundService(this, intent)
        }.onFailure { openPermissionActivity() }
        updateTile()
    }
    // Intent overload is required below API 34; the PendingIntent overload
    // does not exist there. Never invoke the old overload on Android 14+.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openPermissionActivity() {
        val intent = Intent(this, VpnTilePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 927, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else startActivityAndCollapse(intent)
    }
}
