# PaperFlux Android

<p align="center">
  <img src="docs/images/paperflux-mark.svg" width="112" alt="Логотип PaperFlux">
</p>

<h1 align="center">PaperFlux Android</h1>

<p align="center"><b>Приватный документный туннель для Android</b><br>Профили · Material 3 · транспорт Yandex Docs</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-research-6957e8?style=flat-square" alt="Исследовательский статус">
  <img src="https://img.shields.io/badge/Android-8%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-6f42c1?style=flat-square" alt="arm64-v8a">
  <img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="GPL-3.0">
</p>

<p align="center"><a href="README.md">English</a> · <b>Русский</b></p>

PaperFlux — Android-клиент, который получает профиль Yandex Docs от пользователя. Добавьте профиль, нажмите подключение, и foreground-сервис сохранит сессию при закрытии интерфейса. В приложении нет заранее заданного сервера или документа.

> Исследовательское ПО. Используйте только документы, серверы и сети, к которым у вас есть доступ.

## Экраны

<p align="center">
  <img src="docs/images/01-home.jpg" width="220" alt="Главный экран PaperFlux">
  <img src="docs/images/02-profiles.jpg" width="220" alt="Профили PaperFlux">
  <img src="docs/images/03-logs.jpg" width="220" alt="Журнал PaperFlux">
</p>
<p align="center">
  <img src="docs/images/04-settings.jpg" width="220" alt="Настройки PaperFlux">
</p>
<p align="center"><sub>Скриншоты обрезаны без системных строк Android.</sub></p>

## Как это работает

Профиль содержит endpoint документа и параметры сессии. Сервис Android создаёт TUN-интерфейс, native worker передаёт кадры через Yandex Engine.IO/WebSocket, а выходная нода открывает соединение назначения со стороны VPS.

```text
Профиль → Android TUN → PaperFlux transport → exit node → Интернет
```

## Возможности клиента

- менеджер профилей: буфер, файл и ручной ввод;
- зашифрованное локальное хранилище профилей;
- polling-handshake Yandex Docs с последующим WebSocket upgrade;
- Android `VpnService` TUN и маршрутизация DNS;
- foreground-сервис с автоматическим восстановлением;
- постоянный журнал текущей сессии и счётчики трафика;
- встроенный native worker для `arm64-v8a`.

Серверная часть находится в репозитории [PaperFlux Server](https://github.com/Flofyyk/PaperFlux). Проект основан на [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux), а интерфейс и Android-жизненный цикл PaperFlux поддерживаются отдельно.

## Требования и сборка

- Android 8.0 (API 26) или новее;
- Android SDK 35 и JDK 17;
- Android NDK 27.0.12077973+ для пересборки native-библиотеки.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Импорт профиля

Поддерживаются URI `paperflux://config` и JSON-файл. Реальный токен, ссылку документа и приватный адрес сервера нельзя добавлять в Git — импортируйте профиль локально.

## Лицензия

Android-клиент следует лицензии проекта OpenFlux. См. [LICENSE](https://github.com/p1neappleXpress/OpenFlux/blob/main/LICENSE) и уведомления о лицензиях в серверном репозитории.
