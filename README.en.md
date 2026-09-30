<p align="center">
  <img src="docs/images/paperflux-mark.svg" width="112" alt="PaperFlux logo">
</p>

<h1 align="center">PaperFlux Android</h1>

<p align="center"><b>Android VPN client with Yandex Docs transport</b></p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/ABI-ARM64%20%C2%B7%20ARMv7%20%C2%B7%20x86%20%C2%B7%20x86__64-6f42c1?style=flat-square" alt="ARM64, ARMv7, x86, x86_64">
  <img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="GPL-3.0">
</p>

<p align="center"><a href="README.en.md">English</a> · <a href="README.md">Русский</a></p>

PaperFlux Android is a VPN client that connects to a PaperFlux server through Yandex Docs. It includes connection profiles, per-app exclusions, traffic statistics and an event journal.

To connect, scan a QR code with the built-in camera scanner, import a configuration from the clipboard or a file, or enter a name, server IP/domain and profile password. Manual setup retrieves the remaining settings from the server. Any PaperFlux server owner can enable the optional [profile discovery service](https://github.com/Flofyyk/PaperFlux/blob/main/docs/PROFILES.md); without it, import a complete configuration. The profile password is an access key, not the VPS SSH/root password.

## Interface

<div align="center">
  <img src="docs/images/01-home.jpg" width="160" alt="PaperFlux home">
  <img src="docs/images/02-profiles.jpg" width="160" alt="PaperFlux profiles">
  <img src="docs/images/03-logs.jpg" width="160" alt="PaperFlux event log">
  <img src="docs/images/04-settings.jpg" width="160" alt="PaperFlux settings">
</div>
<p align="center"><sub>Home · Profiles · Journal · Settings</sub></p>

## Features

- Android VPN service with session traffic counters and a persistent event journal.
- Profiles with selection, editing and deletion; QR, clipboard and file import or manual entry.
- Profile sharing with a locally generated QR code, link copying and the Android share sheet for links or QR images. Shared configurations contain the access key: send them only to trusted recipients.
- An in-app QR scanner with a flashlight. Decoding is local, without Google Lens or Google Play Services; images are neither stored nor uploaded.
- Per-app exclusions are persisted and applied automatically with a brief VPN reconnect. Restart an excluded app if it retains old connections. Routing exclusions do not hide the system-wide presence of a VPN.
- Collapsible profile actions, country flags when address lookup succeeds, and ICMP server latency checks. Blocked ICMP does not mean the VPN is unavailable.
- Yandex Docs transport with authenticated tunnel readiness checks and reconnection handling.
- Foreground notification with connection state, traffic totals and a disconnect action.
- Profile secrets stored in encrypted device storage.

## Requirements

- Android 8.0 or later.
- `arm64-v8a`, `armeabi-v7a`, `x86`, or `x86_64` architecture.
- A compatible PaperFlux server and a valid connection profile.

[Download APK releases](https://github.com/Flofyyk/PaperFluxAndroid/releases)

Choose **universal** if unsure about your device architecture. Architecture-specific APKs provide the same features in a smaller download. ARM64 has been tested on a physical device; other ABIs have build and packaging checks only.

## Transports and recovery

Android 0.4.16 is compatible with PaperFlux Server 0.5.6–0.5.8 using `--session`. Upgrade grouped proxy servers to 0.5.8 to prevent stale flows from accumulating after reconnects. Session uses batched/zstd and is not interchangeable with the legacy PFS2 protocol. Enable the profile discovery service for address-and-key setup; complete configuration import does not require it.

New profiles offer Yandex and Mail.ru Docs. Imported profiles retain their underlying Yandex protocol type. Volga profiles require a separate empty document because that transport modifies document content.

Yandex access challenges open in a dedicated verification window. Document requests are queued, initial and redirected-page cookies are collected, and user verification is separate from connection timeouts. Session does not bypass Yandex CAPTCHA requirements. The service channel is not used for regular VPN traffic. See [server transport configuration](https://github.com/Flofyyk/PaperFlux/blob/main/docs/TRANSPORTS.md).

A restored document channel triggers an immediate DNS/TCP check. Restarted servers must answer a fresh cryptographic challenge before replacing the peer session. Brief interruptions no longer force an unnecessary VPN restart, and repeated service starts preserve foreground status.

## Installation and connection

1. Download the APK from the [latest release](https://github.com/Flofyyk/PaperFluxAndroid/releases/latest) and install it.
2. Open Profiles and scan a QR code, import a configuration from the clipboard or a file, or enter it manually.
3. Select a profile and tap the connect button on the home screen.
4. On first connection, approve the Android VPN permission dialog.

To bypass the VPN for specific apps, select them under Settings → App exclusions. Changes are applied automatically. Restart the excluded app if it retains old connections.

The matching server source is in [PaperFlux Server](https://github.com/Flofyyk/PaperFlux). The project started from [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux); PaperFlux Android is maintained as a separate client.

## Build from source

Requirements: Android SDK 35, JDK 17+, and Node.js 20.19+ or 22.12+. UI sources are included in `web/`; Gradle installs locked dependencies and builds them. Four native ABI binaries are included. Rebuilding them requires Go 1.26.4+ and NDK 27.0.12077973+, using `scripts/build-android-native.ps1` in the server repository.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

## Disclaimer

PaperFlux is experimental research software provided **as is**, without warranties or guarantees of availability, privacy, security, performance, or fitness for a particular purpose. The authors do not operate a service, provide access credentials, or take responsibility for how the software is configured or used.

Use it only for education, research, and testing on systems, documents, servers, and networks you own or are explicitly authorized to use. You are solely responsible for legal compliance, configuration security, data handling, and all traffic generated by your installation.

## License

PaperFlux Android follows the OpenFlux project license. See [LICENSE](https://github.com/p1neappleXpress/OpenFlux/blob/main/LICENSE) and the companion server repository for third-party notices.
