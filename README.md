# PaperFlux Android

<p align="center">
  <img src="docs/images/paperflux-mark.svg" width="112" alt="PaperFlux logo">
</p>

<h1 align="center">PaperFlux Android</h1>

<p align="center"><b>Private document tunnel for Android</b><br>Profiles · Material 3 · Yandex Docs transport</p>

<p align="center">
  <img src="https://img.shields.io/badge/status-research-6957e8?style=flat-square" alt="Research status">
  <img src="https://img.shields.io/badge/Android-8%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-6f42c1?style=flat-square" alt="arm64-v8a">
  <img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="GPL-3.0">
</p>

<p align="center"><a href="#paperflux-android">English</a> · <a href="README.ru.md">Русский</a></p>

PaperFlux is a small Android client for a Yandex Docs profile supplied by the user. Add a profile, press connect, and the foreground service keeps the session alive while the interface is closed. The app contains no preconfigured server or document.

> Research software. Use only with documents, servers, and networks you are authorized to use.

## Screens

<div align="center">
  <img src="docs/images/01-home.jpg" width="160" alt="PaperFlux home">
  <img src="docs/images/02-profiles.jpg" width="160" alt="PaperFlux profiles">
  <img src="docs/images/03-logs.jpg" width="160" alt="PaperFlux event log">
  <img src="docs/images/04-settings.jpg" width="160" alt="PaperFlux settings">
</div>
<p align="center"><sub>App screens are cropped without the Android status and navigation bars.</sub></p>

## How it works

The profile supplies the document endpoint and session parameters at runtime. The Android service creates a TUN interface, the native worker carries framed traffic through Yandex Engine.IO/WebSocket, and the companion exit node opens the destination connection from the VPS.

```text
Profile → Android TUN → PaperFlux transport → exit node → Internet
```

## Client features

- profile manager with clipboard, file, and manual import;
- encrypted local profile storage; tokens are not shown in the profile list;
- Yandex Docs polling handshake followed by WebSocket upgrade;
- Android `VpnService` TUN integration and DNS routing;
- foreground-service lifecycle with automatic recovery;
- persistent per-session event journal and traffic counters;
- bundled native worker for `arm64-v8a`.

The matching exit-node implementation lives in the companion [PaperFlux Server](https://github.com/Flofyyk/PaperFlux) repository. The project is based on [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux); the PaperFlux UI and Android lifecycle are maintained separately.

## Requirements

- Android 8.0 (API 26) or newer;
- Android SDK 35;
- JDK 17;
- Android NDK 27.0.12077973 or newer when rebuilding the native library;
- a permitted Yandex Docs document and a matching PaperFlux exit node.

## Build

```bash
./gradlew :app:assembleDebug
```

The build embeds the Material 3 UI from `app/src/main/assets/paperflux` and packages the native worker from `app/src/main/jniLibs/arm64-v8a`.

Install a debug build with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Importing a profile

The app accepts a `paperflux://config` URI or JSON file. A minimal URI looks like:

```text
paperflux://config?id=1&name=Home&server=203.0.113.10&ip=10.10.10.2&doc=https%3A%2F%2Fdisk.yandex.ru%2Fi%2Fexample&token=YOUR_TOKEN
```

Never commit a real token, document URL, or private server address. Import profiles locally instead.

## License

The Android client follows the OpenFlux project license. See [LICENSE](https://github.com/p1neappleXpress/OpenFlux/blob/main/LICENSE) and the companion server repository for third-party notices.
