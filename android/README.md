# Wink for Android

The Android app, in `android/` of the Wink repository. The website that serves
it lives one level up and deploys to `https://wink.arkhins.com`.

The base of the app. Kotlin, Jetpack Compose, Material 3. Minimum
Android 8.0 (API 26), targets API 35. OkHttp for HTTP, kotlinx.serialization
for JSON, nothing else.

There is nothing inside yet. What the base gives you:

- **Version 0.0.0.0**, a four-segment scheme, in `app/build.gradle.kts`.
- **In-app updates.** On launch the app quietly asks for the latest release.
  If it is newer than the installed build, a popup offers to update: the APK
  downloads inside the app and is handed to Android's installer. The home
  screen has a "Check for updates" card for asking outright.
- **Config from `.env`.** The server address and update source are read at
  build time from the repository's root `.env` (or environment variables)
  into `BuildConfig`. Database and storage secrets in the same file are the
  website's and are never compiled into the APK.
- **The CTR mark** as launcher icon, splash screen and the home screen logo.
- **A release workflow** that builds, signs and publishes APKs to a GitHub
  Release when a tag is pushed.

## Build

Requirements: JDK 21 and the Android SDK (platform 35, build-tools 35.0.0).
Android Studio installs the SDK and can use JDK 21 as its Gradle JDK
(Settings → Build Tools → Gradle → Gradle JDK). Its own bundled runtime is
Java 25, which Gradle 8.13 does not support, so on the command line point
`JAVA_HOME` at a JDK 21, or set `org.gradle.java.home` in
`~/.gradle/gradle.properties`. Point `local.properties` at the SDK:

```
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
```

Then, from `android/`:

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected device or emulator
./gradlew assembleRelease        # signed if a keystore is configured, see below
```

Or open this folder in Android Studio and press Run.

## Configuration

Every value below is looked up in this order: an environment variable, the
same key in the repository's root `.env` (or an `android/.env`),
`local.properties`, then `gradle.properties` or `-P` on the command line. The result ends up in `BuildConfig` and is read
through `Config.kt`.

| `.env` / environment  | Gradle property   | Default                                 | What it is |
|-----------------------|-------------------|-----------------------------------------|------------|
| `WINK_BASE_URL`       | `wink.baseUrl`    | `https://wink.arkhins.com`              | The Wink server. API keys and data fetches go through it. No trailing slash. |
| `WINK_UPDATE_URL`     | `wink.updateUrl`  | `<baseUrl>/api/app-version`             | Reports the latest release (see below). |
| `WINK_GITHUB_REPO`    | `wink.githubRepo` | `Arkhins-0/wink`                        | `owner/name` whose Releases carry the APKs. Asked directly when the server cannot answer. Blank disables it. |

`.env` is gitignored; `.env.example` at the root shows the shape. The
database and S3 keys in `.env` belong to the website and are intentionally
not read by the app build.

Plain HTTP is refused except to `10.0.2.2`, `localhost` and `127.0.0.1`
(`res/xml/network_security_config.xml`), so a dev server on the host machine
works from the emulator with `WINK_BASE_URL=http://10.0.2.2:3000`.

## How the update popup works

1. `AppViewModel` runs `UpdateChecker.latest()` once at launch, and again
   whenever the home screen's version card is tapped.
2. `UpdateChecker` asks `WINK_UPDATE_URL` first. If that fails and
   `WINK_GITHUB_REPO` is set, it asks
   `https://api.github.com/repos/<repo>/releases/latest` directly and maps the
   response to the same shape. So updates work from the very first release,
   before `wink.arkhins.com` exists.
3. The reported version is compared to `BuildConfig.VERSION_NAME` segment by
   segment (`data/Versions.kt`). `0.0.0.1` beats `0.0.0.0`.
4. If newer, `UpdateAvailableDialog` shows over whatever screen is open, with
   the release notes. "Update" downloads the APK into the app's cache
   (`AppUpdater`), then hands it to the system installer through a
   `FileProvider`. The first time, Android asks the user to allow installs
   from this app; the dialog explains that and resumes when they come back.
5. Android only installs an update signed with the same key as the installed
   app, so every release must be signed with the same keystore.

### The server contract

`GET <WINK_BASE_URL>/api/app-version` (and `?fresh=1` to bypass any cache)
must return:

```json
{
  "version": "0.0.0.1",
  "releaseUrl": "https://github.com/Arkhins-0/wink/releases/tag/v0.0.0.1",
  "apkUrl": "https://github.com/Arkhins-0/wink/releases/download/v0.0.0.1/Wink-v0.0.0.1.apk",
  "notes": "## What changed\n- ..."
}
```

`apkUrl` may be null; the dialog then offers "Open release page" instead. The
simplest server implementation proxies GitHub's latest-release endpoint and
caches it for half an hour, exactly as the app's own fallback does.

## Releasing

1. Bump `winkVersionName` and `winkVersionCode` in `app/build.gradle.kts`
   (for example `0.0.0.1` and `2`) and commit.
2. Tag and push:

   ```bash
   git tag v0.0.0.1
   git push origin v0.0.0.1
   ```

3. `.github/workflows/release.yml` (at the repository root) builds both APKs, refuses to publish an
   unsigned release APK, writes the commit subjects since the last tag as the
   release notes, and attaches `Wink-v0.0.0.1.apk`, a debug APK and
   `SHA256SUMS.txt` to a GitHub Release. Installed apps see it on their next
   launch.

A manual run from the Actions tab (workflow_dispatch) builds and verifies
without publishing anything.

### Signing

An unsigned APK will not install, so `assembleRelease` looks for a keystore.
One was generated at `android/release.keystore.jks` (alias `wink`, password
`wink-release`, gitignored). **Back it up.** Every release must be signed with
the same key or Android refuses to install it over the previous version.

To use a different keystore, set `WINK_KEYSTORE_PATH`,
`WINK_KEYSTORE_PASSWORD`, `WINK_KEY_ALIAS` and `WINK_KEY_PASSWORD` in `.env`,
the environment, or as `wink.keystore.*` / `wink.key.*` Gradle properties.

The workflow signs with these repository secrets (Settings → Secrets and
variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.keystore.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | `wink-release` (or yours) |
| `ANDROID_KEY_ALIAS` | `wink` |
| `ANDROID_KEY_PASSWORD` | `wink-release` (or yours) |

Repository variables `WINK_BASE_URL`, `WINK_UPDATE_URL` and
`WINK_GITHUB_REPO` override what the built app points at; without them the
workflow uses the live server and the repository it runs in.

## Layout

```
android/app/src/main/java/com/arkhins/wink/
  Config.kt                     server address, update source, links
  WinkApplication.kt            process-wide objects: the update checker and installer
  MainActivity.kt               splash, edge-to-edge, the Compose root
  data/
    Models.kt                   AppVersionInfo, and GitHub's release shape for the fallback
    Versions.kt                 four-segment version comparison
    UpdateChecker.kt            asks the server, then GitHub, what the latest release is
    AppUpdater.kt               downloads the APK and hands it to the system installer
  ui/
    AppViewModel.kt             update state: found, dismissed, downloading, installing
    WinkApp.kt                  the root: HomeScreen plus the update dialog
    components/UpdateAvailableDialog.kt
    screens/HomeScreen.kt       the mark, the version, the check-for-updates card
    theme/Theme.kt              night palette with the CTR gold accent
android/app/src/main/res/
  drawable-nodpi/ctr_logo.png   the CTR mark used in the app
  mipmap-*/                     launcher icons generated from the same mark
```
