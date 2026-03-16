# Geo Location App

Android app for geolocation, geofencing, and sharing nearby users on a map.

## Prerequisites
- Android Studio Flamingo+ (or recent) with Android SDK 36
- JDK 11

## Secrets (required)
Set these locally (not committed):
- `MAPBOX_ACCESS_TOKEN` — public Mapbox access token (app runtime)
- `MAPBOX_DOWNLOADS_TOKEN` — secret Mapbox downloads token (Gradle dependency auth)

You can set them via environment variables or `~/.gradle/gradle.properties` (local only):
```
MAPBOX_ACCESS_TOKEN=pk...
MAPBOX_DOWNLOADS_TOKEN=sk...
```

## Build & Run
1. Open the project in Android Studio.
2. Ensure the tokens are set (above).
3. Run Gradle sync, then build or run the app on a device/emulator.