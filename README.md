# Diabetes Companion

Kotlin + Jetpack Compose Android app for the SE 499 Diabetes Companion project.
This build covers **Sprint 1: Core Records and Secure Foundation** from the project charter,
following the Figma design brief's structure (welcome + disclaimer, role-specific bottom
navigation, demo role switcher, patient records, doctor thresholds, consent controls).

## Stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, Material 3, brand theme from the logo (blue primary, red accent; light + dark), text-size setting |
| Navigation | Navigation Compose: `auth` graph, then one bottom-nav shell per role |
| State | `ViewModel` + `StateFlow`, unidirectional data flow |
| Auth | Firebase Authentication (email/password) |
| Data | Cloud Firestore with live snapshot flows; rules in `firestore.rules` |
| Reminders | AlarmManager + BroadcastReceiver, restored after reboot |
| Prefs | DataStore (text size, notifications, demo role) |
| DI | Hand-rolled `AppContainer` |
| Min SDK | 24 |

## Firebase setup

1. Firebase console > add Android app `com.z.diabetescompanion`, download `google-services.json` into `app/`.
2. **Authentication > Sign-in method**: enable Email/Password.
3. **Firestore Database > Create database** (production mode, any region).
4. **Firestore > Rules**: paste `firestore.rules` and publish. The rules enforce the charter's
   role and consent model server-side (NFR-07): patients own their records, linked doctors and
   caregivers read them, only a linked doctor can write thresholds, only the patient can grant
   or revoke a link.

The Google Services plugin is applied only when `google-services.json` exists, so the project
compiles without it; auth screens then show a "not configured" banner.

## Sprint 1 coverage

| Story | Where |
|---|---|
| S1-01 Accounts and profiles | Welcome, Login (role selector, verified against the account), Sign up (role picker), Complete profile, Profile |
| S1-02 Consent and data controls | More > Care-team sharing (grant/revoke), Privacy (deletion request, retention) |
| S1-03 Glucose input | Add glucose (manual + unit + time, validation, Generate demo reading, Saved confirmation) |
| S1-04 Glucose history and trends | History tab (chart with target band, range filters, trend, "Trend unavailable") |
| S1-05 Doctor review and thresholds | Doctor > Patients (consented only, search, status), Patient detail, Thresholds |
| S1-06 Medication history | Medications tab > History, Log a dose |
| S1-07 Medication reminders | Medications tab > Reminders (time, days, enable), notifications + permission handling |
| S1-08 Patient dashboard | Home: latest reading + status + trend, 7-day chart, upcoming reminders, quick actions |
| S1-09 Accessible and protected use | Text-size setting, icon + text status, prototype notices, rules, encryption info |

Sprint 2 and 3 entry points (Messages, Alerts, Requests, chatbot, insulin request, weekly
summaries, caregiver and admin screens) exist as labelled placeholders so navigation is complete.

## Firestore data model

```
users/{uid}                       role, displayName, email, age, diabetesType, relevantHistory, carePlan
users/{uid}/readings/{id}         valueMgdl, enteredValue, enteredUnit, source, recordedAt, note
users/{uid}/medications/{id}      name, dose, unit, kind, takenAt, note
users/{uid}/reminders/{id}        medicationName, hour, minute, days[], enabled
users/{uid}/settings/thresholds   targetLow, targetHigh, criticalLow, criticalHigh, updatedBy*
careLinks/{patientUid_memberUid}  patientUid, memberUid, memberRole, status, grantedAt, revokedAt
deletionRequests/{uid}            uid, requestedAt, status
```

## Project layout

```
app/src/main/java/com/z/diabetescompanion/
├── DiabetesCompanionApp.kt, MainActivity.kt
├── data/
│   ├── auth/         AuthRepository, FirebaseAuthRepository, AccountService (register + profile)
│   ├── model/        Domain models (UserProfile, GlucoseReading, MedicationLog, Reminder, ...)
│   ├── repository/   Repository interfaces
│   ├── firestore/    Firestore implementations, mappers, snapshot flows, error copy
│   ├── prefs/        DataStore preferences
│   └── reminders/    ReminderScheduler, ReminderReceiver, BootReceiver
├── domain/           GlucoseStatus, TrendCalculator, validation, demo generator
├── di/AppContainer.kt
└── ui/
    ├── App.kt, Session.kt, AppViewModelProvider.kt
    ├── navigation/   AppNavHost, RootViewModel (session state machine)
    ├── welcome/, auth/
    ├── shell/        RoleShell (bottom nav), PatientShell, DoctorShell, CaregiverShell, AdminShell, MoreScreen
    ├── patient/      home, glucose, medications, profile, sharing
    ├── doctor/       patients (list, detail, thresholds)
    ├── settings/     SettingsScreen, PrivacyScreen
    ├── components/   Buttons, text fields, pickers, chart, status chips, states
    └── theme/
```

## Tests

`./gradlew :app:testDebugUnitTest` runs unit tests for validation, status/trend logic,
demo generation and reminder scheduling.

## Next (Sprint 2)

Alerts and escalation, caregiver dashboard, messaging, chatbot, insulin-advice requests with
doctor-only AI drafts, weekly summaries.
