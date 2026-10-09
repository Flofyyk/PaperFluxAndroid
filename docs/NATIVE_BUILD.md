# Native core for Android 0.4.30

Bundled version: `0.5.17`.

Source: [PaperFlux commit 2cad723166c9ed087e417c9c26685284c3b76a72](https://github.com/Flofyyk/PaperFlux/tree/2cad723166c9ed087e417c9c26685284c3b76a72).

The Session protocol is unchanged. Updating both ends is recommended so the Mail.ru read watchdog also runs on the server.

Build all four Android ABI binaries from that source using Go 1.26.4+ and Android NDK 27.0.12077973+:

```powershell
.\scripts\build-android-native.ps1 -NdkPath '<NDK path>' -AndroidProject '<Android checkout>' -Version '0.5.17'
```

Then build the Android release with JDK 17+ and SDK 35:

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Use the existing installation certificate when preparing an update; do not publish signing keys or device-specific test fixtures.
