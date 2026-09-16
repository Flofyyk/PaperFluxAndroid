package com.accar.openflux

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ServerSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale
import java.util.concurrent.TimeUnit

class OpenFluxVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    /** The only scheduler allowed to start a replacement session. */
    private val reconnectWorker = Executors.newSingleThreadScheduledExecutor()
    private val reconnectScheduled = AtomicBoolean(false)
    private val recoveryGeneration = AtomicInteger(0)
    private val sessionGeneration = AtomicInteger(0)
    private val fullRecoveryScheduled = AtomicBoolean(false)
    @Volatile private var reconnectFuture: ScheduledFuture<*>? = null
    @Volatile private var fullRecoveryFuture: ScheduledFuture<*>? = null
    @Volatile private var reconnectAttempt = 0
    // Startup holds the service monitor while waiting for native readiness.
    // The native log reader must never acquire that monitor for statistics.
    private val statsLock = Any()
    private var tun: ParcelFileDescriptor? = null
    private var process: Process? = null
    private val workerProcesses = Collections.synchronizedList(mutableListOf<Process>())
    private val workerSockets = Collections.synchronizedList(mutableListOf<Socket>())
    @Volatile private var nativeAuthenticated = false
    @Volatile private var nativeTunnelReady = false
    @Volatile private var stopping = false
    @Volatile private var starting = false
    @Volatile private var statsRunning = false
    @Volatile private var networkWatchRunning = false
    @Volatile private var lastRxBytes = 0L
    @Volatile private var lastTxBytes = 0L
    @Volatile private var lastPingMs = 0L
    // Native counters restart at zero when the worker process is replaced.
    // Keep a per-session offset so the visible total does not jump backwards.
    @Volatile private var nativeRxBytes = 0L
    @Volatile private var nativeTxBytes = 0L
    @Volatile private var rxOffsetBytes = 0L
    @Volatile private var txOffsetBytes = 0L
    @Volatile private var lastNativeStatsAt = 0L
    private val nativeStatsPattern = Regex("\\[PAPERFLUX_STATS\\] rx=(\\d+) tx=(\\d+) ping=(\\d+)")
    private val laneStatusPattern = Regex("\\[PAPERFLUX_LANES\\] ready=(\\d+)/(\\d+)")
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            if (wantsConnection() && !tunnelRunning && !starting) scheduleReconnect("Сеть доступна. Восстанавливаем защищённый канал", immediate = true)
        }
        override fun onLost(network: android.net.Network) {
            // Android can report a short Wi-Fi -> mobile handover gap. Give
            // its network stack a moment before tearing down a live attempt.
            reconnectWorker.schedule({ pauseForMissingNetwork() }, 600, TimeUnit.MILLISECONDS)
        }
        override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && wantsConnection() && !tunnelRunning && !starting) {
                scheduleReconnect("Сеть готова. Восстанавливаем защищённый канал", immediate = true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopTunnel(); return START_NOT_STICKY }
        if (intent?.action == UPDATE_SETTINGS) {
            val enabled = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)
            preferences().edit().putBoolean(AUTO_RECONNECT, enabled).apply()
            if (!enabled) cancelReconnects() else if (wantsConnection() && !tunnelRunning) scheduleReconnect("Автоподключение включено", immediate = true)
            return START_STICKY
        }
        // START_STICKY may recreate the foreground service with a null intent.
        // Restore the user's explicit intent, not the short-lived UI state.
        if (intent?.action != START) {
            val prefs = preferences()
            if (prefs.getBoolean(DESIRED_ACTIVE, false) && !starting && !tunnelRunning) {
                documentUrl = prefs.getString("document", documentUrl) ?: documentUrl
                scheduleReconnect("Восстанавливаем фоновое подключение", immediate = true)
            }
            return START_STICKY
        }
        if (starting || tunnelRunning) return START_NOT_STICKY
        val resuming = intent.getBooleanExtra(EXTRA_RESUME, false)
        preferences().edit().putBoolean(DESIRED_ACTIVE, true).apply()
        starting = true
        documentUrl = intent?.getStringExtra(EXTRA_DOCUMENT_URL)?.takeIf { it.startsWith("https://") } ?: documentUrl
        if (documentUrl.isBlank()) {
            starting = false
            publish("ERROR", "Добавьте профиль PaperFlux перед подключением")
            return START_NOT_STICKY
        }
        // A deliberate START begins a new journal. A restart from the recents
        // task is the same VPN session, therefore it must keep its events and
        // counters instead of leaving the user with a single last log line.
        if (!resuming) {
            SessionJournal.begin(this)
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_event").apply()
        }
        lastUiEvent = ""
        lastUiEventAt = 0L
        if (resuming) {
            val snapshot = TunnelSnapshot.read(this)
            lastRxBytes = snapshot.optLong("rxBytes", 0L); lastTxBytes = snapshot.optLong("txBytes", 0L)
            lastPingMs = snapshot.optLong("ping", 0L)
            nativeRxBytes = 0L; nativeTxBytes = 0L; rxOffsetBytes = lastRxBytes; txOffsetBytes = lastTxBytes; lastNativeStatsAt = 0L
            publish("RECONNECTING", "Восстанавливаем защищённый туннель")
        } else {
            // The foreground service itself continues to persist these values
            // while the Activity/WebView is closed.
            lastRxBytes = 0L; lastTxBytes = 0L; lastPingMs = 0L
            nativeRxBytes = 0L; nativeTxBytes = 0L; rxOffsetBytes = 0L; txOffsetBytes = 0L; lastNativeStatsAt = 0L
            TunnelSnapshot.startSession(this)
            publish("CONNECTING", "Создаём защищённый туннель")
        }
        startForeground(NOTIFICATION_ID, notification("PaperFlux: подключение"))
        worker.execute { startTunnel() }
        return START_STICKY
    }

    @Synchronized
    private fun startTunnel() {
        val attempt = sessionGeneration.incrementAndGet()
        fun current() = attempt == sessionGeneration.get() && wantsConnection() && !stopping
        try {
            if (!wantsConnection()) return
            stopping = false
            releaseTunnelResources()
            tunnelRunning = false
            nativeAuthenticated = false
            nativeTunnelReady = false
            lastNativeStatsAt = 0L
            requireValidatedNetwork()
            val identity = ProfileStore(this).active() ?: error("Добавьте действующий профиль PaperFlux")
            val profileDocuments = identity.optJSONArray("documentUrls")
            documentUrl = if (profileDocuments != null) (0 until profileDocuments.length()).joinToString(",") { profileDocuments.getString(it) } else identity.getString("documentUrl")
            val profileId = identity.getString("id")
            val profileToken = identity.getString("token")
            val clientIp = identity.getString("clientIp")
            check(profileId.isNotBlank() && profileToken.isNotBlank() && clientIp.isNotBlank()) {
                "Добавьте действующий профиль PaperFlux перед подключением"
            }
            publish("TUN", "Настраиваем VPN-интерфейс")
            val socketName = "openflux_tun_${android.os.Process.myPid()}_${System.nanoTime()}"
            val binary = File(applicationInfo.nativeLibraryDir, "libopenflux.so")
            check(binary.exists()) { "Нативное ядро OpenFlux не найдено" }
            // Establish the VPN first, with this app excluded, so every
            // OpenFlux transport socket is born on the underlying network.
            val builder = Builder().setSession("PaperFlux").setMtu(1400)
                .addAddress(clientIp, 24)
                .addDisallowedApplication(packageName)
                // Use Yandex DNS on the TUN as well as in the native
                // bootstrap resolver.  1.1.1.1 is filtered on some mobile
                // operators and made Android report that the Internet was
                // unavailable even when LTE was working.
                .addDnsServer("77.88.8.8")
                .addDnsServer("77.88.8.1")
                .addRoute("0.0.0.0", 0)
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(EXCLUDED_APPS, emptySet()).orEmpty().forEach { packageName ->
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            tun = builder.establish()
                ?: error("Не удалось создать Android TUN")
            val documentUrls = documentUrl.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            check(documentUrls.isNotEmpty()) { "Не указана ссылка на Yandex Docs" }
            // One native process owns the TUN/TCP stack. Document lanes live
            // inside that process, so replies keep one shared flow table.
            val command = mutableListOf(binary.absolutePath, "--client", "--transport", "yandex", "--urls", documentUrls.joinToString(","))
            if (profileId.isNotBlank()) command += listOf("--profile-id", profileId)
            if (profileToken.isNotBlank()) command += listOf("--profile-token", profileToken)
            if (clientIp.isNotBlank()) command += listOf("--client-ip", clientIp)
            command += listOf("--tun-fd-sock", "@$socketName")
            process = ProcessBuilder(command).apply {
                directory(filesDir); redirectErrorStream(true)
                environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir
            }.start()
            publish("TRANSPORT", if (documentUrls.size == 1) "Подключаем Yandex Docs transport" else "Подключаем ${documentUrls.size} Yandex Docs канала")
            // Do not leave the child pipe unread: Go debug output would fill
            // it and freeze the transport. Keeping it in logcat also makes a
            // failed Yandex handshake diagnosable without exposing it in UI.
            val child = process ?: error("Не удалось запустить OpenFlux")
            Thread {
                try {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach {
                            if (process !== child || !current()) return@forEach
                            val safe = redactNativeLog(it)
                            val statsMatch = nativeStatsPattern.find(safe)
                            // Stats are consumed by the UI but do not need to
                            // be written to Logcat on every native tick.
                            if (statsMatch == null) Log.i("OpenFluxNative", safe)
                            statsMatch?.let { m ->
                                acceptNativeStats(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3].toLong())
                            }
							val lanesMatch = laneStatusPattern.find(safe)
							lanesMatch?.let { m ->
								val ready = m.groupValues[1].toInt()
								val total = m.groupValues[2].toInt()
								if (ready > 0 && tunnelRunning && !stopping) {
									publishEvent("[TRANSPORT] Доступно каналов Yandex Docs: $ready из $total")
								}
							}
                            // YANDEX_AUTH_OK is the last prerequisite before
                            // Android may hand the TUN descriptor to native
                            // core. PEER_READY is intentionally emitted only
                            // after that descriptor has been received, so
                            // waiting for it here creates a startup deadlock.
                            if (current() && (safe.contains("[PAPERFLUX] YANDEX_AUTH_OK") || safe.contains("[PAPERFLUX] YANDEX_WAIT_AUTH") || safe.contains("[PAPERFLUX] PEER_READY"))) {
                                nativeAuthenticated = true
                            }
                            if (current() && safe.contains("[PAPERFLUX] TUNNEL_READY")) {
                                val recovered = !nativeTunnelReady
                                nativeTunnelReady = true
                                cancelFullRecovery()
                                if (tunnelRunning && !stopping) TunnelSnapshot.write(this@OpenFluxVpnService, "CONNECTED", "Шифрованный туннель: DNS и TCP подтверждены")
                                if (recovered && tunnelRunning && !stopping) publish("CONNECTED", "Шифрованный туннель: DNS и TCP подтверждены")
                            }
                            if (current() && safe.contains("[PAPERFLUX] TUNNEL_HEALTHY") && nativeTunnelReady && tunnelRunning) {
                                // Refresh the cross-process snapshot without adding a
                                // duplicate CONNECTED item to the visible journal.
                                TunnelSnapshot.write(this@OpenFluxVpnService, "CONNECTED", "Шифрованный туннель: DNS и TCP подтверждены")
                            }
                            if (current() && (safe.contains("PEER_LOST") || safe.contains("TUNNEL_LOST"))) {
                                nativeTunnelReady = false
                                publish("RECONNECTING", "Нет ответа через защищённый канал")
                                scheduleFullRecovery("Защищённая сессия не восстановилась")
                            }
                            if (current() && (safe.contains("Yandex transport lost") || safe.contains("WebSocket write failed"))) {
                                nativeAuthenticated = false
                                nativeTunnelReady = false
                                publish("RECONNECTING", "Связь с Yandex Docs прервана. Восстанавливаем соединение")
                                scheduleFullRecovery("Yandex Docs не восстановил соединение")
                            }
                            if (shouldPublishNativeEvent(safe)) publishEvent(safe)
                        }
                    }
                } catch (e: IOException) {
                    // destroy() closes the pipe; that is normal during a
                    // user-requested disconnect and must not crash the app.
                    if (child.isAlive && !stopping) Log.w("OpenFluxVpn", "Native log pipe closed", e)
                }
            }.start()
            // The native child opens its TUN-FD listener independently. A
            // short yield is enough to begin consuming auth output; the old
            // fixed quarter-second pause only delayed the visible startup.
            Thread.sleep(50)
            publish("TRANSPORT", "Ожидаем подтверждение авторизации Яндекс Docs")
            val authDeadline = System.currentTimeMillis() + 75_000
            while (current() && !nativeAuthenticated && System.currentTimeMillis() < authDeadline) {
                if (!child.isAlive) {
                    val code = runCatching { child.exitValue() }.getOrDefault(-1)
                    error("Соединение завершилось до подтверждения. Повторите попытку")
                }
                Thread.sleep(100)
            }
            if (!current()) return
            check(nativeAuthenticated) { "Не удалось подтвердить защищённое соединение. Проверьте профиль и повторите попытку" }
            sendTunFd(socketName, tun!!)
            publish("DNS", "Проверяем DNS и TCP через защищённый канал")
            val tunnelDeadline = System.currentTimeMillis() + 35_000
            while (current() && !nativeTunnelReady && child.isAlive && System.currentTimeMillis() < tunnelDeadline) Thread.sleep(200)
            if (!current()) return
            check(nativeTunnelReady) { "Защищённый канал создан, но доступ к интернету пока не подтверждён" }
            tunnelRunning = true
            reconnectAttempt = 0
            publish("CONNECTED", "Yandex‑туннель подтверждён. DNS и TCP готовы")
            startTrafficStats()
            startNetworkWatchdog()
            Thread {
                val exitCode = child.waitFor()
                if (process === child && tunnelRunning && current()) {
                    Log.w("OpenFluxVpn", "Native transport stopped: $exitCode")
                    nativeAuthenticated = false
                    nativeTunnelReady = false
                    tunnelRunning = false
                    statsRunning = false
                    publish("RECONNECTING", "Восстанавливаем соединение с Yandex Docs")
                    // A dead child can no longer recover its TUN descriptor.
                    // Close it before retrying so Android does not keep a
                    // stale VPN icon or a black-holed default route.
                    sessionGeneration.incrementAndGet()
                    releaseTunnelResources()
                    scheduleReconnect("Нативный транспорт завершился")
                }
            }.start()
        } catch (e: Exception) {
            if (!current() || e is InterruptedException) return
            Log.e("OpenFluxVpn", "Tunnel startup failed", e)
            startForeground(NOTIFICATION_ID, notification("PaperFlux: не удалось подключиться"))
            publish("ERROR", explainFailure(e))
            releaseTunnelResources()
            if (wantsConnection() && autoReconnectEnabled()) scheduleReconnect(explainFailure(e))
            else if (wantsConnection()) {
                preferences().edit().putBoolean(DESIRED_ACTIVE, false).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } finally {
            if (attempt == sessionGeneration.get()) starting = false
        }
    }

    private fun sendTunFd(name: String, descriptor: ParcelFileDescriptor) {
        var socket: LocalSocket? = null
        var last: Exception? = null
        repeat(25) {
            try {
                socket = LocalSocket(); socket!!.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                socket!!.setFileDescriptorsForSend(arrayOf(descriptor.fileDescriptor))
                socket!!.outputStream.write(1); socket!!.outputStream.flush(); return
            } catch (e: Exception) { last = e; Thread.sleep(200) } finally { runCatching { socket?.close() }; socket = null }
        }
        throw IllegalStateException("Не удалось передать TUN OpenFlux", last)
    }

    /** Launch one native session per document; TUN remains single-owner here. */
    private fun startIsolatedWorkers(urls: List<String>, binary: File) {
        publish("TRANSPORT", "Запускаем ${urls.size} изолированных Yandex-канала")
        // Bind on loopback explicitly; workers connect through the host stack,
        // never through the VPN route. A backlog of four avoids a startup race.
        val servers = urls.map { ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1")) }
        try {
            servers.forEachIndexed { index, server ->
                if (index > 0) Thread.sleep(1200L)
                val command = mutableListOf(binary.absolutePath, "--client", "--transport", "yandex", "--url", urls[index], "--packet-sock", "127.0.0.1:${server.localPort}", "--socks5", "127.0.0.1:${10800 + index}", "--client-ip", "10.10.10.${2 + index}")
                Log.i("OpenFluxVpn", "Worker ${index + 1}: запуск Yandex на 127.0.0.1:${server.localPort}")
                val child = ProcessBuilder(command).apply { directory(filesDir); redirectErrorStream(true); environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir }.start()
                workerProcesses += child
                Thread {
                    val code = runCatching { child.waitFor() }.getOrDefault(-1)
                    if (!stopping) Log.e("OpenFluxVpn", "Worker ${index + 1} завершился с кодом $code")
                }.start()
                Thread {
                    child.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                        val safe = redactNativeLog("[W${index + 1}] $line")
                        val stats = Regex("\\[PAPERFLUX_STATS\\] rx=(\\d+) tx=(\\d+) ping=(\\d+)").find(safe)
                        if (stats == null) Log.i("OpenFluxNative", safe)
                        stats?.let { m -> sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("rx", m.groupValues[1].toLong()).putExtra("tx", m.groupValues[2].toLong()).putExtra("ping", m.groupValues[3].toLong())) }
                        if (safe.contains("YANDEX_AUTH_OK")) publishEvent("Канал ${index + 1}: авторизация подтверждена")
                        if (shouldPublishNativeEvent(safe)) publishEvent(safe)
                    } }
                }.start()
            }
            val sockets = servers.map { server ->
                server.soTimeout = 75_000
                Thread.sleep(300L)
                if (workerProcesses.none { it.isAlive }) {
                    val dead = workerProcesses.map { runCatching { it.exitValue() }.getOrNull() }
                    throw IllegalStateException("Все native workers завершились: $dead")
                }
                val socket = server.accept()
                protect(socket)
                socket.tcpNoDelay = true
                workerSockets += socket
                socket
            }
            val inputs = sockets.map { DataInputStream(it.getInputStream().buffered(64 * 1024)) }
            val outputs = sockets.map { DataOutputStream(it.getOutputStream().buffered(64 * 1024)) }
            tunnelRunning = true
            nativeAuthenticated = true
            publish("CONNECTED", "${urls.size} Yandex-канала подключены")
            startTrafficStats()
            startForeground(NOTIFICATION_ID, notification("PaperFlux: подключён (${urls.size} канала)"))
            // Use duplicated descriptors: constructing two Java streams over
            // the same ParcelFileDescriptor can close/invalid-ate the TUN fd
            // when one stream is finalized on MIUI.
            val tunInFd = tun!!.dup()
            val tunOutFd = tun!!.dup()
            val tunIn = ParcelFileDescriptor.AutoCloseInputStream(tunInFd)
            val tunOut = ParcelFileDescriptor.AutoCloseOutputStream(tunOutFd)
            val writeLock = Any()
            val tunInPackets = java.util.concurrent.atomic.AtomicLong(0)
            val tunOutPackets = java.util.concurrent.atomic.AtomicLong(0)
            sockets.forEachIndexed { idx, _ ->
                Thread {
                    try {
                        while (!stopping) {
                            val n = inputs[idx].readInt()
                            if (n <= 0 || n > 65535) break
                            val packet = ByteArray(n); inputs[idx].readFully(packet)
                            synchronized(writeLock) { tunOut.write(packet); tunOut.flush() }
                            val outCount = tunOutPackets.incrementAndGet()
                            if (outCount <= 5 || outCount % 1000L == 0L) Log.i("OpenFluxVpn", "TUN <- worker ${idx + 1}: packet=$outCount bytes=$n")
                        }
                } catch (e: Exception) { if (!stopping) { Log.e("OpenFluxVpn", "Worker ${idx + 1} packet read failed", e); publish("ERROR", "Обрыв worker ${idx + 1}: ${e.message}") } }
                }.start()
            }
            Thread {
                val buf = ByteArray(65535)
                try {
                    while (!stopping) {
                        val n = tunIn.read(buf); if (n <= 0) break
                        val inCount = tunInPackets.incrementAndGet()
                        val lane = packetLane(buf, n, sockets.size)
                        if (inCount <= 5 || inCount % 1000L == 0L) Log.i("OpenFluxVpn", "TUN -> worker ${lane + 1}: packet=$inCount bytes=$n")
                        synchronized(outputs[lane]) { outputs[lane].writeInt(n); outputs[lane].write(buf, 0, n); outputs[lane].flush() }
                    }
                } catch (e: Exception) { if (!stopping) { Log.e("OpenFluxVpn", "TUN dispatcher failed", e); publish("ERROR", "Чтение VPN-интерфейса остановлено: ${e.message}") } }
            }.start()
        } catch (e: Exception) {
            servers.forEach { runCatching { it.close() } }
            throw IllegalStateException("WorkerManager: ${e.message}", e)
        } finally { servers.forEach { runCatching { it.close() } } }
    }

    private fun packetLane(packet: ByteArray, length: Int, count: Int): Int {
        var h = 2166136261u
        // Hash only the flow key. Including TCP sequence/flags would send
        // successive segments of one connection to different workers.
        val ihl = if (length >= 20 && (packet[0].toInt() ushr 4) == 4) (packet[0].toInt() and 0x0f) * 4 else 0
        val proto = if (ihl >= 20 && length >= ihl) packet[9].toUInt() else 0u
        if (ihl >= 20 && length >= ihl + 4) {
            val a = ByteArray(6) { i -> if (i < 4) packet[12 + i] else packet[i - 4 + ihl] }
            val b = ByteArray(6) { i -> if (i < 4) packet[16 + i] else packet[i - 4 + ihl + 2] }
            // Commutative mixing keeps both packet directions on one lane.
            for (i in 0 until 6) h = (h xor (a[i].toUInt() + b[i].toUInt())) * 16777619u
        }
        h = (h xor proto) * 16777619u
        return (h % count.toUInt()).toInt()
    }

    private fun stopProcessOnly() {
        val old = process
        process = null
        if (old != null) runCatching {
            old.destroy()
            if (!old.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) old.destroyForcibly()
        }
        synchronized(workerSockets) { workerSockets.forEach { runCatching { it.close() } }; workerSockets.clear() }
        synchronized(workerProcesses) { workerProcesses.forEach { runCatching { it.destroy() } }; workerProcesses.clear() }
    }
    private fun releaseTunnelResources() {
        stopProcessOnly(); runCatching { tun?.close() }; tun = null
        nativeAuthenticated = false; nativeTunnelReady = false; tunnelRunning = false
    }
    private fun stopTunnel(announce: Boolean = true, clearDesired: Boolean = true) {
        if (clearDesired) preferences().edit().putBoolean(DESIRED_ACTIVE, false).apply()
        sessionGeneration.incrementAndGet()
        cancelReconnects()
        stopping = true
        tunnelRunning = false
        statsRunning = false
        networkWatchRunning = false
        cancelRestartAlarm(); releaseTunnelResources()
        if (announce) publish("DISCONNECTED", "Туннель отключён")
        stopForeground(STOP_FOREGROUND_REMOVE)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun startTrafficStats() {
        if (statsRunning) return
        statsRunning = true
        Thread {
            while (statsRunning && tunnelRunning) {
                // Byte counters come from the native Yandex transport log.
                // TrafficStats is UID-wide (WebView/control traffic included)
                // and must not overwrite the tunnel counters.
                if (nativeTunnelReady) {
                    persistAndBroadcastStats()
                    updateNotification("Подключено · ↓ ${formatBytes(lastRxBytes)}  ↑ ${formatBytes(lastTxBytes)}")
                }
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }.start()
    }

    private fun acceptNativeStats(rx: Long, tx: Long, ping: Long) {
        synchronized(statsLock) {
        // A replacement native worker reports fresh counters from zero. Fold
        // its previous maximum into the session total instead of making the
        // UI appear to lose traffic after a reconnect.
        val now = android.os.SystemClock.elapsedRealtime()
        // One native process reports cumulative counters.  A jump above this
        // bound within a five-second reporting period is not tunnel traffic;
        // it is an out-of-order/mismatched source and must not be presented as
        // a fabricated multi-gigabyte session total.
        val elapsed = now - lastNativeStatsAt
        val rxJump = rx - nativeRxBytes
        val txJump = tx - nativeTxBytes
        val maxReasonableJump = 256L * 1024L * 1024L
        if (lastNativeStatsAt > 0L && elapsed <= 15_000L &&
            ((rxJump > maxReasonableJump && rx >= nativeRxBytes) || (txJump > maxReasonableJump && tx >= nativeTxBytes))) {
            Log.w("OpenFluxVpn", "Ignoring implausible native counter jump rx=$rxJump tx=$txJump in ${elapsed}ms")
            return@synchronized
        }
        if (rx < nativeRxBytes) rxOffsetBytes += nativeRxBytes
        if (tx < nativeTxBytes) txOffsetBytes += nativeTxBytes
        nativeRxBytes = rx.coerceAtLeast(0L)
        nativeTxBytes = tx.coerceAtLeast(0L)
        lastNativeStatsAt = now
        lastRxBytes = rxOffsetBytes + nativeRxBytes
        lastTxBytes = txOffsetBytes + nativeTxBytes
        if (ping > 0L) lastPingMs = ping
        persistAndBroadcastStats()
        }
    }

    private fun persistAndBroadcastStats() = synchronized(statsLock) {
        TunnelSnapshot.updateStats(this, lastRxBytes, lastTxBytes, lastPingMs)
        val duration = TunnelSnapshot.read(this).optLong("durationSec", 0L)
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra("rx", lastRxBytes).putExtra("tx", lastTxBytes)
            .putExtra("ping", lastPingMs).putExtra("duration", duration))
    }

    private fun startNetworkWatchdog() {
        if (networkWatchRunning) return
        networkWatchRunning = true
        Thread {
            var misses = 0
            while (networkWatchRunning && wantsConnection() && !stopping) {
                if (hasUnderlyingInternet()) {
                    misses = 0
                } else {
                    misses++
                    if (misses >= 2) { pauseForMissingNetwork(); break }
                }
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
            }
        }.start()
    }

    private fun hasUnderlyingInternet(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java)
        return manager.allNetworks.any { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@any false
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@any false
            true
        }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> "%.1f ГБ".format(Locale.US, value / (1024.0 * 1024 * 1024))
        value >= 1024L * 1024L -> "%.1f МБ".format(Locale.US, value / (1024.0 * 1024))
        value >= 1024L -> "%.1f КБ".format(Locale.US, value / 1024.0)
        else -> "$value Б"
    }

    private fun measurePing(): Long {
        val targets = arrayOf("www.google.com" to 443, "1.1.1.1" to 443)
        for ((host, port) in targets) {
            val started = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), 1800) }
                return ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            } catch (_: Exception) { /* try fallback */ }
        }
        return -1L
    }
    private fun requireValidatedNetwork() {
        val manager = getSystemService(ConnectivityManager::class.java)
        val active = manager.activeNetwork
        val caps = active?.let { manager.getNetworkCapabilities(it) }
        // On mobile networks Android may omit VALIDATED while traffic is
        // perfectly usable (operator DNS/captive checks can be delayed).
        // INTERNET is the relevant capability for opening the Yandex session.
        check(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) {
            "Сеть: не найдено подключение к интернету. Включите Wi‑Fi или мобильные данные"
        }
    }
    private fun preferences() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun wantsConnection() = preferences().getBoolean(DESIRED_ACTIVE, false)
    private fun autoReconnectEnabled() = preferences().getBoolean(AUTO_RECONNECT, true)
    private fun cancelFullRecovery() {
        recoveryGeneration.incrementAndGet()
        fullRecoveryFuture?.cancel(false); fullRecoveryFuture = null
        fullRecoveryScheduled.set(false)
    }
    private fun cancelReconnects() {
        cancelFullRecovery()
        reconnectFuture?.cancel(false); reconnectFuture = null
        reconnectScheduled.set(false); reconnectAttempt = 0
    }
    /** Recreate TUN + native transport after a grace period unless a new TUNNEL_READY arrives. */
    private fun scheduleFullRecovery(reason: String) {
        if (!fullRecoveryScheduled.compareAndSet(false, true)) return
        val generation = recoveryGeneration.incrementAndGet()
        fullRecoveryFuture = reconnectWorker.schedule({
            fullRecoveryScheduled.set(false)
            if (generation == recoveryGeneration.get() && wantsConnection() && autoReconnectEnabled() && !nativeTunnelReady) {
                sessionGeneration.incrementAndGet()
                releaseTunnelResources()
                scheduleReconnect(reason, immediate = true)
            }
        }, 20, TimeUnit.SECONDS)
    }
    /** Stop the failed path immediately and wait for Android to report a usable network. */
    private fun pauseForMissingNetwork() {
        if (!wantsConnection() || hasUnderlyingInternet()) return
        if (!autoReconnectEnabled()) {
            stopTunnel(announce = true)
            return
        }
        sessionGeneration.incrementAndGet()
        starting = false
        statsRunning = false
        networkWatchRunning = false
        cancelFullRecovery()
        releaseTunnelResources()
        publish("WAITING_NETWORK", "Интернет недоступен. Ожидаем Wi‑Fi или мобильную сеть")
        startForeground(NOTIFICATION_ID, notification("PaperFlux: ожидаем сеть"))
    }
    private fun scheduleReconnect(reason: String, immediate: Boolean = false) {
        if (!wantsConnection() || !autoReconnectEnabled() || stopping) return
        if (!reconnectScheduled.compareAndSet(false, true)) return
        val attempt = ++reconnectAttempt
        val baseDelay = if (immediate) 0L else minOf(30_000L, 1_000L shl minOf(attempt - 1, 5))
        val jitter = if (baseDelay == 0L) 0L else kotlin.random.Random.nextLong(0L, baseDelay / 3L + 1L)
        publish("RECONNECTING", "$reason. Повторяем попытку")
        reconnectFuture = reconnectWorker.schedule({
            reconnectScheduled.set(false)
            if (!wantsConnection() || !autoReconnectEnabled()) return@schedule
            // Network callbacks are noisy while VPN establishes its own
            // interface. A queued callback must not replace a session that is
            // already starting or has just become healthy.
            if (tunnelRunning || starting) return@schedule
            if (!hasUnderlyingInternet()) {
                publish("WAITING_NETWORK", "Ожидаем подключения к Wi‑Fi или мобильной сети")
                return@schedule
            }
            starting = true
            worker.execute { startTunnel() }
        }, baseDelay + jitter, TimeUnit.MILLISECONDS)
    }
    private fun explainFailure(error: Throwable): String {
        val message = error.message.orEmpty()
        val lower = message.lowercase()
        return when {
            "access_network_state" in lower -> "Приложение: нет доступа к состоянию сети Android"
            "internet недоступен" in lower || "активного подключения" in lower -> message
            "permission" in lower || "vpn" in lower -> "VPN: нет разрешения Android"
            "tun" in lower || "descriptor" in lower -> "Не удалось создать VPN-интерфейс"
            "poll" in lower || "http" in lower || "websocket" in lower || "network" in lower -> "Не удалось связаться с Yandex Docs. Проверьте интернет и повторите попытку"
            "document" in lower || "config" in lower -> "Не удалось открыть документ. Проверьте профиль и ссылку"
            else -> "Не удалось запустить защищённое соединение. Повторите попытку"
        }
    }
    private fun redactNativeLog(value: String): String = value
        .replace(Regex("(?i)(token|sign|access_token|cookie)=?[^&\\s]+"), "$1=<скрыто>")
        .take(240)
    private fun publishEvent(event: String) {
        // Native output can repeat the same dial/refused message hundreds of
        // times during a reconnect. Keep the journal useful and avoid
        // waking WebView/SharedPreferences for every duplicate line.
        val now = System.currentTimeMillis()
        if (event == lastUiEvent && now - lastUiEventAt < 5_000L) return
        lastUiEvent = event
        lastUiEventAt = now
        SessionJournal.append(this, event, journalLevel(event), "system", "Система")
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last_event", event).apply()
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("event", event))
    }
    private fun shouldPublishNativeEvent(value: String): Boolean {
        val text = value.lowercase()
        // Cursor frames and regular Engine.IO pings are high-frequency noise;
        // forwarding every one to SharedPreferences and the UI caused visible
        // lag and unnecessary battery wakeups. Keep lifecycle and diagnostics.
        if (text.contains("tunnel_healthy") || text.contains("createendpointconnection was refused")) return false
        return text.contains("bootstrap failed") || text.contains("transport lost") ||
            text.contains("fatal") || text.contains("congested")
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the activity away must not tear down an active VPN. Keep
        // the last document/session state so START_STICKY can restore it if
        // the OEM recreates the service process.
        if (wantsConnection()) {
            preferences().edit()
                .putString("state", if (nativeTunnelReady) "CONNECTED" else "RECONNECTING")
                .putString("document", documentUrl)
                .apply()
            startForeground(NOTIFICATION_ID, notification(if (nativeTunnelReady) connectedNotificationText() else "PaperFlux: восстановление соединения"))
            val restart = Intent(this, RestartReceiver::class.java).setPackage(packageName)
            val pending = PendingIntent.getBroadcast(this, 9182, restart, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            (getSystemService(ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1500L,
                pending
            )
        }
        super.onTaskRemoved(rootIntent)
    }
    private fun cancelRestartAlarm() {
        val restart = Intent(this, RestartReceiver::class.java).setPackage(packageName)
        val pending = PendingIntent.getBroadcast(this, 9182, restart, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pending)
    }
    override fun onDestroy() {
        stopTunnel(announce = false, clearDesired = false)
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        worker.shutdownNow(); reconnectWorker.shutdownNow(); super.onDestroy()
    }
    override fun onRevoke() {
        // Android revoked the system VPN permission.  There is no usable TUN
        // to reconnect to, so remove the foreground state and require a new
        // explicit user start instead of leaving a misleading VPN icon.
        stopTunnel(announce = true, clearDesired = true)
        super.onRevoke()
    }
    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else null
    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_paperflux_notification)
        .setContentTitle("PaperFlux")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOnlyAlertOnce(true)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить",
            PendingIntent.getService(this, 921, Intent(this, OpenFluxVpnService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .build()
    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification(text))
    }
    private fun connectedNotificationText() = "Подключено · ↓ ${formatBytes(lastRxBytes)}  ↑ ${formatBytes(lastTxBytes)}"
    override fun onCreate() {
        super.onCreate()
        (getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "PaperFlux VPN", NotificationManager.IMPORTANCE_DEFAULT))
        runCatching { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback) }
    }
    private fun publish(state: String, detail: String) {
        SessionJournal.append(
            this,
            detail,
            when (state) { "ERROR" -> "error"; "CONNECTED" -> "success"; "RECONNECTING", "WAITING_NETWORK" -> "warning"; else -> "info" },
            if (state == "WAITING_NETWORK" || state == "DNS") "network" else "connection",
            state
        )
        TunnelSnapshot.write(this, state, detail)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("state", state).putString("detail", detail).putLong("updated", System.currentTimeMillis()).apply()
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra("state", state).putExtra("detail", detail).putExtra("time", System.currentTimeMillis()))
        if (state == "ERROR") updateNotification("Ошибка · $detail")
        else if (state == "CONNECTED") updateNotification(connectedNotificationText())
        else updateNotification(detail)
    }

    companion object {
        const val START="com.accar.openflux.START"
        const val STOP="com.accar.openflux.STOP"
        const val UPDATE_SETTINGS="com.accar.openflux.UPDATE_SETTINGS"
        const val ACTION_STATE="com.accar.openflux.STATE"
        const val EXTRA_DOCUMENT_URL="document_url"
        const val EXTRA_RESUME="resume_existing_session"
        const val PREFS="openflux_state"
        const val EXCLUDED_APPS="excluded_apps"
        const val DESIRED_ACTIVE="desired_active"
        const val AUTO_RECONNECT="auto_reconnect"
        const val EXTRA_AUTO_RECONNECT="extra_auto_reconnect"
        private const val CHANNEL="paperflux_tunnel_v2"
        private const val NOTIFICATION_ID=7
        @Volatile private var lastUiEvent: String = ""
        @Volatile private var lastUiEventAt: Long = 0L
        @Volatile private var documentUrl: String = ""
        @Volatile private var tunnelRunning = false

        fun isTunnelRunning(): Boolean = tunnelRunning

        private fun journalLevel(message: String): String = when {
            Regex("(?i)error|failed|обрыв|refused|1005|broken pipe|reset").containsMatchIn(message) -> "error"
            Regex("(?i)reconnect|ожидаем|waiting|bootstrap").containsMatchIn(message) -> "warning"
            Regex("(?i)connected|ready|готов|успеш").containsMatchIn(message) -> "success"
            else -> "info"
        }
    }
}
