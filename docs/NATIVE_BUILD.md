# Native core for Android 0.4.31

Bundled version: `0.5.18`.

Source: [PaperFlux commit 2db20a8becd9bfa1fd3acc739453ab43d6511f83](https://github.com/Flofyyk/PaperFlux/tree/2db20a8becd9bfa1fd3acc739453ab43d6511f83).

The Session protocol is unchanged. Updating both ends is recommended so Mail.ru browser verification and the read watchdog also run on the server.

Build all four Android ABI binaries from that source using Go 1.26.4+ and Android NDK 27.0.12077973+:

```powershell
.\scripts\build-android-native.ps1 -NdkPath '<NDK path>' -AndroidProject '<Android checkout>' -Version '0.5.18'
```

Then build the Android release with JDK 17+ and SDK 35:

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Use the existing installation certificate when preparing an update; do not publish signing keys or device-specific test fixtures.
