# AGENTS.md — Messages

Offline SMS messaging app for Android (Google Messages clone).
**Kotlin + Jetpack Compose + Material 3.** Package `com.anindra.messages`, min SDK 29. Default test emulators: `emulator-5558` (`Pixel_AOSP12_AOSP31`, Android 12) or `emulator-5557` (`Pixel_AOSP11_AOSP30`, Android 11) — both Magisk-rooted with microG and Google Messages installed.

## Essential commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest   # JUnit tests live in app/src/test/java/...
~/android/platform-tools/adb -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk   # or emulator-5557 (AOSP 11)
```

### Start the emulator (defaults: `emulator-5558` = AOSP 12, `emulator-5557` = AOSP 11)

Launch the **GUI** emulator (drop `-no-window` for headless; the regression
scripts need one of the two default AVDs booted), then wait for
`sys.boot_completed`:

```bash
~/android/emulator/emulator -avd Pixel_AOSP12_AOSP31 -port 5558 -no-audio -no-boot-anim \
    -gpu swiftshader_indirect -no-snapshot &
~/android/platform-tools/adb wait-for-device
until [ "$(~/android/platform-tools/adb -s emulator-5558 shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
```

Android 11 is the same recipe with `-avd Pixel_AOSP11_AOSP30 -port 5557` and
`adb -s emulator-5557`. `Pixel_7_AOSP_35` (`-port 5554`) still exists as the
historical 1080x2400 target.

Available AVDs (`~/android/emulator/emulator -list-avds`): `Pixel_AOSP12_AOSP31`
(5558), `Pixel_AOSP11_AOSP30` (5557) — the standing defaults, next section —
plus `Pixel_7_AOSP_35`, `Pixel_7_AOSP_36`, `AOSP_17`, `Pixel_7_G34`,
`Pixel_Android12`, `android31`. Stop one with
`~/android/platform-tools/adb -s emulator-5558 emu kill` (or `-s emulator-5557`).

### Standing comparison AVDs (Magisk + microG + Google Messages)

| Serial | AVD | Image | Screen |
|---|---|---|---|
| `emulator-5557` | `Pixel_AOSP11_AOSP30` | AOSP 11, API 30 | 1080x2400 @ 420dpi |
| `emulator-5558` | `Pixel_AOSP12_AOSP31` | AOSP 12, API 31 | 1080x2340 @ 440dpi |

Both are preloaded with **Magisk v25.2 root** (`su` → `u:r:magisk:s0`) via the
rootAVD ramdisk patch, **microG** (GmsCore 0.3.17 + FakeStore) and **Google
Messages 2026.x** installed. Google Messages holds the default-SMS role; the
regression suite takes it for `com.anindra.messages` at script start (`cmd role
add-role-holder … $PKG`) and can hand it back with
`su -c 'cmd role add-role-holder android.app.role.SMS com.google.android.apps.messaging'`.

Gotchas that cost real time on these AVDs:

- **Port arithmetic.** The `emulator-NNNN` serial follows the console (even)
  port; the raw adb endpoint is console+1 (odd) — A11 adb = 5558, A12 adb =
  5559. `adb connect 127.0.0.1:<p>` talks to *adb* ports while `adb -s` takes
  *serials*; mixing the two attaches a command to the wrong AVD (this misread
  A12 as A11 more than once).
- **AOSP-31 stock ramdisk ships `persist.sys.usb.config=none`** — adbd never
  starts and the device is permanently `offline`. The A31 ramdisk is repacked
  as gzip cpio with `=adb` (original lz4 kept at
  `~/Downloads/android-avd-build/ramdisk31-orig-lz4.img`).
- **`content insert` into `content://sms` silently no-ops on the AOSP-30
  image.** The only inbound-SMS seed channel that works everywhere is
  `adb emu sms send`.

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

## Compose previews

Previews live in **`app/src/debug/java/com/anindra/messages/ui/previews/`** (8
files, ~70 previews), not next to the code they render. `ui-tooling-preview` is
an `implementation` dependency, so a preview written into `src/main` compiles
into the **release** APK with only the renderer stripped. `PreviewPlacementTest`
holds that line; `PreviewCoverageTest` fails the build if any visual composable
in `src/main` has no preview referencing it.

- Use `@PreviewLightDark` (available at the current 1.8.3), not a hand-rolled
  `PreviewParameterProvider` for light/dark.
- Leave `MessagesTheme` on `mode = "system"`. It follows the preview's `uiMode`,
  which is what lets `@PreviewLightDark` switch the whole set. Passing an
  explicit mode makes the annotation a no-op.
- A **ViewModel-taking screen cannot be previewed.** 20 of them do. The fix is
  the state-hoisted overload the Compose docs describe (stateful wrapper +
  stateless composable taking plain values) — not a fake ViewModel, which drifts
  from the real one.
- To preview a `private` composable, widen it to `internal`. The debug source
  set is a different directory but the **same Gradle module**, so `internal` is
  the narrowest visibility that reaches it.

## Hard rules

1. M3 color roles only — no hex colors outside `Theme.kt` seeds + GM avatar palette.
2. No dead controls. 3. No comments unless genuinely non-obvious.
4. After code changes: build → install → run `scripts/test-*.sh` → track completion in `scripts/TODO.md`.
5. **Every change ships with BOTH tests — never miss this.** For any code change,
   not just bug fixes:
   - a **JUnit test file** under `app/src/test/java/...` covering the changed
     logic, runnable with `./gradlew testDebugUnitTest` (must stay green); and
   - a **`scripts/test-*.sh` regression script** the developer can re-run on
     `emulator-5558` (or `emulator-5557` for Android 11 coverage).
   For a bug fix the script must fail before the fix and pass after. A change is
   not "done" until both exist and pass.
6. Don't take screenshots without the user's permission (AI readback is slow).
   Assert from `uiautomator` dumps instead — and if a value can only be seen in
   a pixel, **make the app report it as text** (see Diagnostics) rather than
   screenshotting.
7. **No `Co-Authored-By` trailer in commit messages.** The harness appends one
   automatically when the message is passed to `git commit` inline; pipe it
   through a file instead so the message stays exactly as written:
   `git commit -F - <<'EOF' … EOF`.
8. Never create GitHub issues unless explicitly asked.
9. **Ask before adding a feature.** When a report or an idea implies new UI — a
   button, a screen, a setting, a gesture — check with the user first and
   describe what you would add. Do not grow the app's surface on your own
   initiative, even when the change looks obvious: #386's report was answered
   with a new clock button beside Send, which the maintainer had not asked for
   and immediately had removed (scheduling stays on hold-Send). Fixes to
   existing behaviour are fine; new affordances, settings and screens are not
   without a yes.

## Traps that have cost real time

**A regression script that passes on broken code is worse than no script.**
Break the fix, rebuild, **reinstall the APK**, then re-run. Gradle's build cache
and a stale install both make a script silently test the old build. Only after
seeing the expected `[FAIL]` does a `[PASS]` mean anything.

**Do not duplicate a decision in two places.** `DiagnosticsReport` once
re-implemented the theme-selection branch, so it reported `#000000` while the
theme painted `#131314` — and the regression script, which read Diagnostics,
agreed with it. Both were wrong together. Theme selection now lives once in
`Theme.kt` (`schemeFor()`, `themeIsDark()`) and Diagnostics calls it. Whenever
code that *reports* state re-derives what code that *renders* state decided, it
will eventually disagree.

**Several tests parse `src/main` source as text** (12 files do). They anchor on
declaration strings, so renaming a composable or changing its visibility breaks
them. When one fails on a rename, fix the anchor to be visibility-agnostic
(`Regex("(?:private|internal|public)?\\s*fun Name\\(")`) rather than reverting
the production change.

**Compose's `Color.red/green/blue` are `Float` in 0..1, not the packed ARGB
`Int`.** `"#%06X".format(color.red * 255)` throws
`IllegalFormatConversionException` — scale and round each channel first.

**Every new string needs all 13 translated locales** (`values-<lang>/`, not
`values-night`/`values-v31`, which are config qualifiers).
`TranslationParityTest` requires each of them to define every key in `values/`.
"Amoled" stays untranslated as a panel-technology proper noun, like `SMS` and
`PIN` do elsewhere. Its casing is mixed-case deliberately, so any script
grepping the picker row for `text="AMOLED"` has to match case-insensitively.

**Adding a new `when` key in `MainActivity`'s `AnimatedContent` can look like a
navigation route.** `NavTransitionDepthTest` scans for `"key" ->` branches and
will demand a `routeDepth` entry. Add the key to its `NON_ROUTE_WHEN_KEYS`
companion if it is a theme or intent mode, not a screen.

**The AVD is touchy** (see `scripts/AGENTS.md`): `uiautomator` segfaults
intermittently, and on a 1080x2400 screen the **last row in a settings list sits
under the bottom inset**, so a tap on its label's centre lands between rows.
Locate the enclosing `clickable="true"` node's bounds instead of the text node's.

## Diagnostics as an assertion surface

`Diagnostics → Advanced settings` reports device state as plain text, which makes
it assertable from a `uiautomator` dump with no screenshot. It now includes
`Resolved page colour: #RRGGBB` — the colour the current theme mode actually
resolves to. Prefer extending this sheet over any screenshot comparison: a
duplicated resolution path in the report is exactly the bug above, so it reads
the same functions the theme renders through.
