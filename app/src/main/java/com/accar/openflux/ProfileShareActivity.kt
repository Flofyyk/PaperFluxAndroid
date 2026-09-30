package com.accar.openflux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.util.UUID

class ProfileShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sharing is explicit; keep the secret out of screenshots/Recents.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val profile = runCatching { ProfileStore(this).find(intent.getStringExtra("profile-key").orEmpty()) }.getOrElse {
            Toast.makeText(this, "Профиль не найден", Toast.LENGTH_SHORT).show(); finish(); return
        }
        val config = runCatching { ProfileConfig.export(profile) }.getOrElse {
            Toast.makeText(this, "Не удалось экспортировать профиль", Toast.LENGTH_SHORT).show(); finish(); return
        }
        setContentView(R.layout.activity_profile_share)
        findViewById<TextView>(R.id.share_name).text = profile.getString("name")
        findViewById<View>(R.id.share_close).setOnClickListener { finish() }
        val qr = runCatching { ProfileQr.bitmap(config) }.getOrNull()
        if (qr != null) {
            findViewById<ImageView>(R.id.share_qr).setImageBitmap(qr)
        } else {
            findViewById<View>(R.id.share_qr_card).visibility = View.GONE
            findViewById<View>(R.id.share_qr_unavailable).visibility = View.VISIBLE
            findViewById<View>(R.id.share_send_qr).visibility = View.GONE
        }
        findViewById<View>(R.id.share_copy).setOnClickListener {
            val clip = ClipData.newPlainText("PaperFlux профиль", config)
            if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.share_send).setOnClickListener {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, config), "Отправить конфигурацию"))
        }
        findViewById<View>(R.id.share_send_qr).setOnClickListener {
            if (qr == null) return@setOnClickListener
            runCatching {
                val directory = File(cacheDir, "shared-profiles").apply { mkdirs() }
                directory.listFiles()?.filter { it.name.startsWith("profile-") && System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
                val file = File(directory, "profile-${UUID.randomUUID()}.png")
                file.outputStream().use { check(qr.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val uri = FileProvider.getUriForFile(this, "$packageName.profile-files", file)
                val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.clipData = ClipData.newUri(contentResolver, "PaperFlux QR", uri)
                startActivity(Intent.createChooser(send, "Отправить QR-код"))
            }.onFailure { Toast.makeText(this, "Не удалось отправить QR-код", Toast.LENGTH_SHORT).show() }
        }
        val root = findViewById<View>(R.id.share_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}

internal object ProfileQr {
    fun bitmap(config: String): Bitmap {
        val matrix = QRCodeWriter().encode(config, BarcodeFormat.QR_CODE, 800, 800, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 3))
        val pixels = IntArray(matrix.width * matrix.height) { index -> if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}
