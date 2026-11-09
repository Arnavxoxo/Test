# App Version Checker (Android)

Android Studio project for an Android 12 / One UI 4.1 Galaxy S10.

## What this first build does
- Scans installed apps (including package name and installed version).
- Filters/searches the inventory and optionally includes system apps.
- Opens an APKMirror-targeted web search for each app.
- Lets the user select a downloaded `.apk` and inspects its package name, version, minimum SDK and native ABI directories.
- Rejects APKs whose minimum SDK is above Android 12 (API 31) or whose native libraries do not match the device's supported ABIs.

## Important limitations
- APKMirror has no documented public API for complete release discovery. This version opens a targeted search rather than scraping APKMirror or claiming it has found the latest release.
- One UI 4.1 compatibility cannot be guaranteed from an APK manifest. The checker verifies Android SDK and CPU ABI compatibility only. Samsung-specific runtime behavior still needs testing.
- A single APK from a split bundle may not be sufficient to install the app. APKM/APKS/XAPK bundles are not parsed in this first version.

## Build
1. Open this folder in Android Studio (Giraffe or newer; Android Gradle Plugin 8.7.3).
2. Install Android SDK Platform 35 and JDK 17 if prompted.
3. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
4. The debug APK will be under `app/build/outputs/apk/debug/`.

The app requests `QUERY_ALL_PACKAGES` because scanning the full installed-app inventory is its core function. This is intended for sideloading/personal use; Google Play has strict approval requirements for this permission.
