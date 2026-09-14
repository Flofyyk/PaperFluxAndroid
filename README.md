# PaperFlux Android

**Private document tunnel for Android** · **Material 3** · **arm64**

PaperFlux is a small Android client for a user-supplied Yandex Docs profile. Add a profile, press connect, and the app routes device traffic through the encrypted PaperFlux channel. The connection is owned by a foreground service, so the UI may be closed without ending an active session.

> Research software. Use only with documents, servers, and networks you are authorized to use.

## Screens

<p align="center">
  <img src="docs/images/01-home.jpg" width="220" alt="PaperFlux home">
  <img src="docs/images/02-profiles.jpg" width="220" alt="PaperFlux profiles">
  <img src="docs/images/03-logs.jpg" width="220" alt="PaperFlux session log">
  <img src="docs/images/04-settings.jpg" width="220" alt="PaperFlux settings">
</p>

The images are cropped from the app screens: Android status and navigation bars are intentionally excluded.

## How it works

The app deliberately contains no server address or ready-made document. A profile supplies the endpoint details, the Android service creates a TUN interface, and the native transport carries the session to an OpenFlux exit node.

```text
Profile → Android TUN → PaperFlux transport → exit node → Internet
```

## Client features

- profile manager with import from clipboard, file, or manual fields;
- encrypted local profile storage (the document token never appears in the public profile list);
- Yandex Docs Engine.IO polling → WebSocket upgrade;
- Android `VpnService` TUN integration and DNS routing;
- background foreground-service lifecycle with automatic recovery;
- persistent per-session event journal and traffic counters;
- a bundled native worker for the `arm64-v8a` ABI.

The matching exit-node implementation is maintained in the companion [OpenFlux repository](https://github.com/Flofyyk/OpenFlux). The project started from [p1neappleXpress/OpenFlux](https://github.com/p1neappleXpress/OpenFlux); the PaperFlux UI and Android lifecycle are separate work.

## Requirements

- Android 8.0 (API 26) or newer;
- Android SDK 35;
- JDK 17;
- Android NDK 27.0.12077973 or newer when rebuilding the native library;
- an OpenFlux profile containing a permitted Yandex Docs document URL and access token.

## Build

```bash
./gradlew :app:assembleDebug
```

The Gradle build embeds the bundled React UI from `app/src/main/assets/paperflux` and packages the native library from `app/src/main/jniLibs/arm64-v8a`.

Install a debug build with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Installing an update stops the current VPN service; reconnect once after updating.

## Importing a profile

The app accepts a `paperflux://config` URI or JSON file. A minimal URI looks like:

```text
paperflux://config?id=1&name=Home&server=203.0.113.10&ip=10.10.10.2&doc=https%3A%2F%2Fdisk.yandex.ru%2Fi%2Fexample&token=YOUR_TOKEN
```

Never commit a real token, document URL, or private server address. Import profiles locally instead. The app masks tokens in the profile list and bounds the local session journal.

## License

The Android client follows the license of the OpenFlux project. See [LICENSE](https://github.com/p1neappleXpress/OpenFlux/blob/main/LICENSE) and the companion repository for third-party notices.
