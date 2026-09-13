# AGENTS.md — Messages

Offline SMS messaging app for Android (Google Messages clone).
**Kotlin + Jetpack Compose + Material 3.** Package `com.anindra.messages`, min SDK 29, emulator `emulator-5554`.

## Essential commands

```bash
./gradlew assembleDebug
~/android/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Where docs live

- App dev guide: [Development.md](Development.md) / [Developer.md](Developer.md)
- Test scripts + agent rules + task tracker: **`scripts/` is a git submodule**
  (`git@github.com:an1ndra/Messages-scripts.git`) — read `scripts/AGENTS.md`,
  `scripts/Development.md`, `scripts/TODO.md`. If `scripts/` is empty:
  `git submodule update --init scripts`.

## Hard rules

1. M3 color roles only — no hex colors outside `Theme.kt` seeds + GM avatar palette.
2. No dead controls. 3. No comments unless genuinely non-obvious.
4. After code changes: build → install → run `scripts/test-*.sh` → track completion in `scripts/TODO.md`.
5. Add a `test-*.sh` regression script for every bug fixed (dev must be able to re-run it).
6. Don't take screenshots without the user's permission (AI readback is slow).
7. Never create GitHub issues unless explicitly asked.