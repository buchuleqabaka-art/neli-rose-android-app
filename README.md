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
