# Messages

A simple, offline-first SMS messaging app for Android with a clean Material 3
interface. No ads, no analytics, no tracking — the app has **no INTERNET
permission** and all data stays on your device.

## Features

- SMS send/receive with per-message sent/delivered/failed status
- Conversations with pinning, drafts, archive, trash + undo
- Search, swipe actions, long-press context menu
- Scheduled messages, delayed sending, forwarding
- MMS images, contact avatars/photos, blocked numbers
- Dual-SIM selection, backup/restore database
- Light/dark/system themes (Material 3)

## Build

Requirements: JDK 17+, Android SDK 35.

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # unsigned unless release.keystore exists
```

The output is in `app/build/outputs/apk/`.

## Install to a connected device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Contributing

Bug reports and pull requests are welcome. Please keep changes consistent
with existing code style (Kotlin, Jetpack Compose, single-Activity).

## License

[GPL-3.0](LICENSE)
