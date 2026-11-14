# Neli-Rose Retirement Home – Android App

> **Where Care is Always Near**

A resident-facing Android app for Neli-Rose Retirement Home. Residents can log in, track their health, follow their daily schedule, join activities, contact family and caregivers, and share their location in an emergency.

Built by the **Ambitious Developers** student group as part of the Neli-Rose Resident Management Information System (RMIS) project.

## Safety status — not ready for resident use

This is a prototype, not a medical or emergency service. Authentication now fails closed.
The medical schedule is unavailable until verified data or a working service is provided.
The home address and two general phone numbers were supplied by the user; they are not
confirmed as dedicated caregiver or emergency lines. Directions use the address as a search
query, so check the destination in the maps app. Calls open the dialer and location sharing
opens a share sheet; neither contacts anyone automatically. Activity requests can be shared
through another app, but are not delivered until the user chooses a recipient and sends them. Contact entries are supplied by
the user and should be verified. Earlier installations may have saved example schedule or
activity entries; review those manually before relying on them.

Vitals and symptoms are saved locally, not sent to a care team. Symptom history shows the ten
most recent entries and retains at most 100 on this device. Hydration is a manual counter,
not a clinical goal. Validation checks format only, not clinical
safety. Android backup is disabled, so data can be lost on uninstall or device loss. Local
storage is not application-level encrypted. Room mirrors some local data asynchronously;
SharedPreferences is currently the source used by the screens. Database migrations must be
implemented and tested before changing the schema; destructive migration has been disabled.

Before release, the project owner must configure and verify Firebase authentication, restrict
Firestore rules to the appropriate authorized users and test them with the Firebase emulator,
restrict the Maps API key by Android package/signing certificate and API, and verify the
address lookup and confirm the appropriate caregiver and emergency contact details. Broadcast FCM topics are not private and
must not contain personal health information. Review privacy, device security, accessibility,
clinical safety, consent and emergency workflows with qualified stakeholders. Test the app
on real devices, including offline and permission-denied cases. The checked-in APK and ZIP
are older artifacts and do not include these source changes.

Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` with a compatible JDK,
Android SDK and the required Gradle dependencies installed.

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
- Shortcut to enter vitals
- Reminder status (currently unavailable)
- Call the home using a supplied general contact number (opens the dialer)
- **Share My Location** sends a Google Maps link through any app
- **View Map & Directions** opens the Google Maps screen

### Health
- Record blood pressure, heart rate, temperature and weight
- Medical schedule is disabled until verified medication records are available
- Hydration tracker (glasses of water)
- Record symptoms locally; this does not notify the care team

### Schedule
- Day progress (for example "4 of 6 done")
- Today's events with tick-off
- Add new events

### Activities
- Filter by All, Joined or Available
- "Happening Today" and "Coming Up this Week" lists
- Share an activity request through another app

### Contact
- Quick-call list of family, doctor and nurse
- Add new contacts
- Send quick SMS messages such as "I'm doing well" or "Please call me"
- Call either of the supplied home contact numbers

### Settings
- Open Settings using the gear in the top header; logout is now inside Settings.
- Edit the name and phone displayed on this device. These changes are not synced to the care team.
- Open Android display and notification settings, request an email/password reset, and review locally saved vitals.
- Care-team announcements and API integration are not connected yet.

### Google Maps
- Map showing the resident's location when permission is granted
- Directions search by the supplied address; confirm the destination in the maps app
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

## How Data Is Stored

| Data | Where |
|---|---|
| User profile (name, phone, email) | Firestore `users/{uid}` and Room |
| FCM token | Firestore `users/{uid}.fcmToken` |
| Vitals, hydration, medications | SharedPreferences, with an asynchronous Room mirror |
| Schedule events | SharedPreferences, with an asynchronous Room mirror |
| Activities | SharedPreferences, with an asynchronous Room mirror |
| Contacts | SharedPreferences, with an asynchronous Room mirror |
| Dashboard counters | SharedPreferences |




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
