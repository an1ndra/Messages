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
5. Add a `test-*.sh` regression script for every bug fixed (dev must be able to re-run it).
6. Don't take screenshots without the user's permission (AI readback is slow).
7. Never create GitHub issues unless explicitly asked.