# AeroTracker

An Android app for model rocketry enthusiasts to log launches, capture launch-site data, and get an automatic post-flight readout on how each flight went. Every launch records its motor, location, altitude, and recovery condition, and AeroTracker analyzes that data on-device to flag issues like steep launch angles or underperforming altitude.

## Features

- **Launch logging** — record rocket name, motor type, launch angle, max altitude, flight duration, recovery condition, weather notes, and outcome for every flight.
- **GPS launch sites** — capture the launch location and elevation automatically from the device's location services.
- **Launch photos** — attach a photo of each launch straight from the camera.
- **On-device flight analysis** — a rule-based analyzer reviews each launch (angle, altitude vs. expected for the motor class, recovery condition, outcome) and returns a plain-language verdict: *Nominal*, *Minor Issues*, or *Needs Review*.
- **Launch history** — browse, edit, and delete past launches, synced to the cloud per account.
- **Accounts & sync** — email/password auth with per-user data stored in Firestore.
- **Onboarding flow** — a first-run walkthrough introducing the app.

## Tech stack

- **Language:** Java
- **Platform:** Android (minSdk 24, targetSdk 36)
- **Backend:** Firebase Authentication + Cloud Firestore
- **Location:** Google Play Services Location
- **UI:** Material Components, Navigation Component, ViewPager2, SwipeRefreshLayout, SplashScreen
- **Images:** Glide
- **Build:** Gradle (Kotlin DSL)

The launch-analysis logic lives in `aerotracker/app/src/main/java/com/example/aerotracker/analysis/` as pure Java with no Android dependencies, so it's covered by JUnit tests (`LaunchAnalyzerTest`).

## Setup

1. Clone the repo and open the `aerotracker/` directory in Android Studio.
2. Create a Firebase project and add an Android app with the package name `com.example.aerotracker`.
3. Enable **Email/Password** authentication and **Cloud Firestore** in the Firebase console.
4. Download your `google-services.json` and place it in `aerotracker/app/`.
5. Build and run on a device or emulator (API 24+).

```bash
cd aerotracker
./gradlew assembleDebug      # build a debug APK
./gradlew test               # run unit tests
```

## Screenshots

<!-- Add screenshots here -->
| Onboarding | Log a launch | Launch history | Flight analysis |
| --- | --- | --- | --- |
| _coming soon_ | _coming soon_ | _coming soon_ | _coming soon_ |
