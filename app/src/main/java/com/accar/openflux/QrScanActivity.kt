package com.accar.openflux

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/** Local camera + decoder: no external app, Google services or image upload. */
class QrScanActivity : AppCompatActivity() {
    private lateinit var scanner: DecoratedBarcodeView
    private var resumed = false
    private var completed = false
    private var torch = false
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else finishError("Для QR-сканирования разрешите доступ к камере. Также можно импортировать конфиг из буфера или файла")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 7, 13))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(MaterialButton(this).apply { text = "Назад"; setOnClickListener { finish() } })
        header.addView(TextView(this).apply { text = "QR-код профиля"; textSize = 20f; setTextColor(Color.WHITE); setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        scanner = DecoratedBarcodeView(this).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            setStatusText("Наведите камеру на QR-код PaperFlux")
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    if (completed || !resumed) return
                    val value = result.text.orEmpty().trim()
                    if (!QrProfilePayload.accepts(value)) {
                        setStatusText("Это не конфигурация PaperFlux. Наведите камеру на QR-код профиля")
                        return
                    }
                    completed = true
                    pause()
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTENT, value))
                    finish()
                }
            })
        }
        scanner.barcodeView.addStateListener(object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = Unit
            override fun previewStopped() = Unit
            override fun cameraClosed() = Unit
            override fun cameraError(error: Exception) { finishError("Камера недоступна. Закройте другие приложения с камерой и повторите сканирование") }
        })
        root.addView(scanner, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(MaterialButton(this).apply {
            text = "Включить фонарик"
            isEnabled = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
            setOnClickListener {
                torch = !torch
                if (torch) scanner.setTorchOn() else scanner.setTorchOff()
                text = if (torch) "Выключить фонарик" else "Включить фонарик"
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) })
        root.addView(TextView(this).apply {
            text = "Распознавание выполняется на устройстве. Снимки не сохраняются и никуда не отправляются."
            textSize = 12f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(dp(20), 0, dp(20), dp(16))
        })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        if (!hasCameraPermission()) cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() { super.onResume(); resumed = true; startCamera() }
    override fun onPause() { resumed = false; scanner.pause(); super.onPause() }
    private fun startCamera() { if (resumed && !completed && hasCameraPermission()) scanner.resume() }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun finishError(message: String) {
        if (completed || isFinishing) return
        completed = true
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object { const val EXTRA_CONTENT = "qr-content"; const val EXTRA_ERROR = "qr-error" }
}

internal object QrProfilePayload {
    // QR data is untrusted. MainActivity still validates every field before saving.
    fun accepts(value: String): Boolean = value.length in 1..16_384 &&
        (value.startsWith("paperflux://config?") || runCatching {
            val json = org.json.JSONObject(value)
            json.has("id") && json.has("token") && (json.has("documentUrl") || json.has("documentUrls") || json.has("doc"))
        }.getOrDefault(false))
}
