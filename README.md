# Diabetes Companion

Diabetes Companion is an Android app for day-to-day diabetes management. Patients log glucose readings and medication, get reminders and alerts, and stay in touch with their doctor and caregiver from one place. Doctors set each patient's thresholds and review their records, caregivers follow the patients who have given them access, and administrators manage accounts across the platform.

The app was built over three sprints as our software engineering project for Fall 2026. Version 1.0 is the final release.

## Features

### Patient
- Log glucose readings manually, or generate a demo reading for testing
- Glucose history with charts, date filters and trend indicators
- Medication and insulin log with scheduled reminders
- Warning and Critical alerts when a reading falls outside the thresholds set by the doctor, with "I'm OK", "Treating" and "Need Help" responses
- General-guidance chatbot that estimates the carbohydrates in a meal. Once the patient confirms the total, the app calculates the meal dose from the insulin-to-carb ratio confirmed by their doctor (rounded down to 0.5 units, with the working shown). The chatbot itself never gives an insulin amount.
- Insulin-advice requests sent to the assigned doctor
- Messaging with the doctor
- Weekly summaries
- Consent-based sharing with a doctor and caregiver, which the patient can revoke at any time

### Doctor
- List of assigned patients with their glucose and medication history
- Target and critical thresholds for each patient
- Insulin-advice requests with an AI-prepared draft that the doctor edits or replaces before sending. The patient never sees the draft.
- Alert follow-up and resolution notes
- Messaging with patients and caregivers

### Caregiver
- Dashboard for the patients who shared their records
- Escalated alerts and messaging with the doctor

### Administrator
- Overview of accounts, patients, active cases and alerts
- User management: change roles, suspend and reactivate accounts
- Cross-patient alert list and analytics

### Alert escalation
An unanswered Warning goes to the caregiver after five minutes, and to the doctor five minutes after that if it is still unresolved. Critical readings and "Need Help" responses skip the wait. An alert is only closed once someone records a resolution note.

## Tech stack

| Area | Used |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM with repositories and a separate domain layer |
| Backend | Firebase Authentication, Cloud Firestore with Security Rules |
| Local storage | DataStore Preferences |
| Background work | AlarmManager reminders and alert escalation, restored after reboot |
| AI | OpenAI API over OkHttp (chatbot, carb estimates, doctor drafts) |
| Testing | JUnit unit tests, AndroidX instrumented tests |

Minimum Android version is 7.0 (API 24). The app targets API 36.

## Project structure

```
app/src/main/java/com/z/diabetescompanion/
├── data/        Firebase repositories, AI client, alerts, reminders, preferences
├── domain/      Glucose logic, alert rules, insulin calculator, weekly summary, admin metrics
├── di/          App container
└── ui/          Compose screens grouped by role (patient, doctor, caregiver, admin)
firestore.rules           Firestore Security Rules
firestore.indexes.json    Firestore indexes
```

## Getting started

### Requirements
- Android Studio with JDK 17 or later
- Android SDK 36
- A Firebase project, if you are not using ours

### Build
1. Clone the repository and open it in Android Studio.
2. Copy `.env.example` to `.env` and add your OpenAI key:
   ```properties
   OPENAI_API_KEY=your_key
   OPENAI_MODEL=gpt-6-luna
   ```
   The app still runs without a key, but the AI features show a configuration message instead.
3. Let Gradle sync, then run the **app** configuration, or build from the command line:
   ```sh
   ./gradlew :app:assembleDebug
   ```

### Using your own Firebase project
1. Register an Android app with package name `com.z.diabetescompanion` and replace `app/google-services.json` with your own.
2. Enable Email/Password sign-in and create a Cloud Firestore database.
3. Point `.firebaserc` at your project and deploy the rules and indexes:
   ```sh
   firebase deploy --only firestore:rules,firestore:indexes
   ```
4. Create the first administrator by hand. Sign up normally, then set `role` to `admin` on that user's document in the `users` collection. The rules do not allow anyone to make themselves an admin from the app. From then on, admins can manage roles from the Users tab.

## Testing

```sh
./gradlew :app:testDebugUnitTest
```

The unit tests cover glucose logic, alert rules, the insulin calculator and dose guard, carb-estimate parsing, reminder scheduling, weekly summaries, admin metrics and input validation. The final build passed all 87 tests. Instrumented tests for the repositories, preferences and AI integration are in `app/src/androidTest`.

We also checked the Firestore Security Rules against the deployed project using real signed-in accounts. Those checks confirmed that users cannot read other patients' data, cannot raise their own role, and cannot lift their own suspension.

## Sprint history

Each sprint has its own branch and was merged into `main` through a pull request.

| Sprint | Branch | Delivered |
|---|---|---|
| 1 | `sprint-1` | Accounts and profiles, consent, glucose logging and history, doctor thresholds, medication log and reminders, patient dashboard |
| 2 | `sprint-2` | Alerts and timed escalation, caregiver dashboard, care-team messaging, chatbot and meal insulin calculator, insulin-advice requests with doctor-reviewed AI drafts, weekly summaries |
| 3 | `sprint-3` | Admin interface, settings and privacy, usability fixes and final checks |

## Known limitations

- Account suspension is enforced at sign-in and on active sessions. It is not checked on every individual write.
- Reminder recovery after a device reboot is handled, but it has not been tested end to end on a physical device.
- On narrow screens at the largest text size, the bottom navigation labels get cut off. The icons still identify each tab.

## Disclaimer

Diabetes Companion is an academic prototype. It was developed and tested with fictional health data. It is not a medical device and should not be used for real treatment decisions.

## Team

- Aljohara Alnasser
- Shahad Bin Sainid
- Norah Alsheikh
- Norah Aldeeb
- Reema Alhammad

Supervised by Dr. Asma Sattar.
