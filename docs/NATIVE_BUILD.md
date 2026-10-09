# Native core for Android 0.4.29

Bundled version: `0.5.16-android.1`.

Source: [PaperFlux commit 98fc96ac20cb7e38c737771276f5f70ba4280e4b](https://github.com/Flofyyk/PaperFlux/tree/98fc96ac20cb7e38c737771276f5f70ba4280e4b).

The standalone Android proxy adds no server protocol changes. Existing compatible Session servers do not require an update for this client release.

Build all four Android ABI binaries from that source using Go 1.26.4+ and Android NDK 27.0.12077973+:

```powershell
.\scripts\build-android-native.ps1 -NdkPath '<NDK path>' -AndroidProject '<Android checkout>' -Version '0.5.16-android.1'
```

Then build the Android release with JDK 17+ and SDK 35:

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

Use the existing installation certificate when preparing an update; do not publish signing keys or device-specific test fixtures.
