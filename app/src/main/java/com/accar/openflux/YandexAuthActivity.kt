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
import androidx.activity.OnBackPressedCallback
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
    private var pageComplete = false
    private var submitted = false
    private var submittedAt = 0L
    private var checkingPage = false
    private val checkHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val automaticResultCheck = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (submitted && pending != null) {
                val live = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))
                if (live?.optString("requestId") != pending?.optString("requestId") || live?.optString("attempt") != pending?.optString("attempt")) {
                    finish(); return
                }
                if (android.os.SystemClock.elapsedRealtime() - submittedAt >= 30_000 && !pageFailed) {
                    showLoadError("Cookies переданы, но документ ещё не подключился. Повторите проверку; передача результата не подтверждает доступ.")
                }
            }
            val automaticHealthy = intent.getBooleanExtra("automatic", false) && TunnelSnapshot.read(this@YandexAuthActivity).optString("state") == "CONNECTED"
            if (automaticHealthy && !YandexCheckPresentation.shouldDeferAutomaticDismissal(
                    checkingPage, submitted, pending != null, pageComplete, pageFailed, isCheckpoint(currentUrl))) {
                // An alternative channel recovered. Do not keep a modal check
                // in front of a working VPN, and do not cancel its native request.
                finish(); return
            }
            // CAPTCHA can finish via AJAX without navigating. Inspect only
            // readiness; never click, solve or send an incomplete challenge.
            if (pending != null && !submitted && !pageFailed && pageComplete && currentUrl.isNotEmpty() && !isCheckpoint(currentUrl)) saveCookies(finishIfTunnelReady = automaticHealthy)
            checkHandler.postDelayed(this, 1500)
        }
    }
    private val directExecutor = Executor { it.run() }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("automatic", false) && TunnelSnapshot.read(this).optString("state") == "CONNECTED") { finish(); return }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { cancelCheck() }
        })
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { WebView.setDataDirectorySuffix("verification") }
        pending = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))?.takeIf {
            System.currentTimeMillis() - it.optLong("created") in 0..1_800_000 &&
                (intent.getStringExtra("request-id")?.let { id -> id == it.optString("requestId") } ?: true)
        }
        if (intent.hasExtra("request-id") && pending == null) { finish(); return }
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
        val carrier = YandexVerificationPolicy.carrierLabel(pending?.optString("transport").orEmpty())
        findViewById<TextView>(R.id.auth_origin).text = "${if (remote) "VPS" else "ТЕЛЕФОН"} · $carrier"
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
            submitted = false
            browser?.loadUrl(startUrl)
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
                    currentUrl = url; pageFailed = false; pageComplete = false
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
                    pageComplete = true
                    setStatus(if (isCheckpoint(url)) "Пройдите проверку на странице Яндекса" else if (pending != null) "Страница открыта. Проверяем результат…" else "Страница открыта. Нажмите «Я прошёл проверку»")
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
            if (YandexVerificationPolicy.shouldResetBrowserCookies(prefs.getString("route", "").orEmpty(), route)) CookieManager.getInstance().removeAllCookies {
                prefs.edit().putString("route", route).apply()
                runOnUiThread { if (!isFinishing) web.loadUrl(raw) }
            } else if (!isFinishing) {
                prefs.edit().putString("route", route).apply()
                web.loadUrl(raw)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (remote) ProxyController.getInstance().setProxyOverride(ProxyConfig.Builder().addProxyRule("http://$proxy").build(), directExecutor) { runOnUiThread { loadVerified() } }
            else ProxyController.getInstance().clearProxyOverride(directExecutor) { runOnUiThread { loadVerified() } }
        } else loadVerified()
        checkHandler.postDelayed(automaticResultCheck, 1500)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun setStatus(message: String, color: Int = Color.rgb(189, 183, 204)) {
        statusView?.text = message
        statusView?.setTextColor(color)
    }
    private fun showLoadError(message: String, retry: Boolean = true) {
        pageFailed = true
        pageComplete = false
        progressView?.visibility = View.GONE
        errorView?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.auth_error_text).text = message
        findViewById<View>(R.id.auth_retry).visibility = if (retry) View.VISIBLE else View.GONE
        setStatus("Не удалось открыть страницу", Color.rgb(255, 177, 177))
    }
    private fun isAllowedYandexUrl(uri: Uri): Boolean {
        return YandexVerificationPolicy.allows(uri.toString())
    }
    private fun requestRoute(remote: Boolean) = YandexVerificationPolicy.browserCookieRoute(pending?.optString("attempt").orEmpty(), remote)
    private fun saveCookies(finishIfTunnelReady: Boolean = false) {
        if (submitted || checkingPage) return
        if (!pageComplete) { setStatus("Дождитесь загрузки страницы Яндекса"); return }
        if (!YandexVerificationPolicy.allows(currentUrl) || isCheckpoint(currentUrl) || pageFailed) {
            setStatus("Сначала завершите проверку на странице Яндекса", Color.rgb(255, 177, 177))
            return
        }
        val web = browser ?: return
        val checkedUrl = currentUrl
        checkingPage = true
        // A challenge may be embedded at an ordinary document URL. URL alone
        // and pre-existing root cookies are not proof that it has been solved.
        // This returns a boolean only; no page data enters the app or logs.
        web.evaluateJavascript("""
            (function() {
                if (document.readyState !== 'complete') return false;
                if (/captcha|подтвердите,? что вы не робот|проверка безопасности/i.test(document.title)) return false;
                var nodes = document.querySelectorAll('.SmartCaptcha,.CheckboxCaptcha,.captcha,form[action*="checkcaptcha"],iframe[src*="smartcaptcha"]');
                for (var i = 0; i < nodes.length; i++) {
                    var style = getComputedStyle(nodes[i]);
                    if (nodes[i].getClientRects().length && style.visibility !== 'hidden' && style.display !== 'none') return false;
                }
                return true;
            })()
        """.trimIndent()) { result ->
            checkingPage = false
            if (isFinishing || isDestroyed || submitted || pageFailed || currentUrl != checkedUrl) return@evaluateJavascript
            if (result != "true") {
                setStatus("Завершите проверку и дождитесь открытия документа")
                // The page was still a challenge. Keep its native request,
                // but do not block a healthy VPN with an auxiliary modal.
                if (finishIfTunnelReady && TunnelSnapshot.read(this).optString("state") == "CONNECTED") finish()
                return@evaluateJavascript
            }
            persistCookies()
        }
    }
    private fun persistCookies() {
        val jar = CookieManager.getInstance()
        pending?.let { request ->
            val live = NativeAuthBridge.read(File(noBackupFilesDir, "auth-request.json"))
            if (live?.optString("attempt") != request.optString("attempt") || live.optString("requestId") != request.optString("requestId")) {
                setStatus("Эта проверка уже завершена. Откройте актуальную", Color.rgb(255, 177, 177))
                browser?.postDelayed({ finish() }, 1200); return
            }
            val values = JSONObject()
            jar.flush()
            YandexVerificationPolicy.collect(startUrl, currentUrl, jar::getCookie).forEach { (name, value) -> values.put(name, value) }
            if (values.length() == 0) { setStatus("Сначала пройдите проверку Яндекса", Color.rgb(255, 177, 177)); return }
            val offer = JSONObject().put("attempt", request.optString("attempt")).put("requestId", request.optString("requestId")).put("transport", request.optString("transport"))
                .put("remote", request.optBoolean("remote")).put("jar", values)
            YandexVerificationPolicy.cookieDomain(currentUrl)?.let { offer.put("domain", it) }
            runCatching { NativeAuthBridge.write(File(noBackupFilesDir, "auth-offer.json"), offer); jar.flush() }
                .onSuccess { submitted = true; submittedAt = android.os.SystemClock.elapsedRealtime(); setStatus("Результат передан. Ждём подтверждения доступа к документу", Color.rgb(255, 213, 128)) }
                .onFailure { setStatus("Не удалось передать результат. Попробуйте ещё раз", Color.rgb(255, 177, 177)) }
            return
        }
        val values = JSONObject()
        YandexVerificationPolicy.collect(startUrl, currentUrl, jar::getCookie).forEach { (name, value) -> values.put(name, value) }
        if (values.length() == 0) { setStatus("Сначала пройдите проверку Яндекса", Color.rgb(255, 177, 177)); return }
        runCatching {
            NativeAuthBridge.savePreflightCookies(this, startUrl, values)
            jar.flush()
        }.onSuccess {
            submitted = true; setStatus("Проверка сохранена. Повторите подключение", Color.rgb(130, 220, 170)); browser?.postDelayed({ finish() }, 900)
        }.onFailure { setStatus("Не удалось сохранить проверку", Color.rgb(255, 177, 177)) }
    }
    private fun isCheckpoint(raw: String): Boolean {
        return YandexVerificationPolicy.isCheckpoint(raw)
    }
    private fun cancelCheck() {
        pending?.let { request ->
            runCatching { NativeAuthBridge.rememberAutoOpenDismissal(this, request) }
            NativeAuthBridge.write(File(noBackupFilesDir, "auth-command.json"), JSONObject()
                .put("attempt", request.optString("attempt")).put("action", "cancel-auth")
                .put("params", JSONObject().put("requestId", request.optString("requestId"))))
        }
        finish()
    }
    override fun onDestroy() {
        checkHandler.removeCallbacksAndMessages(null)
        val hadBrowser = browser != null
        browser?.stopLoading(); browser?.destroy(); browser = null
        if (hadBrowser && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) ProxyController.getInstance().clearProxyOverride(directExecutor) {}
        super.onDestroy()
    }
}
