package com.accar.openflux

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.Gravity
import android.view.View
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
    private var statusView: TextView? = null
    private var progressView: ProgressBar? = null
    private var errorView: View? = null
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
            System.currentTimeMillis() - it.optLong("created") in 0..1_800_000
        }
        val raw = pending?.optString("url")?.takeIf { it.isNotBlank() } ?: intent.getStringExtra("document").orEmpty()
        startUrl = raw
        val remote = pending?.optBoolean("remote") == true
        val proxy = pending?.optString("proxy").orEmpty()
        val uri = Uri.parse(raw)
        if (!isAllowedYandexUrl(uri) || (pending == null && uri.host != "disk.yandex.ru")) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_yandex_auth)
        val root = findViewById<View>(R.id.auth_root)
        val content = findViewById<LinearLayout>(R.id.auth_content)
        if (resources.configuration.screenWidthDp >= 600) {
            content.layoutParams = FrameLayout.LayoutParams(dp(840), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        findViewById<TextView>(R.id.auth_origin).text = if (remote) "ДЛЯ СЕРВЕРА" else "ДЛЯ ТЕЛЕФОНА"
        findViewById<TextView>(R.id.auth_description).text = if (remote)
            "Яндекс запросил подтверждение для VPS. Страница открывается через защищённый служебный канал; после проверки соединение продолжится само."
        else "Яндекс запросил подтверждение на телефоне. Завершите его на странице ниже — результат передастся автоматически."
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            findViewById<TextView>(R.id.auth_description).maxLines = 2
        }
        findViewById<View>(R.id.auth_close).setOnClickListener { cancelCheck() }
        findViewById<Button>(R.id.auth_later).setOnClickListener { cancelCheck() }
        findViewById<Button>(R.id.auth_confirm).setOnClickListener { saveCookies() }
        findViewById<Button>(R.id.auth_retry).setOnClickListener {
            errorView?.visibility = View.GONE
            pageFailed = false
            browser?.reload()
        }
        statusView = findViewById(R.id.auth_status)
        progressView = findViewById(R.id.auth_loading)
        errorView = findViewById(R.id.auth_error)
        val web = findViewById<WebView>(R.id.auth_web).apply {
            settings.javaScriptEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0"
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, progress: Int) {
                    progressView?.progress = progress
                    progressView?.visibility = if (progress >= 100) View.GONE else View.VISIBLE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    currentUrl = url; pageFailed = false
                    errorView?.visibility = View.GONE
                    setStatus("Открываем страницу Яндекса…")
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) showLoadError("Страница не открылась. Проверьте интернет и попробуйте ещё раз.")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: android.webkit.WebResourceResponse) {
                    if (request.isForMainFrame) showLoadError("Яндекс не ответил на запрос. Попробуйте открыть страницу ещё раз.")
                }
                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    if (pageFailed) return
                    setStatus(if (isCheckpoint(url)) "Пройдите проверку на странице Яндекса" else if (pending != null) "Проверка завершена. Передаём результат…" else "Страница открыта. Нажмите «Я прошёл проверку»")
                    if (pending != null && !pageFailed && !isCheckpoint(url)) view.postDelayed({
                        if (!isFinishing && !pageFailed && currentUrl == url && !isCheckpoint(currentUrl)) saveCookies()
                    }, 1500)
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    return !isAllowedYandexUrl(request.url)
                }
            }
        }
        browser = web
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        if (remote && (!proxy.matches(Regex("127\\.0\\.0\\.1:[0-9]{1,5}")) || !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))) {
            showLoadError("Служебный канал сервера сейчас недоступен. Закройте окно и повторите попытку позже.", retry = false)
            return
        }
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
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun setStatus(message: String, color: Int = Color.rgb(189, 183, 204)) {
        statusView?.text = message
        statusView?.setTextColor(color)
    }
    private fun showLoadError(message: String, retry: Boolean = true) {
        pageFailed = true
        progressView?.visibility = View.GONE
        errorView?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.auth_error_text).text = message
        findViewById<View>(R.id.auth_retry).visibility = if (retry) View.VISIBLE else View.GONE
        setStatus("Не удалось открыть страницу", Color.rgb(255, 177, 177))
    }
    private fun isAllowedYandexUrl(uri: Uri): Boolean {
        val host = uri.host.orEmpty()
        return uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            (host == "yandex.ru" || host.endsWith(".yandex.ru") || host == "yandex.com" ||
                host.endsWith(".yandex.com") || host == "smartcaptcha.yandexcloud.net")
    }
    private fun requestRoute(remote: Boolean) = "${pending?.optString("attempt").orEmpty()}/$remote"
    private fun saveCookies() {
        if (submitted) return
        if (isCheckpoint(currentUrl) || pageFailed) {
            setStatus("Сначала завершите проверку на странице Яндекса", Color.rgb(255, 177, 177))
            return
        }
        val jar = CookieManager.getInstance()
        pending?.let { request ->
            val live = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))
            if (live?.optString("attempt") != request.optString("attempt") || live.optString("requestId") != request.optString("requestId")) {
                setStatus("Эта проверка уже завершена. Откройте актуальную", Color.rgb(255, 177, 177))
                browser?.postDelayed({ finish() }, 1200); return
            }
            val values = JSONObject()
            jar.flush()
            for (cookieUrl in (listOf(startUrl, currentUrl) + listOf("disk.yandex.ru", "docs.yandex.ru", "yandex.ru").map { "https://$it/" }).distinct()) {
                jar.getCookie(cookieUrl).orEmpty().split(';').forEach { part ->
                    val split = part.indexOf('=')
                    if (split > 0) values.put(part.substring(0, split).trim(), part.substring(split + 1).trim())
                }
            }
            if (values.length() == 0) { setStatus("Сначала пройдите проверку Яндекса", Color.rgb(255, 177, 177)); return }
            val offer = JSONObject().put("attempt", request.optString("attempt")).put("requestId", request.optString("requestId")).put("transport", request.optString("transport"))
                .put("remote", request.optBoolean("remote")).put("jar", values)
            runCatching { NativeAuthBridge.write(File(noBackupFilesDir, "auth-offer.json"), offer); jar.flush() }
                .onSuccess { submitted = true; setStatus("Результат передан. Соединение продолжится само", Color.rgb(130, 220, 170)); browser?.postDelayed({ finish() }, 900) }
                .onFailure { setStatus("Не удалось передать результат. Попробуйте ещё раз", Color.rgb(255, 177, 177)) }
            return
        }
        val values = JSONObject()
        for (cookieUrl in (listOf(startUrl, currentUrl) + listOf("https://disk.yandex.ru/", "https://docs.yandex.ru/", "https://yandex.ru/")).distinct()) {
            jar.getCookie(cookieUrl).orEmpty().split(';').forEach { part ->
                val split = part.indexOf('=')
                if (split > 0) values.put(part.substring(0, split).trim(), part.substring(split + 1).trim())
            }
        }
        if (values.length() == 0) { setStatus("Сначала пройдите проверку Яндекса", Color.rgb(255, 177, 177)); return }
        runCatching {
            NativeAuthBridge.savePreflightCookies(this, startUrl, values)
            jar.flush()
        }.onSuccess {
            submitted = true; setStatus("Проверка сохранена. Повторите подключение", Color.rgb(130, 220, 170)); browser?.postDelayed({ finish() }, 900)
        }.onFailure { setStatus("Не удалось сохранить проверку", Color.rgb(255, 177, 177)) }
    }
    private fun isCheckpoint(raw: String): Boolean {
        val uri = Uri.parse(raw)
        return raw.isBlank() || uri.path.orEmpty().contains("captcha", ignoreCase = true) ||
            uri.host.orEmpty().startsWith("passport.") || uri.host == "smartcaptcha.yandexcloud.net"
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
