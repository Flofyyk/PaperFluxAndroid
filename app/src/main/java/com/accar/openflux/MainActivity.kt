package com.accar.openflux

import android.animation.ObjectAnimator
import android.Manifest
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.Collator
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private var designWeb: WebView? = null
    @Volatile private var installedAppsCache: String? = null
    private data class StageWidget(
        val root: LinearLayout,
        val dot: TextView,
        val title: TextView,
        val subtitle: TextView,
        val state: TextView,
    )
    private lateinit var connectButton: MaterialButton
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var transportLink: TextInputEditText
    private lateinit var logText: TextView
    private lateinit var stages: Map<String, StageWidget>
    private val stageStates = mutableMapOf<String, String>()
    private val eventLines = ArrayDeque<String>()
    private var pulse: ObjectAnimator? = null
    private var spinner: ObjectAnimator? = null
    private var currentState = "DISCONNECTED"
    // Dark tokens from the supplied PaperFlux mock-up (.pf-dark).
    private val ink = Color.rgb(8, 7, 13)
    private val surface = Color.rgb(19, 18, 25)
    private val surfaceVariant = Color.rgb(38, 35, 46)
    private val outline = Color.rgb(72, 69, 78)
    private val primary = Color.rgb(195, 184, 255)
    private val primaryContainer = Color.rgb(66, 53, 147)
    private val onPrimary = Color.rgb(42, 26, 134)
    private val onSurface = Color.rgb(231, 226, 236)
    private val onSurfaceVariant = Color.rgb(202, 198, 210)

    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            requestNotificationThenStart()
        } else {
            if (::connectButton.isInitialized) renderState("ERROR", "VPN-разрешение не выдано")
            sendWebState("ERROR", "VPN-разрешение не выдано")
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        startOpenFlux()
    }
    private val profileFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val result = if (uri == null) {
            "Импорт отменён"
        } else {
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать файл")
                require(bytes.size <= 64 * 1024) { "Файл конфига слишком большой" }
                importProfileConfig(String(bytes, Charsets.UTF_8))
            }.getOrElse { "Ошибка импорта: ${it.message ?: "неверный файл"}" }
        }
        val web = designWeb
        if (web != null) web.evaluateJavascript("window.__paperFluxOnProfileFile&&window.__paperFluxOnProfileFile(${org.json.JSONObject.quote(result)})", null)
    }
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            sendWebState(intent.getStringExtra("state"), intent.getStringExtra("detail"), intent.getStringExtra("event"), intent.getLongExtra("rx", -1L), intent.getLongExtra("tx", -1L), intent.getLongExtra("ping", -1L), intent.getLongExtra("duration", -1L))
            if (designWeb != null) return
            intent.getStringExtra("event")?.let { addLog(it) }
            intent.getStringExtra("state")?.let { state ->
                renderState(state, intent.getStringExtra("detail") ?: "")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // App labels are cheap; icons are requested lazily by the WebView.
        Thread { installedAppsCache = installedAppsJson(includeIcons = false) }.start()
        buildWebUi()
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, stateReceiver, IntentFilter(OpenFluxVpnService.ACTION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
        val snapshot = TunnelSnapshot.read(this)
        sendWebState(snapshot.optString("state"), snapshot.optString("detail"), duration = snapshot.optLong("durationSec", -1L), rx = snapshot.optLong("rxBytes", -1L), tx = snapshot.optLong("txBytes", -1L), ping = snapshot.optLong("ping", -1L))
    }
    override fun onStop() { runCatching { unregisterReceiver(stateReceiver) }; super.onStop() }

    private fun buildWebUi() {
        val web = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            setBackgroundColor(Color.rgb(8, 7, 13))
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    ViewCompat.getRootWindowInsets(view)?.let { applyWebInsets(view, it) }
                    // The VPN service can finish authentication while the
                    // WebView is still loading. Replay the persisted state so
                    // the UI cannot remain stuck on the connecting spinner.
                    val snapshot = org.json.JSONObject(PaperFluxBridge().getState())
                    view.post { sendWebState(snapshot.optString("state", "DISCONNECTED"), snapshot.optString("detail"), duration = snapshot.optLong("durationSec", -1L), rx = snapshot.optLong("rxBytes", -1L), tx = snapshot.optLong("txBytes", -1L), ping = snapshot.optLong("ping", -1L)) }
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                    request.url.toString() != "file:///android_asset/paperflux/index.html"
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    android.util.Log.e("PaperFluxWeb", "${message.message()} @${message.lineNumber()}")
                    return true
                }
            }
            addJavascriptInterface(PaperFluxBridge(), "PaperFluxNative")
            loadUrl("file:///android_asset/paperflux/index.html")
        }
        designWeb = web
        ViewCompat.setOnApplyWindowInsetsListener(web) { view, insets ->
            (view as? WebView)?.let { applyWebInsets(it, insets) }
            insets
        }
        setContentView(web)
        ViewCompat.requestApplyInsets(web)
    }

    private fun applyWebInsets(web: WebView, insets: WindowInsetsCompat) {
        val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
        // WebView CSS pixels are density-independent while WindowInsets are
        // physical pixels. Passing the raw number made a 3x status inset on
        // high-density devices and pushed the whole header far down.
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        fun cssPx(value: Int) = (value / density).toInt()
        web.post {
            web.evaluateJavascript(
                "(function(root){if(!root)return;" +
                    "root.style.setProperty('--pf-inset-top','${cssPx(safe.top)}px');" +
                    "root.style.setProperty('--pf-inset-right','${cssPx(safe.right)}px');" +
                    "root.style.setProperty('--pf-inset-bottom','${cssPx(safe.bottom)}px');" +
                    "root.style.setProperty('--pf-inset-left','${cssPx(safe.left)}px');" +
                    "})(document.documentElement);", null)
        }
    }

    private fun sendWebState(state: String?, detail: String? = null, event: String? = null, rx: Long = -1L, tx: Long = -1L, ping: Long = -1L, duration: Long = -1L) {
        val web = designWeb ?: return
        val payload = org.json.JSONObject().apply {
            state?.let { put("state", it) }; detail?.let { put("detail", it) }; event?.let { put("log", it) }
            if (rx >= 0) put("rxBytes", rx); if (tx >= 0) put("txBytes", tx); if (ping >= 0) put("ping", ping)
            if (duration >= 0) put("durationSec", duration)
        }.toString()
        web.evaluateJavascript("window.__paperFluxOnState&&window.__paperFluxOnState(${org.json.JSONObject.quote(payload)})", null)
    }

    private fun requireProfilesIdle() {
        val snapshot = TunnelSnapshot.read(this)
        val vpnAlive = getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses?.any { it.processName == "$packageName:vpn" } == true
        check(snapshot.optString("state") == "DISCONNECTED" || (snapshot.optString("state") == "ERROR" && !vpnAlive)) {
            "Сначала отключите VPN"
        }
    }

    private fun profileFromConfig(rawValue: String): org.json.JSONObject {
        val raw = rawValue.trim()
        val uriStart = raw.indexOf("paperflux://config?")
        val source = if (uriStart >= 0) {
            val uri = android.net.Uri.parse(raw.substring(uriStart).lineSequence().first().trim())
            check(uri.scheme == "paperflux" && uri.host == "config") { "Нужна ссылка paperflux://config" }
            org.json.JSONObject()
                .put("id", uri.getQueryParameter("id"))
                .put("token", uri.getQueryParameter("token"))
                .put("clientIp", uri.getQueryParameter("ip"))
                .put("documentUrl", uri.getQueryParameter("doc"))
                .put("server", uri.getQueryParameter("server"))
                .put("name", uri.getQueryParameter("name") ?: "PaperFlux")
        } else {
            org.json.JSONObject(raw)
        }
        val id = source.optString("id").trim()
        val token = source.optString("token").trim()
        val documents = source.optJSONArray("documentUrls")?.let { values ->
            (0 until values.length()).map { values.optString(it).trim() }.filter { it.isNotEmpty() }
        } ?: splitDocumentUrls(source.optString("documentUrl", source.optString("doc")))
        val document = documents.joinToString(",")
        val clientIp = source.optString("clientIp", source.optString("ip")).trim()
        val server = source.optString("server").trim()
        val name = source.optString("name", "PaperFlux").trim().ifBlank { "PaperFlux" }
        check(id.matches(Regex("[1-9][0-9]*"))) { "В конфиге нет ID профиля" }
        check(token.length >= 32) { "Нужен токен доступа не короче 32 символов" }
        check(validDocumentUrls(documents)) { "Укажите одну или две ссылки Yandex Docs" }
        check(clientIp.matches(Regex("10\\.10\\.10\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])"))) { "Некорректный виртуальный IP" }
        return org.json.JSONObject().put("id", id).put("token", token).put("clientIp", clientIp)
            .put("documentUrl", document).put("documentUrls", org.json.JSONArray(documents)).put("server", server).put("name", name)
    }

    private fun importProfileConfig(value: String): String = try {
        requireProfilesIdle()
        val profile = profileFromConfig(value)
        ProfileStore(this).save(profile)
        "Профиль добавлен и выбран"
    } catch (e: Exception) {
        "Ошибка импорта: ${e.message ?: "неверный формат"}"
    }

    /** Manual input often arrives as two lines or is copied with semicolons. */
    private fun splitDocumentUrls(value: String): List<String> =
        value.split(Regex("[;,\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun validDocumentUrls(documents: List<String>): Boolean =
        documents.isNotEmpty() && documents.size <= 2 && documents.all { url ->
            runCatching {
                val uri = android.net.Uri.parse(url)
                uri.scheme == "https" && uri.host == "disk.yandex.ru" && !uri.path.isNullOrBlank()
            }.getOrDefault(false)
        }

    private inner class PaperFluxBridge {
        private fun requireIdle() = requireProfilesIdle()
        @JavascriptInterface fun getProfiles(): String {
            val store = ProfileStore(this@MainActivity)
            store.migrate()
            return store.publicState()
        }
        @JavascriptInterface fun selectProfile(id: String): String = try {
            requireIdle(); ProfileStore(this@MainActivity).select(id); "Профиль выбран"
        } catch (e: Exception) { e.message ?: "Ошибка профиля" }
        @JavascriptInterface fun deleteProfile(id: String): String = try {
            requireIdle(); ProfileStore(this@MainActivity).delete(id); "Профиль удалён"
        } catch (e: Exception) { e.message ?: "Ошибка профиля" }
        @JavascriptInterface fun connect() = runOnUiThread { requestConnect() }
        @JavascriptInterface fun disconnect() = runOnUiThread { startService(Intent(this@MainActivity, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP)) }
        @JavascriptInterface fun setAutoReconnect(enabled: Boolean) {
            getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putBoolean(OpenFluxVpnService.AUTO_RECONNECT, enabled).apply()
            startService(Intent(this@MainActivity, OpenFluxVpnService::class.java)
                .setAction(OpenFluxVpnService.UPDATE_SETTINGS).putExtra(OpenFluxVpnService.EXTRA_AUTO_RECONNECT, enabled))
        }
        @JavascriptInterface fun getAutoReconnect(): Boolean = getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE)
            .getBoolean(OpenFluxVpnService.AUTO_RECONNECT, true)
        @JavascriptInterface fun getDocumentUrl(): String = getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).getString("document", "") ?: ""
        @JavascriptInterface fun setDocumentUrl(value: String) {
            getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putString("document", value.trim()).apply()
        }
        @JavascriptInterface fun clearConfig() {
            getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit()
                .remove("document").remove("profile_name").remove("profile_id")
                .remove("profile_token").remove("client_ip").remove("server_ip").apply()
        }
        @JavascriptInterface fun getClipboardConfig(): String {
            val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            return cm?.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString()?.trim().orEmpty()
        }
        @JavascriptInterface fun importConfig(value: String): String {
            return importProfileConfig(value)
        }
        @JavascriptInterface fun pickConfigFile() = runOnUiThread {
            profileFilePicker.launch(arrayOf("text/plain", "application/json", "application/octet-stream"))
        }
        @JavascriptInterface fun updateProfile(raw: String): String = try {
            requireIdle()
            val value = org.json.JSONObject(raw)
            val id = value.optString("id").trim()
            val name = value.optString("name").trim().ifBlank { "PaperFlux" }
            val document = value.optString("documentUrl").trim()
            val clientIp = value.optString("clientIp").trim()
            val server = value.optString("server").trim()
            val token = value.optString("token").trim()
            check(id.matches(Regex("[1-9][0-9]*"))) { "Укажите ID профиля" }
            val documents = splitDocumentUrls(document)
            check(validDocumentUrls(documents)) { "Укажите одну или две ссылки Yandex Docs" }
            check(clientIp.matches(Regex("10\\.10\\.10\\.(?:[1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])"))) { "Укажите виртуальный IP" }
            val store = ProfileStore(this@MainActivity)
            val state = org.json.JSONObject(store.publicState())
            val exists = (0 until state.getJSONArray("profiles").length()).any { state.getJSONArray("profiles").getJSONObject(it).optString("id") == id }
            if (exists) {
                store.update(id, name, server, documents.joinToString(","), clientIp, token.ifBlank { null })
                "Профиль сохранён"
            } else {
                check(server.isNotBlank()) { "Укажите адрес сервера профиля" }
                check(token.length >= 32) { "Для нового профиля укажите токен доступа" }
                store.save(org.json.JSONObject().put("id", id).put("name", name).put("server", server)
                    .put("documentUrl", documents.joinToString(",")).put("documentUrls", org.json.JSONArray(documents))
                    .put("clientIp", clientIp).put("token", token))
                "Профиль создан и выбран"
            }
        } catch (e: Exception) { "Ошибка профиля: ${e.message ?: "проверьте поля"}" }
        @JavascriptInterface fun getState(): String = TunnelSnapshot.read(this@MainActivity).toString()
        @JavascriptInterface fun getSessionLogs(): String = SessionJournal.read(this@MainActivity)
        @JavascriptInterface fun clearSessionLogs() { SessionJournal.clear(this@MainActivity) }
        @JavascriptInterface fun getInstalledApps(): String = installedAppsCache ?: installedAppsJson(includeIcons = false)
        @JavascriptInterface fun getAppIcon(pkg: String): String = runCatching {
            drawableDataUri(packageManager.getApplicationIcon(pkg))
        }.getOrDefault("")
        @JavascriptInterface fun setExcludedApps(raw: String) {
            runCatching {
                val values = mutableSetOf<String>()
                val array = org.json.JSONArray(raw)
                for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(values::add)
                getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putStringSet(OpenFluxVpnService.EXCLUDED_APPS, values).apply()
                installedAppsCache = null
                Thread { installedAppsCache = installedAppsJson(includeIcons = false) }.start()
            }
        }
    }

    private fun installedAppsJson(includeIcons: Boolean): String {
        val excluded = getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).getStringSet(OpenFluxVpnService.EXCLUDED_APPS, emptySet()).orEmpty()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .sortedWith { left, right -> Collator.getInstance(Locale.getDefault()).compare(packageManager.getApplicationLabel(left).toString(), packageManager.getApplicationLabel(right).toString()) }
        val result = org.json.JSONArray()
        apps.forEach { app ->
            val obj = org.json.JSONObject()
            val label = packageManager.getApplicationLabel(app).toString()
            obj.put("id", app.packageName)
            obj.put("name", label)
            obj.put("pkg", app.packageName)
            obj.put("excluded", excluded.contains(app.packageName))
            obj.put("glyph", "◉")
            obj.put("color", "#5b4fe8")
            if (includeIcons) runCatching { obj.put("icon", drawableDataUri(packageManager.getApplicationIcon(app))) }
            result.put(obj)
        }
        return result.toString()
    }

    private fun drawableDataUri(drawable: Drawable): String {
        val width = drawable.intrinsicWidth.coerceIn(1, 64)
        val height = drawable.intrinsicHeight.coerceIn(1, 64)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildUi() {
        val appRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ink) }
        val pages = FrameLayout(this)
        val home = homePage()
        val logs = logsPage()
        val settings = settingsPage()
        pages.addView(home); pages.addView(logs); pages.addView(settings)
        logs.visibility = View.GONE; settings.visibility = View.GONE
        appRoot.addView(pages, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val nav = BottomNavigationView(this).apply {
            setBackgroundColor(surface)
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            val navigationColors = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.rgb(223, 225, 255), onSurfaceVariant))
            itemIconTintList = navigationColors
            itemTextColor = navigationColors
            itemActiveIndicatorColor = android.content.res.ColorStateList.valueOf(Color.rgb(51, 56, 102))
            menu.add(0, 1, 0, "Главная").setIcon(R.drawable.ic_home_outline)
            menu.add(0, 2, 1, "Журнал").setIcon(R.drawable.ic_history_outline)
            menu.add(0, 3, 2, "Настройки").setIcon(R.drawable.ic_tune_outline)
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    1 -> { home.visibility = View.VISIBLE; logs.visibility = View.GONE; settings.visibility = View.GONE }
                    2 -> { home.visibility = View.GONE; logs.visibility = View.VISIBLE; settings.visibility = View.GONE }
                    3 -> { home.visibility = View.GONE; logs.visibility = View.GONE; settings.visibility = View.VISIBLE }
                }
                true
            }
        }
        appRoot.addView(nav, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(appRoot)
    }

    private fun homePage(): ScrollView {
        val page = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(ink) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(22), dp(12), dp(22), dp(24)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_paperflux_mark); contentDescription = "PaperFlux" }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val wordmark = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, 0, 0) }
        wordmark.addView(TextView(this).apply { text = "PaperFlux"; textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) })
        wordmark.addView(TextView(this).apply { text = "Yandex Docs transport"; textSize = 10f; setTextColor(onSurfaceVariant) })
        header.addView(wordmark, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply { text = "YANDEX"; textSize = 9f; letterSpacing = .08f; setTextColor(Color.rgb(230, 223, 255)); background = rounded(primaryContainer, dp(14)); setPadding(dp(9), dp(6), dp(9), dp(6)) })
        body.addView(header, matchWrap())

        connectButton = MaterialButton(this).apply {
            text = ""; icon = getDrawable(R.drawable.ic_pf_shield); iconSize = dp(68); iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; cornerRadius = dp(84)
            background = connectBackground("DISCONNECTED"); contentDescription = "Подключить VPN"
            elevation = dp(10).toFloat(); setOnClickListener {
                // The control is a true power button: a second tap must be able
                // to cancel a slow bootstrap/authentication attempt, not only an
                // already connected tunnel.
                if (currentState in setOf("CONNECTING", "TUN", "TRANSPORT", "CONNECTED")) {
                    startService(Intent(this@MainActivity, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.STOP))
                } else {
                    requestConnect()
                }
            }
        }
        val connectOrb = FrameLayout(this).apply { background = rounded(primaryContainer, dp(96)); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        connectOrb.addView(connectButton, FrameLayout.LayoutParams(dp(168), dp(168), Gravity.CENTER))
        body.addView(connectOrb, LinearLayout.LayoutParams(dp(184), dp(184)).apply { topMargin = dp(26); gravity = Gravity.CENTER_HORIZONTAL })
        body.addView(TextView(this).apply { text = "Нажмите для подключения\nYandex Docs · Engine.IO transport"; textSize = 14f; setTextColor(onSurfaceVariant); gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(16), 0, dp(24)) }, matchWrap())

        val status = card(surface); val statusBody = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        statusBody.addView(ImageView(this).apply { setImageResource(R.drawable.ic_pf_shield_off); imageTintList = android.content.res.ColorStateList.valueOf(onSurfaceVariant); background = rounded(surfaceVariant, dp(18)); setPadding(dp(13), dp(13), dp(13), dp(13)); contentDescription = null }, LinearLayout.LayoutParams(dp(56), dp(56)))
        val statusText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        statusTitle = TextView(this).apply { textSize = 16f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) }
        statusDetail = TextView(this).apply { textSize = 13f; setTextColor(onSurfaceVariant); setPadding(0, dp(3), 0, 0) }
        statusText.addView(statusTitle); statusText.addView(statusDetail); statusBody.addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); status.addView(statusBody); body.addView(status, matchWrap())

        body.addView(sectionTitle("Канал передачи"))
        val channel = card(surface)
        val channelBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
        channelBody.addView(channelRow(R.drawable.ic_link_outline, "Yandex Docs", "Документ как транспортный контейнер"))
        channelBody.addView(channelRow(R.drawable.ic_history_outline, "Engine.IO transport", "WebSocket / polling fallback"), matchWrap(top = 14))
        channel.addView(channelBody); body.addView(channel, matchWrap())

        body.addView(sectionTitle("Этапы"))
        stages = linkedMapOf(
            "TUN" to stage("VPN-интерфейс", "Создание системного туннеля устройства"),
            "TRANSPORT" to stage("Yandex transport", "Установка Engine.IO соединения с Yandex Docs"),
            "CONNECTED" to stage("DNS и TCP", "Настройка маршрутов и проверка сети"),
        )
        stageStates.putAll(stages.keys.associateWith { "IDLE" })
        val stageCard = card(surface)
        val stageContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
        stages.values.forEachIndexed { index, widget ->
            stageContainer.addView(widget.root)
            if (index != stages.size - 1) stageContainer.addView(View(this).apply { setBackgroundColor(outline); alpha = .5f }, LinearLayout.LayoutParams(dp(2), dp(18)).apply { leftMargin = dp(15); topMargin = dp(3); bottomMargin = dp(3) })
        }
        stageCard.addView(stageContainer); body.addView(stageCard, matchWrap())
        body.addView(sectionTitle("Статистика сессии"))
        val statsCard = card(surface)
        val statsGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val firstStats = LinearLayout(this).apply { gravity = Gravity.CENTER }
        firstStats.addView(statTile("ВРЕМЯ", "—"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        firstStats.addView(statTile("PING", "—"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        val secondStats = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        secondStats.addView(statTile("ВХОДЯЩИЙ", "—"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        secondStats.addView(statTile("ИСХОДЯЩИЙ", "—"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        statsGrid.addView(firstStats); statsGrid.addView(secondStats); statsCard.addView(statsGrid); body.addView(statsCard, matchWrap())
        body.addView(MaterialButton(this).apply { text = "Проверить скорость"; icon = getDrawable(R.drawable.ic_history_outline); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; cornerRadius = dp(24); setTextColor(onPrimary); backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yandex.ru/internet/"))) } }, matchWrap(top = 20))
        page.addView(body); return page
    }

    private fun logsPage(): ScrollView {
        val page = ScrollView(this).apply { setBackgroundColor(ink) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = "Журнал"; textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) })
        body.addView(TextView(this).apply { text = "События подключения и причины ошибок"; textSize = 14f; setTextColor(onSurfaceVariant); setPadding(0, dp(8), 0, dp(16)) })
        body.addView(MaterialButton(this).apply { text = "Очистить журнал"; icon = getDrawable(android.R.drawable.ic_menu_delete); cornerRadius = dp(20); setTextColor(Color.rgb(255, 218, 214)); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(115, 36, 43)); setOnClickListener { eventLines.clear(); logText.text = "Журнал очищен" } }, matchWrap())
        body.addView(sectionTitle("Последние события"))
        val logCard = card(surface); logText = TextView(this).apply { text = "Готово. Нажмите подключение, чтобы начать новую сессию."; textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(onSurfaceVariant); setPadding(dp(20), dp(20), dp(20), dp(20)); minLines = 22 }; logCard.addView(logText); body.addView(logCard, matchWrap())
        body.addView(TextView(this).apply { text = "Токены, cookies и подписи не выводятся в журнал."; textSize = 12f; setTextColor(onSurfaceVariant); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) }, matchWrap())
        page.addView(body); return page
    }

    private fun settingsPage(): ScrollView {
        val page = ScrollView(this).apply { setBackgroundColor(ink) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        body.addView(TextView(this).apply { text = "Настройки"; textSize = 30f; setTextColor(onSurface); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        body.addView(TextView(this).apply { text = "Параметры применяются при следующем подключении"; textSize = 14f; setTextColor(onSurfaceVariant); setPadding(0, dp(8), 0, dp(16)) })
        body.addView(sectionTitle("Yandex Docs"))
        val input = TextInputLayout(this).apply { hint = "Ссылка на документ"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; setHintTextColor(android.content.res.ColorStateList.valueOf(onSurfaceVariant)); boxStrokeColor = primary }
        transportLink = TextInputEditText(this).apply { setSingleLine(true); textSize = 14f; setTextColor(onSurface) }
        input.addView(transportLink); body.addView(input, matchWrap())
        body.addView(MaterialButton(this).apply { text = "Сохранить ссылку"; cornerRadius = dp(20); setTextColor(onPrimary); backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setOnClickListener { getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putString("document", transportLink.text.toString().trim()).apply(); addLog("Ссылка на документ сохранена") } }, matchWrap(top = 16))
        body.addView(sectionTitle("Маршрутизация"))
        body.addView(MaterialButton(this).apply {
            text = "Исключения приложений"; cornerRadius = dp(20); setTextColor(onSurface); backgroundTintList = android.content.res.ColorStateList.valueOf(surfaceVariant)
            setOnClickListener { showExclusionsDialog() }
        }, matchWrap(top = 8))
        body.addView(TextView(this).apply { text = "Выбранные приложения будут обходить VPN. Изменения применяются при следующем подключении."; textSize = 12f; setTextColor(onSurfaceVariant); setPadding(dp(4), dp(8), dp(4), 0) })
        body.addView(sectionTitle("Сеть"))
        body.addView(card(surface).apply { addView(TextView(this@MainActivity).apply { text = "DNS-сервер\n77.88.8.8 / 77.88.8.1\n\nMTU пакета\n1400"; textSize = 15f; setTextColor(onSurface); setPadding(dp(20), dp(20), dp(20), dp(20)) }) }, matchWrap(top = 8))
        body.addView(sectionTitle("Поведение"))
        val behavior = card(surface)
        val behaviorBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        behaviorBody.addView(switchRow("Автоматическое переподключение", "Восстанавливать туннель после обрыва", "auto_reconnect", true))
        behaviorBody.addView(switchRow("Подключаться автоматически", "Запускать VPN при открытии приложения", "auto_connect", false))
        behavior.addView(behaviorBody); body.addView(behavior, matchWrap(top = 8))
        body.addView(sectionTitle("Данные"))
        body.addView(MaterialButton(this).apply {
            text = "Сбросить ссылку"; cornerRadius = dp(20); setTextColor(Color.rgb(255, 218, 214)); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(115, 36, 43))
            setOnClickListener { getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().remove("document").remove("profile_name").remove("profile_id").remove("profile_token").remove("client_ip").remove("server_ip").apply(); transportLink.setText("") }
        }, matchWrap(top = 8))
        page.addView(body)
        return page
    }

    private fun switchRow(title: String, subtitle: String, prefKey: String, fallback: Boolean): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10))
        val copy = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = title; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) })
            addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(onSurfaceVariant); setPadding(0, dp(3), 0, 0) })
        }
        addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val preferences = getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE)
        addView(MaterialSwitch(this@MainActivity).apply {
            isChecked = preferences.getBoolean(prefKey, fallback)
            thumbTintList = android.content.res.ColorStateList.valueOf(primary)
            trackTintList = android.content.res.ColorStateList.valueOf(primaryContainer)
            setOnCheckedChangeListener { _, value -> preferences.edit().putBoolean(prefKey, value).apply() }
        })
    }

    private fun showExclusionsDialog() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != packageName }.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase(Locale.getDefault()) }
        val selected = getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).getStringSet(OpenFluxVpnService.EXCLUDED_APPS, emptySet()).orEmpty().toMutableSet()
        val labels = apps.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        val states = apps.map { it.packageName in selected }.toBooleanArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Исключения из VPN")
            .setMultiChoiceItems(labels, states) { _, which, checked -> if (checked) selected.add(apps[which].packageName) else selected.remove(apps[which].packageName) }
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { _, _ -> getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putStringSet(OpenFluxVpnService.EXCLUDED_APPS, selected).apply() }
            .show()
    }

    private fun requestConnect() {
        // A session journal must describe only the current connection attempt.
        // Do not replay a refused/timeout event from a previous VPN session.
        getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit()
            .remove("last_event")
            .putString("state", "CONNECTING")
            .putString("detail", "Запускаем новую сессию")
            .apply()
        val link = if (::transportLink.isInitialized) transportLink.text?.toString()?.trim().orEmpty()
        else getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).getString("document", "").orEmpty()
        if (!link.startsWith("https://")) {
            getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putString("state", "DISCONNECTED").apply()
            sendWebState("ERROR", "Добавьте профиль перед подключением")
            return
        }
        getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).edit().putString("document", link).apply()
        val request = VpnService.prepare(this)
        if (request == null) requestNotificationThenStart() else permission.launch(request)
    }
    private fun requestNotificationThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startOpenFlux()
    }
    private fun startOpenFlux() {
        val link = if (::transportLink.isInitialized) transportLink.text?.toString()?.trim().orEmpty()
        else getSharedPreferences(OpenFluxVpnService.PREFS, MODE_PRIVATE).getString("document", "").orEmpty()
        if (::connectButton.isInitialized) renderState("CONNECTING", "Запрашиваем разрешение и запускаем туннель")
        startForegroundService(Intent(this, OpenFluxVpnService::class.java).setAction(OpenFluxVpnService.START).putExtra(OpenFluxVpnService.EXTRA_DOCUMENT_URL, link))
    }
    private fun renderState(state: String, detail: String) {
        currentState = state
        val connected = state == "CONNECTED"
        statusTitle.text = when (state) { "CONNECTED" -> "Туннель активен"; "CONNECTING", "TUN", "TRANSPORT" -> "Установка туннеля…"; "ERROR" -> "Обрыв соединения"; else -> "Туннель отключён" }
        statusDetail.text = detail; connectButton.text = ""; connectButton.isEnabled = true
        val active = state in setOf("CONNECTING", "TUN", "TRANSPORT", "CONNECTED")
        connectButton.contentDescription = if (active) "Отменить подключение VPN" else "Подключить VPN"
        connectButton.background = connectBackground(state)
        when {
            connected -> { stopSpinner(); startPulse() }
            state in setOf("CONNECTING", "TUN", "TRANSPORT") -> { stopPulse(); startSpinner() }
            else -> { stopSpinner(); stopPulse() }
        }
        stages.forEach { (key, widget) -> widget.root.alpha = if (connected || key == state || (state == "TRANSPORT" && key == "TUN")) 1f else .42f }
        updateStages(state)
        if (detail.isNotBlank()) addLog(detail)
    }
    private fun startPulse() { if (pulse?.isRunning == true) return; pulse = ObjectAnimator.ofFloat(connectButton, View.SCALE_X, 1f, 1.055f, 1f).apply { duration = 1500; repeatCount = ObjectAnimator.INFINITE; repeatMode = ObjectAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator(); start() } }
    private fun stopPulse() { pulse?.cancel(); connectButton.alpha = 1f }
    private fun startSpinner() { if (spinner?.isRunning == true) return; spinner = ObjectAnimator.ofFloat(connectButton, View.ROTATION, 0f, 360f).apply { duration = 1300; repeatCount = ObjectAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator(); start() } }
    private fun stopSpinner() { spinner?.cancel(); connectButton.rotation = 0f; connectButton.scaleX = 1f; connectButton.scaleY = 1f }
    private fun addLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        if (eventLines.lastOrNull()?.endsWith(message) == true) return
        eventLines.addLast("$stamp  $message")
        while (eventLines.size > 40) eventLines.removeFirst()
        val text = SpannableStringBuilder()
        eventLines.forEachIndexed { index, line ->
            if (index > 0) text.append('\n')
            val start = text.length
            text.append(line)
            val color = when {
                line.contains("ошиб", true) || line.contains("обрыв", true) || line.contains("failed", true) || line.contains("refused", true) || line.contains("1005") -> Color.rgb(255, 180, 171)
                line.contains("подключён", true) || line.contains("готов", true) || line.contains("AUTH_OK", true) -> Color.rgb(190, 235, 190)
                line.contains("ожида", true) || line.contains("переподключ", true) -> Color.rgb(255, 214, 140)
                else -> onSurfaceVariant
            }
            text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        logText.text = text
    }
    private fun channelRow(icon: Int, title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(primary); contentDescription = null
            background = rounded(surfaceVariant, dp(12)); setPadding(dp(9), dp(9), dp(9), dp(9))
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), 0, 0, 0)
            addView(TextView(this@MainActivity).apply { text = title; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) })
            addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(onSurfaceVariant); setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun statTile(label: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rounded(surfaceVariant, dp(16)); setPadding(dp(12), dp(12), dp(12), dp(12))
        addView(TextView(this@MainActivity).apply { text = label; textSize = 10f; letterSpacing = .08f; setTextColor(onSurfaceVariant) })
        addView(TextView(this@MainActivity).apply { text = value; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface); setPadding(0, dp(5), 0, 0) })
    }
    private fun connectBackground(state: String): GradientDrawable {
        val colors = when (state) {
            "CONNECTED" -> intArrayOf(Color.rgb(63, 214, 140), Color.rgb(20, 153, 96))
            "ERROR" -> intArrayOf(Color.rgb(255, 107, 107), Color.rgb(200, 16, 46))
            "TRANSPORT", "CONNECTING", "TUN" -> intArrayOf(Color.rgb(124, 108, 255), Color.rgb(74, 63, 214))
            else -> intArrayOf(Color.rgb(124, 108, 255), Color.rgb(74, 63, 214))
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { shape = GradientDrawable.OVAL }
    }
    private fun stage(titleText: String, subtitleText: String): StageWidget {
        val root = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(3), 0, dp(3)) }
        val dot = TextView(this).apply { gravity = Gravity.CENTER; text = "•"; textSize = 18f; setTextColor(onSurfaceVariant); background = rounded(outline, dp(16)) }
        root.addView(dot, LinearLayout.LayoutParams(dp(32), dp(32)))
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val title = TextView(this).apply { text = titleText; textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface) }
        val subtitle = TextView(this).apply { text = subtitleText; textSize = 12f; setTextColor(onSurfaceVariant); setPadding(0, dp(2), 0, 0) }
        copy.addView(title); copy.addView(subtitle); root.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val state = TextView(this).apply { text = "ОЖИДАНИЕ"; textSize = 10f; letterSpacing = .06f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurfaceVariant) }
        root.addView(state)
        return StageWidget(root, dot, title, subtitle, state)
    }
    private fun updateStages(state: String) {
        val order = listOf("TUN", "TRANSPORT", "CONNECTED")
        val active = when (state) {
            "CONNECTING" -> -1
            "TUN" -> 0
            "TRANSPORT" -> 1
            "CONNECTED" -> 2
            "ERROR" -> 1
            else -> -2
        }
        order.forEachIndexed { index, key ->
            val result = when {
                state == "ERROR" && index == active -> "ERROR"
                state == "CONNECTED" || index < active -> "SUCCESS"
                index == active -> "ACTIVE"
                else -> "IDLE"
            }
            stageStates[key] = result
            val widget = stages[key] ?: return@forEachIndexed
            val marker = when (result) { "SUCCESS" -> "✓"; "ACTIVE" -> "…"; "ERROR" -> "×"; else -> "•" }
            val bg = when (result) { "SUCCESS" -> Color.rgb(27, 138, 90); "ACTIVE" -> primary; "ERROR" -> Color.rgb(213, 37, 63); else -> outline }
            val fg = when (result) { "SUCCESS", "ACTIVE", "ERROR" -> Color.WHITE; else -> onSurfaceVariant }
            widget.dot.text = marker; widget.dot.setTextColor(fg); widget.dot.background = rounded(bg, dp(16)); widget.state.text = when (result) { "SUCCESS" -> "ГОТОВО"; "ACTIVE" -> "В РАБОТЕ"; "ERROR" -> "ОШИБКА"; else -> "ОЖИДАНИЕ" }
            widget.state.setTextColor(when (result) { "SUCCESS" -> Color.rgb(27, 138, 90); "ACTIVE" -> primary; "ERROR" -> Color.rgb(213, 37, 63); else -> onSurfaceVariant })
            widget.root.alpha = if (result == "IDLE") .62f else 1f
        }
    }
    private fun sectionTitle(value: String): TextView = TextView(this).apply { text = value.uppercase(Locale.getDefault()); textSize = 12f; letterSpacing = .12f; setTextColor(primary); setPadding(dp(4), dp(24), 0, dp(8)) }
    private fun card(color: Int) = MaterialCardView(this).apply { radius = dp(24).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = outline; setCardBackgroundColor(color) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun matchWrap(top: Int = 0) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
