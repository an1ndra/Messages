# AGENTS.md — Messages

Offline SMS messaging app for Android (Google Messages clone).
**Kotlin + Jetpack Compose + Material 3.** Package `com.anindra.messages`, min SDK 29, emulator `emulator-5554`.

## Essential commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest   # JUnit tests live in app/src/test/java/...
~/android/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

### Start the emulator (`emulator-5554`)

Launch the **GUI** emulator (drop `-no-window` for headless; the regression
scripts need `emulator-5554` booted), then wait for `sys.boot_completed`:

```bash
~/android/emulator/emulator -avd Pixel_7_AOSP_35 -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -no-snapshot &
~/android/platform-tools/adb wait-for-device
until [ "$(~/android/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
```

Available AVDs (`~/android/emulator/emulator -list-avds`): `Pixel_7_AOSP_35`
(default, 1080x2400 @ 420dpi), `Pixel_7_AOSP_36`, `AOSP_17`, `Pixel_7_G34`,
`Pixel_Android12`, `android31`. Stop it with
`~/android/platform-tools/adb -s emulator-5554 emu kill`.

## Where docs live

- App dev guide: [docs/Development.md](docs/Development.md) / [docs/Developer.md](docs/Developer.md)
- Test scripts + agent rules + task tracker: **`scripts/` is a git submodule**
  (`git@github.com:an1ndra/Messages-scripts.git`) — read `scripts/AGENTS.md`,
  `scripts/Development.md`, `scripts/TODO.md`. If `scripts/` is empty:
  `git submodule update --init scripts`.

### Submodule branch tracking

`scripts/` tracks the **same-named branch** of Messages-scripts as the app repo
is on (Develop ↔ Develop, main ↔ main), set via `branch = Develop` in
`.gitmodules`. After creating/merging branches, point the submodule at the
matching branch:

```bash
git -C scripts fetch origin
git -C scripts checkout -B <branch> origin/<branch>   # same name as app branch
git -C scripts submodule update --remote scripts       # or: pull tip of that branch
git add scripts && git commit -m "chore: bump scripts submodule"
```

Publish script changes (run from app root or inside `scripts/`):

```bash
git -C scripts add -A && git -C scripts commit -m "..." && git -C scripts push
git add scripts && git commit -m "chore: bump scripts submodule"
```

## Hard rules

1. M3 color roles only — no hex colors outside `Theme.kt` seeds + GM avatar palette.
2. No dead controls. 3. No comments unless genuinely non-obvious.
4. After code changes: build → install → run `scripts/test-*.sh` → track completion in `scripts/TODO.md`.
5. **Every change ships with BOTH tests — never miss this.** For any code change,
   not just bug fixes:
   - a **JUnit test file** under `app/src/test/java/...` covering the changed
     logic, runnable with `./gradlew testDebugUnitTest` (must stay green); and
   - a **`scripts/test-*.sh` regression script** the developer can re-run on
     `emulator-5554`.
   For a bug fix the script must fail before the fix and pass after. A change is
   not "done" until both exist and pass.
6. Don't take screenshots without the user's permission (AI readback is slow).
7. Never create GitHub issues unless explicitly asked.