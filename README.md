# Diabetes Companion - Sprint 3 Application

This package contains the Android application source and a debug APK for demonstration.

## Install the included APK

1. Extract the ZIP.
2. Copy `APK/DiabetesCompanion-Sprint3-Demo.apk` to an Android phone running Android 7.0 or newer.
3. Open the APK on the phone and allow installation from that source when Android asks.
4. Open Diabetes Companion and create an account or sign in with an authorized account.

For installation with Android platform tools, use:
```sh
adb install -r -t APK/DiabetesCompanion-Sprint3-Demo.apk
```

The APK connects to the existing Firebase project, `diabetes-companion-cace7`.
It contains no private OpenAI key. AI features need a key added to the source project and a new build; the app provides its available manual paths and configuration feedback.

## Open and build the source

This folder is the Android Studio project root; it contains `settings.gradle.kts`.

1. In Android Studio, choose **Open** and select this folder.
2. Let Gradle sync. Use Android Studio's bundled Gradle JDK and install Android SDK 36 if prompted.
3. The included `app/google-services.json` is the Firebase Android client configuration for this project. Keep it for the existing environment, or replace it with your own Firebase project's configuration for package `com.z.diabetescompanion`.
4. Copy `.env.example` to `.env`. Add your own OpenAI key:
   ```properties
   OPENAI_API_KEY=your_own_key
   OPENAI_MODEL=gpt-6-luna
   ```
5. Run the **app** configuration from Android Studio, or build with the included wrapper:
   ```sh
   ./gradlew :app:assembleDebug
   ```
   On Windows, use `gradlew.bat :app:assembleDebug`.

The new APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Rebuild whenever you change the OpenAI configuration. The project currently embeds that value in the APK, so distribute only builds using a key you are authorized to share.

If macOS/Linux reports that the wrapper is not executable, run `chmod +x gradlew` first.

## Firebase setup

For the existing environment, Firebase Authentication and Firestore are already configured.
For your own environment:

- Register the Android app and download its `google-services.json`.
- Enable Email/Password sign-in in Firebase Authentication.
- Create a Cloud Firestore database.
- Publish the included `firestore.rules` to that database.
- Deploy `firestore.indexes.json` as well. It adds collection-group scope to the single-field
  index on `alerts.recordedAt`, which the admin dashboard's cross-patient alert query needs.
  Without it that one query fails and the Alerts tab says so instead of showing an empty list.
  ```sh
  firebase deploy --only firestore:rules,firestore:indexes
  ```
  The index takes a few minutes to build before the query starts returning rows.
- The included `.firebaserc` selects the existing project. Change it before using Firebase CLI commands with another project.

### Creating the first administrator

The rules deliberately refuse to let an account register itself as an admin, so the first one
is promoted by hand: open Firestore in the Firebase console and set `role` to `admin` on that
user's document in `users`. After that, an existing administrator can change any other
account's role from the Users tab. An administrator cannot change their own role or suspend
themselves, which is what stops the platform being locked out.

No Firebase password, service-account credential, OpenAI key or test-account password is included.

## What is included

- `app/src/`: Kotlin application code, Android resources, unit tests and instrumentation test source.
- `app/build.gradle.kts`, root Gradle files and `gradle/`: build configuration and wrapper.
- `app/google-services.json`: Firebase Android client configuration.
- `firestore.rules`, `firestore.indexes.json`, `firebase.json` and `.firebaserc`: Firebase deployment configuration.
- `tools/check_rules.py`: checks the deployed Security Rules as real signed-in users, for the
  access-protection evidence. Takes the test accounts from environment variables; no password
  is stored in the repository.
- `docs/sprint3-usability-evaluation.md`: the US-21 task script and results template.
- `.env.example`: OpenAI configuration template.
- `APK/`: debug demo APK.
- `SHA256SUMS.txt`: file integrity checksums.

Build caches, local SDK paths, IDE settings, logs, private configuration, documentation reports and screenshots are excluded.

## Verification and current limits

The exported source built successfully with `:app:assembleDebug`; `:app:testDebugUnitTest` completed with 87 passed, 0 skipped and 0 failed. The APK signature was verified with Android SDK apksigner. The APK is a debug demonstration build, not a Play Store release.

Sprint 3 was additionally checked against the live Firebase project:

- The admin dashboard was driven on an emulator against real data: 38 accounts, 22 patients,
  10 active cases, 16 alerts. The overview figures, the account list and the alert list all
  agree because they are computed by one function, `domain/AdminMetrics.kt`, over one
  snapshot of the same records (US-19, "counts match records").
- Suspending and reactivating an account was exercised through the Users tab and over the
  Firestore REST API as three different signed-in users. Seventeen access-control checks pass
  against the deployed rules, including: a patient or doctor cannot read alerts across
  patients, a patient cannot make themselves an admin, a suspended account cannot lift its
  own suspension, and an administrator can change neither their own role nor their own status.
- Turning **Glucose alerts** off in Settings suppressed the local notification while the alert
  was still raised and recorded, which is the behaviour the setting describes.
- Suspending an account while it was signed in ended that session within seconds, and the
  account signed in again once an administrator reactivated it.
- Layouts were reviewed in light and dark themes at the default and largest text sizes.

This is an academic prototype using fictional health data. Known limits:

- **Suspension is enforced at the session, not on every write.** A suspended account is
  refused at sign-in, has any open session ended, and cannot lift its own suspension or
  change its own role. It can still write its own records through a direct API call, because
  enforcing suspension per write would mean reading the user document inside every rule.
- Scheduled timer/restart recovery after a device reboot has not been exercised end to end on
  a real device; the alarms are re-armed by `BootReceiver` but only the in-app path is tested.
- On a 360dp-wide screen at the largest text size, bottom-navigation labels truncate. Icons
  and the selected-tab indicator still identify each tab. This affects every role's shell,
  not only the admin one.
- US-21 needs participant sessions, which are a human activity. The task script and the
  results template for them are in `docs/sprint3-usability-evaluation.md`; the completion
  rates in it are not filled in.
