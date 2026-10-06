package com.accar.openflux

import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Fresh proof and authenticated encrypted reply; never HTTP or a raw token. */
internal object ProfileActivationClient {
    private class RejectedKey : IOException("Ключ профиля отклонён сервером")
    private val random = SecureRandom()
    private fun sign(token: String, label: String, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        update(label.toByteArray(Charsets.UTF_8)); doFinal(data)
    }

    fun exchange(profile: JSONObject): JSONObject {
        val host = profile.getString("server")
        val token = profile.getString("token")
        require(host.matches(Regex("[A-Za-z0-9.-]{1,253}"))) { "Неверный адрес сервера активации" }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, 24000), 2500)
            socket.soTimeout = 2500
            val input = DataInputStream(socket.getInputStream())
            val hello = ByteArray(37); input.readFully(hello)
            require(hello.copyOfRange(0, 5).contentEquals("PFPC1".toByteArray(Charsets.US_ASCII))) { "Неверный ответ сервера" }
            val nonce = ByteArray(32).also(random::nextBytes)
            val pair = hello.copyOfRange(5, 37) + nonce
            val lookup = sign(token, "paperflux-profile-lookup-v1", byteArrayOf())
            val proof = sign(token, "paperflux-profile-client-v1", pair + lookup)
            socket.getOutputStream().write(nonce + lookup + proof)
            if (input.readUnsignedByte() != 1) throw RejectedKey()
            val iv = ByteArray(12); input.readFully(iv)
            val size = input.readInt()
            require(size in 16..8208) { "Ответ сервера слишком большой" }
            val sealed = ByteArray(size); input.readFully(sealed)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sign(token, "paperflux-profile-response-v1", pair), "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("PFPC1".toByteArray(Charsets.US_ASCII) + pair)
            val reply = JSONObject(String(cipher.doFinal(sealed), Charsets.UTF_8))
            require(reply.getString("id") == profile.getString("id") && reply.getString("clientIp") == profile.getString("clientIp")) { "Сервер вернул другой профиль" }
            reply.put("token", token).put("server", host)
            return reply
        }
    }

    fun activate(profile: JSONObject, current: () -> Boolean): JSONObject {
        var busy = false
        repeat(8) { attempt ->
            check(current()) { "Подключение отменено" }
            val reply = try { exchange(profile) } catch (e: RejectedKey) {
                throw IllegalStateException("Ключ профиля отклонён сервером. Получите действующий конфиг в боте")
            } catch (e: Exception) { null }
            if (reply != null) {
                if (reply.optString("activation") == "denied") throw IllegalStateException("Профиль заблокирован или квота исчерпана")
                busy = reply.optString("activation") == "busy"
                if (!reply.optBoolean("activationRequired") || reply.optString("activation") == "accepted") {
                    return ProfileConfig.parse(reply.toString())
                }
            }
            if (attempt < 7) {
                // Jitter avoids hundreds of handsets retrying in lockstep.
                val until = android.os.SystemClock.elapsedRealtime() + 600 + random.nextInt(900)
                while (current() && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(100)
            }
        }
        throw IllegalStateException(if (busy) "Все активные места VPS заняты. Повторите подключение позже" else "Сервер активации недоступен. Проверьте сеть и повторите подключение")
    }
}
