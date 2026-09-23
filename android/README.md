# Wink for Android

The Android app, in `android/` of the Wink repository. The website and API it
talks to live one level up and deploy to `https://wink.arkhins.com`; see the
root README for what the app does.

Kotlin, Jetpack Compose, Material 3. Minimum Android 8.0 (API 26), targets
API 35. OkHttp + kotlinx.serialization for the API, Firebase Cloud Messaging
for push, CameraX + ML Kit for the QR scanner, ZXing for the account's own QR,
Coil for photos, Android's own `PdfRenderer` for reading PDFs.

What is inside:

- **Permission gate.** Notifications (Android 13+) and storage (Android 9 and
  older) are asked for before anything else; the app does not continue until
  both are allowed and asks again after a refusal.
- **Sign in, invite and reset links.** `https://wink.arkhins.com/invite/…`,
  `/reset/…` and `/v/…` open in the app when it is installed (App Links,
  verified against the site's `assetlinks.json`).
- **Onboarding** once: photo (from the photo picker, shrunk before upload),
  name, date of birth, contact number.
- **Home** (inbox and the next race), **Schedule** (admins edit session times
  here), **Race weekend** (sessions and the channel), **Chats**, **Compose**,
  **People** (create, edit, status, relay/bulk email), **Account** (QR, code,
  scanner, password, updates, sign out).
- **Popups.** Firebase delivers messages as heads-up notifications; in the
  foreground they also show as a card at the top of the screen. Tapping either
  opens the right chat, weekend or message.
- **Documents** are saved to `Downloads/Wink` the moment they are opened;
  PDFs read in the app, everything else opens with the app that handles it.
- **In-app updates**, unchanged: a newer GitHub Release is offered as a popup
  and installed from inside the app.

## Build

Requirements: JDK 21 and the Android SDK (platform 35). Android Studio's own
runtime is Java 25, which Gradle 8.13 does not support, so point `JAVA_HOME`
at a JDK 21 on the command line (or set it as Android Studio's Gradle JDK).
`local.properties` must point at the SDK:

```
sdk.dir=C:/Users/you/AppData/Local/Android/Sdk
```

`app/google-services.json` (Firebase console → project settings → Android app
`com.arkhins.wink`) must be present; it is gitignored, so download it once per
machine. The debug build keeps its `.debug` application-id suffix only once
that file also lists `com.arkhins.wink.debug` — add a second Android app with
that package in Firebase and re-download the file.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # onto a connected device or emulator
./gradlew assembleRelease        # signed if a keystore is configured, see below
```

## Configuration

Build-time values are looked up, in order, as an environment variable, the
same key in the repository's root `.env`, `local.properties`, then
`gradle.properties` / `-P`. They end up in `BuildConfig` via `Config.kt`.

| `.env` / environment  | Gradle property   | Default                                 | What it is |
|-----------------------|-------------------|-----------------------------------------|------------|
| `WINK_BASE_URL`       | `wink.baseUrl`    | `https://wink.arkhins.com`              | The Wink server. All API calls go through it. |
| `WINK_UPDATE_URL`     | `wink.updateUrl`  | `<baseUrl>/api/app-version`             | Reports the latest release. |
| `WINK_GITHUB_REPO`    | `wink.githubRepo` | `Arkhins-0/wink`                        | `owner/name` whose Releases carry the APKs. |

The session token lives in DataStore (`data/SessionStore.kt`) and is sent as a
bearer header by `data/Api.kt` — only to `WINK_BASE_URL`, never to the storage
bucket a document download redirects to.

## Release

Push a tag `vX.Y.Z.W` matching `winkVersionName` in `app/build.gradle.kts`.
`.github/workflows/release.yml` builds and signs the APKs and publishes the
GitHub Release. Repository secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and
`GOOGLE_SERVICES_JSON` (the raw contents of `app/google-services.json`; the
build fails without it).

The release certificate's SHA-256 is listed in the site's
`public/.well-known/assetlinks.json`; a new keystore means updating that file.

## Layout

```
app/src/main/java/com/arkhins/wink/
  Config.kt, WinkApplication.kt, MainActivity.kt
  data/   SessionStore, Api (+ApiModels), Documents, UpdateChecker, AppUpdater
  push/   Notifications (channel, heads-up), WinkMessagingService
  ui/     WinkApp (permission gate → auth → main navigation), AppViewModel,
          Links (deep links → routes), Format (times)
    components/  Common, Frame (top bar, countdown chip, popup, bottom nav),
                 MessageCard (+ document sheet), Composer, UpdateAvailableDialog
    screens/     Permission, Auth (login/forgot/set password), Onboarding, Home,
                 Schedule, Weekend, Chats, Compose, People, Account, Scanner, Pdf
```
