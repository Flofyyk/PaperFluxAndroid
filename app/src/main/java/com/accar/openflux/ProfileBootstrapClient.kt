package com.accar.openflux

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal class ProfileBootstrapClient {
    @Volatile private var socket: Socket? = null
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; runCatching { socket?.close() } }
    fun fetch(server: String, secret: String, name: String): JSONObject {
        check(secret.length in 32..128) { "Вставьте пароль профиля (ключ доступа) с сервера, не SSH-пароль" }
        check(server.length in 1..253 && !server.any { it.isWhitespace() || it in "/@?#\\" }) { "Укажите IP или домен сервера без https://" }
        val host = server.removePrefix("[").removeSuffix("]")
        check(host.isNotBlank()) { "Укажите адрес сервера" }
        Socket().use { connection ->
            socket = connection
            try {
                check(!cancelled) { "Запрос отменён" }
                connection.connect(InetSocketAddress(host, 24000), 5000)
                connection.soTimeout = 5000
                val input = DataInputStream(connection.getInputStream())
                val output = DataOutputStream(connection.getOutputStream())
                val magic = ByteArray(5).also { input.readFully(it) }
                check(String(magic, Charsets.US_ASCII) == "PFPC1") { "Сервер не поддерживает загрузку профиля" }
                val serverNonce = ByteArray(32).also { input.readFully(it) }
                val clientNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val pair = serverNonce + clientNonce
                val lookup = sign(secret, "paperflux-profile-lookup-v1", byteArrayOf())
                output.write(clientNonce + lookup + sign(secret, "paperflux-profile-client-v1", pair + lookup)); output.flush()
                check(input.readUnsignedByte() == 1) { "Пароль профиля неверен или профиль недоступен" }
                val nonce = ByteArray(12).also { input.readFully(it) }
                val size = input.readInt()
                check(size in 16..8208) { "Некорректный ответ сервера" }
                val sealed = ByteArray(size).also { input.readFully(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sign(secret, "paperflux-profile-response-v1", pair), "AES"), GCMParameterSpec(128, nonce))
                cipher.updateAAD(magic + pair)
                val profile = JSONObject(String(cipher.doFinal(sealed), Charsets.UTF_8))
                    .put("token", secret).put("server", server).put("name", name.trim().ifBlank { "PaperFlux" })
                check(!cancelled) { "Запрос отменён" }
                return ProfileConfig.parse(profile.toString())
            } finally { socket = null }
        }
    }
    private fun sign(secret: String, label: String, bytes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")); update(label.toByteArray(Charsets.US_ASCII)); doFinal(bytes)
    }
}
