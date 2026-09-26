package com.accar.openflux

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import org.json.JSONObject
import java.io.File

/** Isolated remote page: deliberately no JavaScript bridge. */
class YandexAuthActivity : AppCompatActivity() {
    private var browser: WebView? = null
    private var pending: JSONObject? = null
    private var startUrl = ""
    private var currentUrl = ""
    private var pageFailed = false
    private var submitted = false
    private val directExecutor = Executor { it.run() }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { WebView.setDataDirectorySuffix("verification") }
        pending = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))?.takeIf {
            System.currentTimeMillis() - it.optLong("created") in 0..300_000
        }
        val raw = pending?.optString("url")?.takeIf { it.isNotBlank() } ?: intent.getStringExtra("document").orEmpty()
        startUrl = raw
        val remote = pending?.optBoolean("remote") == true
        val proxy = pending?.optString("proxy").orEmpty()
        if (remote && (!proxy.matches(Regex("127\\.0\\.0\\.1:[0-9]{1,5}")) || !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))) {
            Toast.makeText(this, "Служебный канал сервера недоступен. Проверка напрямую не выполняется", Toast.LENGTH_LONG).show(); finish(); return
        }
        val uri = Uri.parse(raw)
        if (uri.scheme != "https" || uri.host != "disk.yandex.ru" || uri.userInfo != null || uri.port != -1) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        layout.addView(TextView(this).apply {
            text = if (remote) "Проверка Яндекса для сервера. Страница открыта через защищённый служебный канал с адреса VPS. После проверки результат отправится автоматически." else "Проверка Яндекса на телефоне. После проверки результат отправится автоматически, и подключение продолжится."
            setPadding(24, 16, 24, 16)
        })
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0"
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    currentUrl = url; pageFailed = false
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) pageFailed = true
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame) pageFailed = true
                }
                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    if (pending != null && !pageFailed && !isCheckpoint(url)) view.postDelayed({
                        if (!isFinishing && !pageFailed && currentUrl == url && !isCheckpoint(currentUrl)) saveCookies()
                    }, 1500)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val target = request.url
                    val host = target.host.orEmpty()
                    return target.scheme != "https" || target.userInfo != null || target.port != -1 ||
                        !(host == "yandex.ru" || host.endsWith(".yandex.ru") || host == "yandex.com" || host.endsWith(".yandex.com"))
                }
            }
        }
        browser = web
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(Button(this).apply { text = "Сохранить и закрыть"; setOnClickListener { saveCookies() } })
        layout.addView(Button(this).apply { text = "Отмена"; setOnClickListener { cancelCheck() } })
        setContentView(layout)
        val loadVerified = {
            val route = requestRoute(remote)
            val prefs = getSharedPreferences("verification-browser", MODE_PRIVATE)
            if (prefs.getString("route", "") != route) CookieManager.getInstance().removeAllCookies {
                prefs.edit().putString("route", route).apply()
                runOnUiThread { if (!isFinishing) web.loadUrl(raw) }
            } else if (!isFinishing) web.loadUrl(raw)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (remote) ProxyController.getInstance().setProxyOverride(ProxyConfig.Builder().addProxyRule("http://$proxy").build(), directExecutor) { runOnUiThread { loadVerified() } }
            else ProxyController.getInstance().clearProxyOverride(directExecutor) { runOnUiThread { loadVerified() } }
        } else loadVerified()
    }
    private fun requestRoute(remote: Boolean) = "${pending?.optString("attempt").orEmpty()}/$remote"
    private fun saveCookies() {
        if (submitted) return
        if (isCheckpoint(currentUrl) || pageFailed) {
            Toast.makeText(this, "Дождитесь загрузки страницы и завершите проверку Яндекса", Toast.LENGTH_LONG).show()
            return
        }
        val jar = CookieManager.getInstance()
        pending?.let { request ->
            val live = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))
            if (live?.optString("attempt") != request.optString("attempt") || live.optString("requestId") != request.optString("requestId")) {
                Toast.makeText(this, "Эта проверка уже завершена. Откройте актуальную проверку", Toast.LENGTH_LONG).show(); finish(); return
            }
            val values = JSONObject()
            jar.flush()
            for (cookieUrl in (listOf(startUrl, currentUrl) + listOf("disk.yandex.ru", "docs.yandex.ru", "yandex.ru").map { "https://$it/" }).distinct()) {
                jar.getCookie(cookieUrl).orEmpty().split(';').forEach { part ->
                    val split = part.indexOf('=')
                    if (split > 0) values.put(part.substring(0, split).trim(), part.substring(split + 1).trim())
                }
            }
            if (values.length() == 0) { Toast.makeText(this, "Сначала пройдите проверку Яндекса", Toast.LENGTH_LONG).show(); return }
            val offer = JSONObject().put("attempt", request.optString("attempt")).put("requestId", request.optString("requestId")).put("transport", request.optString("transport"))
                .put("remote", request.optBoolean("remote")).put("jar", values)
            runCatching { NativeAuthBridge.write(File(noBackupFilesDir, "auth-offer.json"), offer); jar.flush() }
                .onSuccess { submitted = true; Toast.makeText(this, "Результат передан. Ожидаем подтверждения соединения", Toast.LENGTH_LONG).show(); finish() }
                .onFailure { Toast.makeText(this, "Не удалось передать результат проверки", Toast.LENGTH_LONG).show() }
            return
        }
        val values = JSONObject()
        for (cookieUrl in (listOf(startUrl, currentUrl) + listOf("https://disk.yandex.ru/", "https://docs.yandex.ru/", "https://yandex.ru/")).distinct()) {
            jar.getCookie(cookieUrl).orEmpty().split(';').forEach { part ->
                val split = part.indexOf('=')
                if (split > 0) values.put(part.substring(0, split).trim(), part.substring(split + 1).trim())
            }
        }
        if (values.length() == 0) { Toast.makeText(this, "Сначала пройдите проверку Яндекса", Toast.LENGTH_LONG).show(); return }
        runCatching {
            NativeAuthBridge.savePreflightCookies(this, startUrl, values)
            jar.flush()
        }.onSuccess {
            Toast.makeText(this, "Сохранено. Повторите подключение", Toast.LENGTH_LONG).show(); finish()
        }.onFailure { Toast.makeText(this, "Не удалось сохранить проверку", Toast.LENGTH_LONG).show() }
    }
    private fun isCheckpoint(raw: String): Boolean {
        val uri = Uri.parse(raw)
        return raw.isBlank() || uri.path.orEmpty().contains("showcaptcha") || uri.host.orEmpty().startsWith("passport.")
    }
    private fun cancelCheck() {
        pending?.let { request ->
            NativeAuthBridge.write(File(noBackupFilesDir, "auth-command.json"), JSONObject()
                .put("attempt", request.optString("attempt")).put("action", "cancel-auth")
                .put("params", JSONObject().put("requestId", request.optString("requestId"))))
        }
        finish()
    }
    @Deprecated("Legacy back callback")
    override fun onBackPressed() { cancelCheck() }
    override fun onDestroy() {
        browser?.stopLoading(); browser?.destroy(); browser = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) ProxyController.getInstance().clearProxyOverride(directExecutor) {}
        super.onDestroy()
    }
}
