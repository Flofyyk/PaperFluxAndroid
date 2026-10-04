package com.accar.openflux

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.AtomicFile
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest

/** One visible check, independent local/server identities, bounded waiting list. */
internal class AuthRequestQueue {
    private val requests = LinkedHashMap<String, JSONObject>()
    private val completed = LinkedHashSet<String>()
    fun current(): JSONObject? = requests.values.firstOrNull()
    fun accept(request: JSONObject): Boolean {
        val id = request.optString("requestId")
        if (id.isEmpty() || id in completed) return false
        val empty = requests.isEmpty()
        if (requests.containsKey(id) || requests.size < 8) requests[id] = request
        return empty && requests.isNotEmpty()
    }
    fun acknowledge(id: String): Boolean {
        val active = requests.keys.firstOrNull() == id
        requests.remove(id)
        if (id.isNotEmpty()) {
            completed.add(id)
            if (completed.size > 64) completed.remove(completed.first())
        }
        return active
    }
}

/** Private, attempt-bound IPC; no cookies or document URLs enter Logcat. */
object NativeAuthBridge {
    internal fun autoOpenScope(request: JSONObject) = YandexAuthAutoOpenPolicy.scope(
        request.optString("attempt"), request.optString("transport"), request.optBoolean("remote"))
    private fun autoOpenHistory(context: Context): JSONObject = runCatching {
        // AtomicFile must be allowed to recover .bak even if the base file is
        // missing after interruption. Only MainActivity writes this history.
        val bytes = AtomicFile(File(context.noBackupFilesDir, "auth-auto-opened.json")).readFully()
        require(bytes.size <= 65536)
        JSONObject(bytes.toString(Charsets.UTF_8))
    }.getOrElse { JSONObject() }
    fun wasAutoOpened(context: Context, request: JSONObject): Boolean =
        autoOpenHistory(context).has(autoOpenScope(request))
    fun rememberAutoOpen(context: Context, request: JSONObject) {
        val previous = autoOpenHistory(context)
        val scope = autoOpenScope(request)
        val next = JSONObject()
        // Bounded private history, no cookies, URLs or credentials. Fresh
        // connection attempts have a new socket identity and remain eligible.
        previous.keys().asSequence().filter { it != scope }
            .sortedByDescending { previous.optLong(it) }.take(63)
            .forEach { next.put(it, previous.optLong(it)) }
        next.put(scope, System.currentTimeMillis())
        write(File(context.noBackupFilesDir, "auth-auto-opened.json"), next)
    }
    fun rememberAutoOpenDismissal(context: Context, request: JSONObject) {
        // Atomic file, not cached SharedPreferences: :auth and the UI are
        // separate processes. Another channel or connection attempt is distinct.
        val file = File(context.noBackupFilesDir, "auth-auto-open-dismissed.json")
        val now = System.currentTimeMillis()
        val previous = read(file) ?: JSONObject()
        val next = JSONObject()
        previous.keys().forEach { scope ->
            if (previous.optLong(scope) > now && next.length() < 16) next.put(scope, previous.optLong(scope))
        }
        next.put(autoOpenScope(request), now + 600_000)
        write(file, next)
    }
    fun autoOpenDismissedUntil(context: Context, request: JSONObject): Long {
        val value = read(File(context.noBackupFilesDir, "auth-auto-open-dismissed.json")) ?: return 0
        return value.optLong(autoOpenScope(request))
    }
    fun verificationState(context: Context, vpnAlive: Boolean, vpnConnected: Boolean = false): JSONObject? {
        if (!vpnAlive) return null
        val request = read(File(context.noBackupFilesDir, "auth-request.json")) ?: return null
        val age = System.currentTimeMillis() - request.optLong("created")
        if (request.optString("requestId").isBlank() || age !in 0..1_800_000 ||
            !YandexVerificationPolicy.allows(request.optString("url"))) return null
        // Public UI payload: never expose request URLs, cookies or socket paths.
        return JSONObject().put("carrier", YandexVerificationPolicy.carrierLabel(request.optString("transport")))
            .put("side", if (request.optBoolean("remote")) "VPS" else "телефон")
            .put("automatic", !vpnConnected && !wasAutoOpened(context, request) && System.currentTimeMillis() >= autoOpenDismissedUntil(context, request))
    }
    internal fun cookieKey(provider: String, document: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$provider\u0000$document".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun savePreflightCookies(context: Context, document: String, values: JSONObject) {
        val file = File(context.noBackupFilesDir, "preflight-cookies.json")
        val stored = read(file) ?: JSONObject()
        for (provider in listOf("yandex", "vyandex")) stored.put(cookieKey(provider, document), values)
        write(file, stored)
    }
    fun applyPreflightCookies(context: Context, profileId: String, provider: String, documents: List<String>, namespace: String = profileId) {
        val file = File(context.noBackupFilesDir, "preflight-cookies.json")
        val target = File(context.noBackupFilesDir, "session-cookies-$namespace.json")
        val stored = read(target) ?: if (target.exists()) error("Хранилище cookies не удалось прочитать") else JSONObject()
        var changed = false
        if (!target.exists() && namespace != profileId) {
            // Preserve only the selected node's document keys from the old
            // ID-only store; different VPSes can legitimately reuse an ID.
            val legacy = read(File(context.noBackupFilesDir, "session-cookies-$profileId.json"))
            for (doc in documents) {
                val key = cookieKey(provider, doc)
                legacy?.optJSONObject(key)?.let { stored.put(key, it); changed = true }
            }
        }
        // A newly imported profile can reuse the same Yandex documents under
        // a different profile ID. Migrate only exact document-key matches; no
        // unrelated document or provider cookies are copied.
        val wanted = documents.map { cookieKey(provider, it) }
        context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it != target && it.name.matches(Regex("session-cookies-[A-Za-z0-9_-]{1,90}\\.json")) }
            .take(32).forEach { source ->
                val previous = read(source) ?: return@forEach
                for (key in wanted) if (!stored.has(key)) {
                    previous.optJSONObject(key)?.let { stored.put(key, it); changed = true }
                }
            }
        if (changed) write(target, stored)
        val pending = read(file) ?: return
        changed = false
        for (doc in documents) {
            val key = cookieKey(provider, doc)
            pending.optJSONObject(key)?.let { stored.put(key, it); pending.remove(key); changed = true }
        }
        if (changed) { write(target, stored); write(file, pending) }
    }
    fun read(file: File): JSONObject? = runCatching {
        if (!file.exists() || file.length() > 65536) return null
        JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
    }.getOrNull()
    fun write(file: File, value: JSONObject) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65536)
        val atomic = AtomicFile(file); val out = atomic.startWrite()
        try { out.write(bytes); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    fun start(context: Context, socketPath: String, child: Process, current: () -> Boolean, onRequest: (JSONObject) -> Unit, onUpdated: () -> Unit) {
        val requestFile = File(context.noBackupFilesDir, "auth-request.json")
        val offerFile = File(context.noBackupFilesDir, "auth-offer.json")
        val commandFile = File(context.noBackupFilesDir, "auth-command.json")
        requestFile.delete(); offerFile.delete(); commandFile.delete()
        val queue = AuthRequestQueue()
        val lock = Any()
        fun displayNext() {
            val next = queue.current()
            if (next == null) { requestFile.delete(); onUpdated() }
            else { write(requestFile, next); onRequest(next) }
        }
        Thread({
            while (child.isAlive && current()) {
                val socket = LocalSocket()
                val connected = AtomicBoolean(false)
                try {
                    socket.connect(LocalSocketAddress(socketPath, LocalSocketAddress.Namespace.FILESYSTEM))
                    connected.set(true)
                    val input = DataInputStream(socket.inputStream)
                    val output = DataOutputStream(socket.outputStream)
                    Thread({
                        try {
                            while (child.isAlive && current() && connected.get()) {
                                val command = read(commandFile)
                                if (command != null && command.optString("attempt") == socketPath) {
                                    command.remove("attempt")
                                    val id = command.optJSONObject("params")?.optString("requestId").orEmpty()
                                    val payload = command.toString().toByteArray(Charsets.UTF_8)
                                    output.writeInt(payload.size + 1); output.writeByte(4); output.write(payload); output.flush()
                                    commandFile.delete()
                                    synchronized(lock) { if (queue.acknowledge(id)) displayNext() }
                                }
                                val offer = read(offerFile)
                                if (offer != null && offer.optString("attempt") == socketPath) {
                                    offer.remove("attempt")
                                    val payload = offer.toString().toByteArray(Charsets.UTF_8)
                                    output.writeInt(payload.size + 1); output.writeByte(2); output.write(payload); output.flush()
                                    offerFile.delete()
                                }
                                Thread.sleep(250)
                            }
                        } catch (_: Exception) { } finally { runCatching { socket.close() } }
                    }, "PaperFlux-auth-offers").start()
                    while (child.isAlive && current()) {
                        val length = input.readInt()
                        require(length in 1..65536)
                        val type = input.readUnsignedByte()
                        val payload = ByteArray(length - 1); input.readFully(payload)
                        if (type == 1 && current()) {
                            val request = JSONObject(payload.toString(Charsets.UTF_8))
                            request.put("attempt", socketPath)
                            request.put("created", System.currentTimeMillis())
                            val id = request.optString("requestId")
                            if (id.isNotEmpty()) synchronized(lock) {
                                val first = queue.accept(request)
                                if (first) displayNext()
                                else if (queue.current()?.optString("requestId") == id) write(requestFile, request)
                            }
                        } else if (type == 5 && current()) {
                            val line = JSONObject(payload.toString(Charsets.UTF_8)).optString("line")
                            if (line.startsWith("AUTH_UPDATED:")) synchronized(lock) {
                                val id = line.removePrefix("AUTH_UPDATED:")
                                if (queue.acknowledge(id)) displayNext()
                            }
                        }
                    }
                } catch (_: Exception) { if (child.isAlive && current()) Thread.sleep(500) }
                finally { connected.set(false); runCatching { socket.close() } }
            }
        }, "PaperFlux-auth-control").start()
    }
}
