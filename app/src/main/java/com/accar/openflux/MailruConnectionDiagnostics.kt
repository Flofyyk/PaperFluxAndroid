package com.accar.openflux

/** Whitelisted lifecycle messages only; never forward provider bodies/URLs. */
internal object MailruConnectionDiagnostics {
    private val verificationWait = Regex("browser verification limited; retry in ([0-9]{1,10}) seconds")

    fun message(line: String): String? {
        val event = line.substringAfter("[M-DOCS] ", "")
        verificationWait.matchEntire(event)?.let {
            return "Mail.ru пока не подтвердил доступ. Следующая попытка через ${it.groupValues[1]} с"
        }
        return when {
            event == "browser verification completed; retrying document request" -> "Проверка Mail.ru пройдена автоматически; открываем документ"
            event == "receive timeout; reconnecting document channel" -> "Mail.ru перестал отвечать; восстанавливаем документный канал"
            event == "cannot open the document; retrying" -> "Mail.ru не открыл документ. Проверьте публичную ссылку и доступ к документу; повторяем попытку"
            event.startsWith("WebSocket dial failed (http ") -> "Не удалось соединиться с редактором Mail.ru; повторяем попытку"
            event == "Socket.IO handshake failed; retrying" -> "Mail.ru не завершил начальное соединение; повторяем попытку"
            event == "Socket.IO connection rejected; reconnecting" || event.startsWith("editor authentication rejected (code=") || event.startsWith("editor rejected connection (event=") -> "Редактор Mail.ru отклонил соединение; подключаемся заново"
            event == "connection to the document dropped; reconnecting" -> "Соединение с документом Mail.ru прервалось; восстанавливаем канал"
            event == "Editor authentication completed" -> "Редактор Mail.ru подтвердил доступ. Проверяем защищённый туннель"
            else -> null
        }
    }
}
