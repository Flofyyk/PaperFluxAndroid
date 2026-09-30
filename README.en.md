<p align="center">
  <img src="docs/images/paperflux-mark.svg" width="112" alt="PaperFlux logo">
</p>

<h1 align="center">PaperFlux Android</h1>

<p align="center">Android VPN client with Yandex Docs and Mail.ru Docs transports</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8%2B-3ddc84?style=flat-square&logo=android&logoColor=white" alt="Android 8+">
  <img src="https://img.shields.io/badge/ABI-ARM64%20%C2%B7%20ARMv7%20%C2%B7%20x86%20%C2%B7%20x86__64-6f42c1?style=flat-square" alt="ARM64, ARMv7, x86, x86_64">
  <img src="https://img.shields.io/badge/license-GPL--3.0-orange?style=flat-square" alt="GPL-3.0">
</p>

<p align="center"><a href="README.md">Русский</a> · <b>English</b></p>

<p align="center"><a href="https://github.com/Flofyyk/PaperFluxAndroid/releases/latest">Download APK</a> · <a href="https://github.com/Flofyyk/PaperFlux">Server</a> · <a href="https://github.com/Flofyyk/PaperFluxAndroid/issues">Report an issue</a></p>

PaperFlux Android connects applications to a PaperFlux server through an encrypted document transport. A configured server and an access profile are required.

## Features

- Connection profiles with editing, diagnostics and configuration sharing.
- QR, clipboard and file import, or setup by server address and access key.
- Built-in QR scanner without Google Lens or Google Play Services.
- Per-app exclusions with automatic settings application.
- Two Yandex document channels and an optional Volga channel.
- Automatic reconnection and support for backup VPS configuration sets.
- Traffic statistics, connection latency and an event journal.
- VPN status notification with a disconnect action.
- Encrypted storage for profile keys.

## Requirements

- Android 8.0 or later.
- ARM64, ARMv7, x86 or x86_64.
- A PaperFlux server using Session and a valid configuration.

## Installation

Download an APK from the [latest release](https://github.com/Flofyyk/PaperFluxAndroid/releases/latest).

| APK | Target |
| --- | --- |
| `universal` | All supported architectures |
| `arm64-v8a` | 64-bit ARM devices |
| `armeabi-v7a` | 32-bit ARM devices |
| `x86` / `x86_64` | Intel/AMD devices and emulators |

Use `universal` if the device architecture is unknown.

1. Install the APK and open the app.
2. Add a configuration in Profiles.
3. Select the profile and tap the connect button.
4. Approve the Android VPN permission dialog.

## Profile setup

Import a complete configuration from a `paperflux://` link, file or QR code. Profiles can be shared by copying the link or sending a QR image through the Android share menu.

Address-based setup requires a name, server IP/domain and profile password. The remaining settings are retrieved through the optional [profile discovery service](https://github.com/Flofyyk/PaperFlux/blob/main/docs/PROFILES.md). If that service is not configured, import a complete configuration.

The profile password is a PaperFlux access key, not the VPS SSH password. Shared links and QR codes contain this key and should only be sent to trusted recipients.

## Transports and recovery

Yandex and Mail.ru Docs are supported. Volga requires a separate empty document with editing access because the transport modifies its content. Yandex access challenges open in a verification window when needed.

The Connected state requires an authenticated session and successful DNS/TCP checks. Disconnected document channels recover in the background. When the device is offline, the app waits for network connectivity; manual disconnection cancels recovery.

Backup sets contain separate configurations for already deployed servers. Switching requires automatic reconnection to be enabled. Changing VPS requires open TCP connections to be established again.

[Transport settings](https://github.com/Flofyyk/PaperFlux/blob/main/docs/TRANSPORTS.md) · [Backup servers](https://github.com/Flofyyk/PaperFlux/blob/main/docs/MULTIUSER.md)

## App exclusions

Select applications under Settings → App exclusions. The list is persisted and applied automatically with a brief VPN reconnect. Restart an excluded app if it retains existing connections.

Exclusions route traffic outside the tunnel but do not hide the system-wide presence of a VPN.

## Build

Android SDK 35, JDK 17+ and Node.js 20.19+ or 22.12+ are required.

```bash
./gradlew :app:assembleDebug
```

APKs are generated in `app/build/outputs/apk/debug/`. On Windows, use `gradlew.bat`.

UI sources are in `web/`; Gradle installs locked dependencies and builds the interface automatically. Native binaries are included for all four ABIs. Rebuilding the core requires Go 1.26.4+, Android NDK 27.0.12077973+ and [build-android-native.ps1](https://github.com/Flofyyk/PaperFlux/blob/main/scripts/build-android-native.ps1).

## License

[GPL-3.0-or-later](LICENSE). Third-party components are listed in [NOTICE](NOTICE). Based on [OpenFlux](https://github.com/p1neappleXpress/OpenFlux).

Provided as is, without warranties. Use on systems you own or are authorized to access.
