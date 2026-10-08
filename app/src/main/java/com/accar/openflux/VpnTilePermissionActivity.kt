package com.accar.openflux

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Non-exported trampoline for the system VPN permission from the tile. */
class VpnTilePermissionActivity : AppCompatActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpn() else finish()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // permission request survives recreation
        if (runCatching { ProfileStore(this).active() }.getOrNull() == null) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        val request = VpnService.prepare(this)
        if (request == null) startVpn() else permission.launch(request)
    }
    private fun startVpn() {
        runCatching { ContextCompat.startForegroundService(this,
            Intent(this, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START))
        }.onFailure { Toast.makeText(this, "Android не разрешил запуск VPN. Попробуйте из приложения", Toast.LENGTH_LONG).show() }
        finish()
    }
}
