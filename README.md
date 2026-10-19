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
- Profile (name, phone, email) saved to Firestore on sign-up

### Home
- Time-based greeting and today's date
- Medication and activity counters
- Vitals status badge (tap to jump to the Health tab)
- Next reminder countdown
- **Call Caregiver Now** opens the dialer
- **Share My Location** sends a Google Maps link through any app
- **View Map & Directions** opens the Google Maps screen

### Health
- Record blood pressure, heart rate, temperature and weight
- Medical schedule with Taken / Pending status
- Hydration tracker (glasses of water)
- Symptom logging

### Schedule
- Day progress (for example "4 of 6 done")
- Today's events with tick-off
- Add new events

### Activities
- Filter by All, Joined or Available
- "Happening Today" and "Coming Up this Week" lists
- Request an activity with a preferred date

### Contact
- Quick-call list of family, doctor and nurse
- Add new contacts
- Send quick SMS messages such as "I'm doing well" or "Please call me"
- Caregiver on duty card with a one-tap call

### Google Maps
- Map showing Neli-Rose and the resident's live location
- Camera fits both points on screen
- **Directions to Neli-Rose** opens turn-by-turn navigation
- Share current location as a Maps link

### Push Notifications (Firebase Cloud Messaging)
- Each device's FCM token is saved to Firestore (`users/{uid}.fcmToken`)
- Every device subscribes to the `residents` topic for broadcast announcements
- Notifications show while the app is open or closed
- Android 13+ notification permission is requested at launch

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
| Local data | Room (SQLite) and SharedPreferences |
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
│       │   ├── HomeFragment.kt         # Dashboard, emergency actions
│       │   ├── HealthFragment.kt       # Vitals, meds, hydration, symptoms
│       │   ├── ScheduleFragment.kt     # Daily events
│       │   ├── ActivitiesFragment.kt   # Activities and requests
│       │   ├── ContactFragment.kt      # Calls, SMS, contacts
│       │   ├── MapActivity.kt          # Google Maps screen
│       │   ├── FcmHelper.kt            # Channel, token, topic, notifications
│       │   ├── NeliMessagingService.kt # Receives FCM messages
│       │   ├── CareData.kt             # Data models and DataManager
│       │   └── db/                     # Room: entities, DAOs, database
│       └── res/                        # Layouts, drawables, colours, menu
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

**Set the real location.** In `MapActivity.kt`, `HOME` is a placeholder (Johannesburg). Replace it with Neli-Rose's real coordinates (right-click the building in Google Maps and copy the latitude and longitude):

```kotlin
private val HOME = LatLng(-26.2041, 28.0473)
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
5. Set Firestore rules so each user can only access their own document:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

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
| User profile (name, phone, email) | Firestore `users/{uid}` and Room |
| FCM token | Firestore `users/{uid}.fcmToken` |
| Vitals, hydration, medications | Room (`vitals`, `medications`) |
| Schedule events | Room (`schedule_events`) |
| Activities | Room (`activities`) |
| Contacts | Room (`contacts`) |
| Dashboard counters | SharedPreferences |

Local data is keyed by the Firebase user ID, so each account on a device sees only its own records. `DataManager` in `CareData.kt` is the single access point for all of this.

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
