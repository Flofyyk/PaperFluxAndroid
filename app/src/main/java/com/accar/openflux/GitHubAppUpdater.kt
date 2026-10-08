package com.accar.openflux

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** No tokens, no telemetry/profile data, no arbitrary URLs accepted from JavaScript. */
class GitHubAppUpdater(private val activity: Activity, private val emit: (String) -> Unit) {
    data class Release(val tag: String, val name: String, val url: String, val sha256: String, val size: Long)
    private val prefs = activity.getSharedPreferences("paperflux_app_updates", 0)
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var closed = false
    @Volatile private var call: Call? = null
    @Volatile private var release: Release? = runCatching {
        prefs.getString("cached_release", null)?.let { parseRelease(JSONObject(it), BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList()) }
    }.getOrNull()
    @Volatile private var readyFile: File? = null
    @Volatile private var state = JSONObject().put("phase", "idle").toString()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private fun publish(phase: String, message: String = "", percent: Int? = null) {
        state = JSONObject().put("phase", phase).put("version", release?.tag?.removePrefix("v").orEmpty())
            .put("message", message).put("percent", percent ?: JSONObject.NULL).toString()
        if (!closed) emit(state)
    }
    fun snapshot() = state

    fun check(manual: Boolean = false) {
        if (closed || busy.get()) return
        val now = System.currentTimeMillis()
        if (!manual && !AppUpdatePolicy.shouldOffer(now, prefs.getLong("snoozed_until", 0L))) return
        if (!manual && now - prefs.getLong("last_attempt", 0L) < 15 * 60_000L) {
            if (release != null && readyFile == null) publish("available")
            return
        }
        if (!busy.compareAndSet(false, true)) return
        prefs.edit().putLong("last_attempt", now).commit()
        executor.execute {
            try {
                val request = Request.Builder().url("https://api.github.com/repos/Flofyyk/PaperFluxAndroid/releases/latest")
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "PaperFlux-Android").build()
                call = client.newCall(request)
                call!!.timeout().timeout(30, TimeUnit.SECONDS)
                val json = call!!.execute().use { response ->
                    check(response.isSuccessful) { "GitHub не ответил на проверку обновления" }
                    val body = response.body ?: error("Пустой ответ GitHub")
                    require(body.contentLength() <= 2 * 1024 * 1024) { "Слишком большой ответ GitHub" }
                    val bytes = body.byteStream().use { input -> input.readBytesBounded(2 * 1024 * 1024) }
                    JSONObject(String(bytes, Charsets.UTF_8))
                }
                release = parseRelease(json, BuildConfig.VERSION_NAME, Build.SUPPORTED_ABIS.toList())
                prefs.edit().putString("cached_release", json.toString()).commit()
                readyFile = null
                if (release != null) publish("available") else if (manual) publish("current", "У вас последняя версия")
            } catch (_: Exception) {
                if (manual) publish("error", "Не удалось проверить GitHub. Проверьте интернет и повторите попытку")
            } finally { call = null; busy.set(false) }
        }
    }

    fun snooze(): String {
        if (busy.get()) return "Дождитесь завершения загрузки"
        if (!prefs.edit().putLong("snoozed_until", System.currentTimeMillis() + AppUpdatePolicy.SNOOZE_MS).commit())
            return "Не удалось сохранить пропуск обновления"
        publish("idle")
        return ""
    }

    fun download() {
        val selected = release ?: return
        if (closed || !busy.compareAndSet(false, true)) return
        publish("downloading", percent = 0)
        executor.execute {
            val folder = File(activity.cacheDir, "updates").apply { mkdirs() }
            val part = File(folder, selected.name + ".part")
            try {
                val response = downloadResponse(selected.url)
                response.use {
                    val body = response.body ?: error("Пустой файл обновления")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    var percent = -1
                    body.byteStream().use { input -> part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (closed || Thread.currentThread().isInterrupted) throw IOException("Cancelled")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= selected.size && total <= MAX_APK_SIZE) { "Размер файла не совпадает" }
                            digest.update(buffer, 0, count); output.write(buffer, 0, count)
                            val next = (total * 100 / selected.size).toInt()
                            if (next != percent) { percent = next; publish("downloading", percent = percent) }
                        }
                        output.fd.sync()
                    } }
                    require(total == selected.size && hex(digest.digest()) == selected.sha256) { "Контрольная сумма APK не совпадает" }
                }
                validateApk(activity.packageManager, part, activity.packageName, selected.tag)
                val file = File(folder, selected.name)
                check(part.renameTo(file)) { "Не удалось сохранить APK" }
                readyFile = file
                publish("ready", "Загрузка завершена. Подтвердите установку обновления")
            } catch (_: Exception) {
                part.delete()
                publish("error", "Не удалось скачать или проверить APK. Повторите попытку; приложение не изменено")
            } finally { call = null; busy.set(false) }
        }
    }

    private fun downloadResponse(raw: String): okhttp3.Response {
        var url = raw.toHttpUrl()
        repeat(6) {
            require(url.scheme == "https" && url.host in DOWNLOAD_HOSTS && url.username.isEmpty() && url.password.isEmpty())
            call = client.newCall(Request.Builder().url(url).header("Accept-Encoding", "identity").build())
            val response = call!!.execute()
            if (response.code in listOf(301, 302, 303, 307, 308)) {
                val next = response.header("Location")?.let { url.resolve(it) }
                response.close()
                url = requireNotNull(next) { "Некорректный адрес GitHub" }
            } else {
                if (!response.isSuccessful) { response.close(); error("GitHub не отдал APK") }
                return response
            }
        }
        error("Слишком много перенаправлений GitHub")
    }

    /** Called by Activity only after explicit consent/unknown-sources permission. */
    fun install() {
        val file = readyFile ?: return
        if (!file.isFile) { publish("error", "Файл обновления исчез. Скачайте его заново"); return }
        runCatching {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.profile-files", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { publish("error", "Не удалось открыть установщик Android") }
    }
    fun close() { closed = true; call?.cancel(); executor.shutdownNow() }

    companion object {
        const val MAX_APK_SIZE = 120 * 1024 * 1024L
        private val DOWNLOAD_HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
        fun parseRelease(json: JSONObject, current: String, abis: List<String>): Release? {
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
            val tag = json.getString("tag_name")
            if (!AppUpdatePolicy.newer(tag, current)) return null
            val assets = json.getJSONArray("assets")
            for (name in AppUpdatePolicy.assetNames(tag, abis)) {
                val rows = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name") == name }
                if (rows.isEmpty()) continue
                require(rows.size == 1) { "Неоднозначный APK" }
                val row = rows.single()
                val digest = row.getString("digest")
                require(digest.startsWith("sha256:")) { "GitHub не предоставил SHA-256" }
                val hash = digest.removePrefix("sha256:")
                require(Regex("[0-9a-f]{64}").matches(hash)) { "GitHub не предоставил SHA-256" }
                val url = row.getString("browser_download_url")
                require(url == "https://github.com/Flofyyk/PaperFluxAndroid/releases/download/$tag/$name") { "Чужой источник APK" }
                val size = row.getLong("size")
                require(size in 1..MAX_APK_SIZE)
                return Release(tag, name, url, hash, size)
            }
            error("В релизе нет APK для этого устройства")
        }

        @Suppress("DEPRECATION")
        fun validateApk(manager: PackageManager, file: File, packageName: String, tag: String) {
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val archive = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) { "Некорректный APK" }
            val installed = manager.getPackageInfo(packageName, flags)
            require(archive.packageName == packageName && archive.versionName == tag.removePrefix("v")) { "Пакет или версия APK не совпадают" }
            val nextCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
            val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
            require(nextCode > currentCode) { "APK не является обновлением" }
            val next = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
            val previous = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
            require(!next.isNullOrEmpty() && !previous.isNullOrEmpty()) { "APK не подписан" }
            require(next.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet() ==
                previous.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()) { "Чужая подпись APK" }
        }
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Ответ слишком большой" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
