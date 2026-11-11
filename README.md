# Neli-Rose Retirement Home – Android App

> **Where Care is Always Near**

A resident-facing Android app for Neli-Rose Retirement Home. Residents can log in, track their health, follow their daily schedule, join activities, contact family and caregivers, and share their location in an emergency.

Built by the **Ambitious Developers** student group as part of the Neli-Rose Resident Management Information System (RMIS) project.

---

## Table of Contents

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
5. [Google Maps Setup](#google-maps-setup)
6. [Firebase Setup](#firebase-setup)
7. [Testing Push Notifications](#testing-push-notifications)
8. [How Data Is Stored](#how-data-is-stored)
9. [Security Notes](#security-notes)
10. [Troubleshooting](#troubleshooting)
11. [Team](#team)

---

## Features

### Authentication
- Email and password sign-up and login (Firebase Authentication)
- Google sign-in (Credential Manager)
- Forgot-password email reset
- Stays logged in between launches
- Profile (name, phone, email) saved to Firestore once the account has been created
- Nobody reaches the home screen without a successful Firebase sign-in, and a failed sign-up or password reset is reported as a failure

### Home
- Time-based greeting and today's date
- Medicines taken today and activities joined, counted from the saved records
- Vitals status for today (tap to jump to the Health tab)
- Next reminder, worked out from today's unfinished medicines and schedule events
- **Call Caregiver Now** calls the contact the resident chose as caregiver
- **Share My Location** asks the phone for a new position and sends it as a Google Maps link through any app
- **Profile** opens the profile and settings screen
- **View Map & Directions** opens the Google Maps screen

### Health
- Record blood pressure (mmHg), heart rate (bpm), temperature (°C) and weight (kg); readings are checked before they are saved and every saved reading is kept
- The resident's own medicines (add, edit, remove), marked as taken or not for each day
- Hydration tracker (glasses of water), counted per day
- Symptom logging with notes, date and time

### Schedule
- Choose the day to look at; every day has its own progress (for example "4 of 6 done") and earlier days are kept
- Events with tick-off, edit and delete
- Add new events with a date picker and a time picker, once or repeating every day

### Activities
- Filter by All, Joined or Available
- "Happening Today" and "Coming Up this Week" lists
- Request an activity with a preferred date. The request is stored in Firestore, "Request sent" only appears once the server has confirmed it, and "My requests" shows whether the care team has seen it
- Care team accounts get a list of all residents' requests (see [Firebase Setup](#firebase-setup))

### Contact
- Quick-call list of the resident's own contacts
- Add, edit and delete contacts
- Choose which contact is the caregiver; the caregiver buttons call that person
- Send quick SMS messages such as "I'm doing well" or "Please call me"
- My caregiver card with a one-tap call

### Profile and settings
- Change name and phone number (saved on the phone and in Firestore)
- Turn on notifications (Android 13+)
- Log out

### Google Maps
- Map showing Neli-Rose and the resident's live location
- Camera fits both points on screen
- **Directions to Neli-Rose** opens turn-by-turn navigation
- Share current location as a Maps link

### Push Notifications (Firebase Cloud Messaging)
- Each device's FCM token is saved to Firestore (`users/{uid}.fcmToken`)
- Every device subscribes to the `residents` topic for broadcast announcements
- Notifications show while the app is open or closed
- Android 13+ notification permission is requested from **Profile and settings**

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | XML layouts, Material Components (no Jetpack Compose) |
| Min / Target SDK | 26 / 37 |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.6.0, version catalog |
| Auth | Firebase Authentication, Credential Manager, Google ID |
| Remote data | Cloud Firestore |
| Local data | Room (SQLite) |
| Maps and location | Maps SDK for Android, Fused Location Provider |
| Messaging | Firebase Cloud Messaging |

---

## Project Structure

```
neli-rose-android-app/
├── app/
│   ├── google-services.json            # Firebase config
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/neli_roseapp/
│       │   ├── LoginActivity.kt        # Email and Google login, password reset
│       │   ├── SignUpActivity.kt       # Account creation, saves profile to Firestore
│       │   ├── MainActivity.kt         # Header, bottom navigation, FCM setup
│       │   ├── ProfileActivity.kt      # Profile and settings
│       │   ├── CareRequestsActivity.kt # Care team's list of activity requests
│       │   ├── HomeFragment.kt         # Dashboard, emergency actions
│       │   ├── HealthFragment.kt       # Vitals, meds, hydration, symptoms
│       │   ├── ScheduleFragment.kt     # Daily events
│       │   ├── ActivitiesFragment.kt   # Activities and requests
│       │   ├── ContactFragment.kt      # Calls, SMS, contacts
│       │   ├── MapActivity.kt          # Google Maps screen
│       │   ├── FcmHelper.kt            # Channel, token, topic, notifications
│       │   ├── NeliMessagingService.kt # Receives FCM messages
│       │   ├── CareData.kt             # Data models and DataManager
│       │   ├── CareLogic.kt            # Dates, vitals checks, next reminder (unit tested)
│       │   ├── LegacyPrefsImport.kt    # One-time copy of records from the old storage
│       │   ├── UiHelpers.kt            # Shared dialogs, pickers and formatting
│       │   └── db/                     # Room: entities, DAOs, database, migrations
│       └── res/                        # Layouts, drawables, colours, menu
├── .github/workflows/blank.yml         # Builds, tests and uploads the APK
├── firestore.rules                     # Firestore security rules (publish them in the console)
├── gradle/libs.versions.toml           # Dependency versions
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Getting Started

### Prerequisites
- **Android Studio** with support for AGP 9.4.1 (use the latest stable release)
- **JDK 17 or newer** (Android Studio's bundled JDK is fine)
- An Android device or emulator running **Android 8.0 (API 26)** or higher. For Google sign-in, Maps and notifications, use an image that includes **Google Play services**.

### Run the app
1. Clone the repository:
   ```bash
   git clone <your-repo-url>
   cd neli-rose-android-app
   ```
2. Open the folder in Android Studio.
3. Wait for Gradle sync to finish (**File > Sync Project with Gradle Files** if it doesn't start).
4. Select a device and press **Run**.

The app ID is `NeliRose.RetirementHome`, which matches the entry in `google-services.json`.

### Build in GitHub Actions
Every push to `main` and every pull request into `main` builds the debug APK and runs the unit tests (`.github/workflows/blank.yml`). Open the finished run under **Actions** and download `neli-rose-debug-apk` from **Artifacts**. An APK built there is signed with a throw-away debug key, so Google sign-in does not work in it unless that key's SHA-1 is added in Firebase.

---

## Google Maps Setup

The map screen needs a Maps API key. Without a working key, the map area loads blank.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and select the project linked to Firebase (`neli-rose-c96ef`).
2. Go to **APIs & Services > Library** and enable **Maps SDK for Android**.
3. Make sure a billing account is linked (Maps requires one, but the free monthly credit covers normal student use).
4. Under **APIs & Services > Credentials**, open your API key and restrict it:
   - **Application restrictions:** Android apps
   - Add package name `NeliRose.RetirementHome` and your SHA-1 fingerprint
   - **API restrictions:** allow Maps SDK for Android (plus the Firebase APIs if the key is shared with Firebase)
5. Put the key in `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_KEY_HERE" />
   ```

**Check the location.** In `MapActivity.kt`, `HOME` is set to 39 Rooihaaskoppie Avenue, East Lynne, Pretoria, using the position from a public directory listing. Confirm it against the building in Google Maps (press and hold on the building to see its latitude and longitude) and correct it if needed:

```kotlin
private val HOME = LatLng(-25.70374, 28.2741)
```

**Get your SHA-1 fingerprint:**
```bash
./gradlew signingReport
```
(On Windows use `gradlew.bat signingReport`.) Copy the SHA-1 from the `debug` variant.

---

## Firebase Setup

The project already includes a `google-services.json`. If you use your own Firebase project, follow these steps.

1. In the [Firebase console](https://console.firebase.google.com/), create a project and add an Android app with package name `NeliRose.RetirementHome`.
2. Add your debug **SHA-1** under **Project settings > Your apps** (needed for Google sign-in).
3. Download the new `google-services.json` and place it in the `app/` folder.
4. Enable the services:
   - **Authentication:** turn on **Email/Password** and **Google**
   - **Firestore Database:** create a database
   - **Cloud Messaging:** enabled by default
5. Publish the Firestore rules from [`firestore.rules`](firestore.rules): open **Firestore Database > Rules**, paste the file's contents and press **Publish**. They let each person read and change only their own profile and activity requests, and let care team accounts read all requests.
6. To make an account a care team account, open **Firestore Database > Data > users**, choose that person's document and add the field `role` with the value `caregiver`. That account then sees **Care team > See residents' activity requests** on its profile screen. The role cannot be set from the app.

If **Email/Password** is not turned on under **Authentication > Sign-in method**, the app reports that email sign-in is not switched on and does not let anyone in.

---

## Testing Push Notifications

1. Run the app on a device or emulator with Google Play services and log in. Allow notifications when asked.
2. In the Firebase console go to **Engage > Messaging > Create your first campaign > Firebase Notification messages**.
3. Enter a title and text, then choose **Send test message** (paste the device token from Logcat or `users/{uid}.fcmToken` in Firestore) or target the topic **`residents`** to reach every device.
4. Check both cases:
   - **App open:** `NeliMessagingService` shows the notification.
   - **App closed:** the system tray shows it using the bell icon and green colour set in the manifest.

FCM only receives messages. To send them automatically from your own logic (for example a reminder when a medication is due), you need a backend such as a Cloud Function that reads `fcmToken` from Firestore.

---

## How Data Is Stored

| Data | Where |
|---|---|
| User profile (name, phone, email) | Firestore `users/{uid}` and Room (`user_profiles`) |
| FCM token | Firestore `users/{uid}.fcmToken` |
| Vitals (every saved reading, with its date) | Room (`vital_readings`) |
| Hydration (one count per day) | Room (`hydration_days`) |
| Medicines, and the days each was taken | Room (`medications`, `medication_logs`) |
| Schedule events, and the days each was done | Room (`schedule_events`, `schedule_completions`) |
| Symptoms (with notes, date and time) | Room (`symptom_logs`) |
| Activities joined | Room (`activities`) |
| Contacts, including which one is the caregiver | Room (`contacts`) |
| Activity requests | Firestore `activityRequests` |

Room is the only place the health records are kept; SharedPreferences is no longer used for them. Local data is keyed by the Firebase user ID, so each account on a device sees only its own records, and nothing is stored when nobody is signed in. `DataManager` in `CareData.kt` is the single access point for all of this.

**Updating from an earlier version.** The database upgrade (version 2 to 3) is a migration that keeps the existing records, and anything that only existed in the old SharedPreferences store is copied into Room the first time each account is used. Old "taken" and "done" marks had no date, so they are kept as old records and are not counted as today's. Old medicines and schedule events become daily items starting on the day of the update.

**Still fixed in the app.** The "Happening Today" and "Coming Up this Week" activity lists are still written into the app, because there is no screen yet for staff to manage them.

---

## Security Notes

Read this before pushing the project to a public repository.

- **`google-services.json` is committed** in `app/`. Firebase client keys are not secrets in the strict sense, but they must be **restricted** (package name and SHA-1) so others cannot abuse your quota.
- **The Maps key is currently written directly in `AndroidManifest.xml`.** For a public repo, keep the key out of source control by loading it from `local.properties` (already in `.gitignore`) with a manifest placeholder:
  1. Add to `local.properties`:
     ```properties
     MAPS_API_KEY=your_key_here
     ```
  2. In `app/build.gradle.kts`, add `import java.util.Properties` at the very top and this inside `defaultConfig`:
     ```kotlin
     val localProps = Properties()
     val localFile = rootProject.file("local.properties")
     if (localFile.exists()) localFile.inputStream().use { localProps.load(it) }
     manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY", "")
     ```
  3. In the manifest, change the value to `android:value="${MAPS_API_KEY}"`.
- If a key was ever exposed publicly, **regenerate it** in Google Cloud Console and add restrictions to the new one.
- Never commit signing keystores or `local.properties`.
- Resident health data is sensitive. Before any real deployment, review Firestore rules and comply with POPIA (South Africa's data protection law).

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Map screen is blank or grey | Check the Maps API key, that **Maps SDK for Android** is enabled, billing is linked, and the package name and SHA-1 restriction match |
| "Email sign-in is not switched on for this app yet" | Turn on **Email/Password** under **Authentication > Sign-in method** in the Firebase console |
| "Your request could not be sent" on the Activities screen | Publish the rules from `firestore.rules` in the Firebase console |
| Google sign-in fails | Add your debug SHA-1 in Firebase, re-download `google-services.json`, and use an emulator image with Google Play |
| No notification appears | Allow notifications in app settings (Android 13+), use a device with Google Play services, and check Logcat for the FCM token |
| Token not saved to Firestore | Make sure you are logged in and your Firestore rules allow writes to `users/{uid}` |
| "Could not find your location" | Turn on GPS and grant location permission. On an emulator, set a location in **Extended controls > Location** |
| Gradle sync fails | Update Android Studio to a version that supports AGP 9.4.1 and use JDK 17 or newer |

---

## Team

**Ambitious Developers**

- Buchule
- Confidence Silinda
- Khorommbi Mudau
- Lesedi Lusenga
- Tumelo Mahlobogoane

*Client: Neli-Rose Retirement Home*

---

## License

This project was built for academic purposes. 
