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
     `emulator-5554`.
   For a bug fix the script must fail before the fix and pass after. A change is
   not "done" until both exist and pass.
6. Don't take screenshots without the user's permission (AI readback is slow).
   Assert from `uiautomator` dumps instead — and if a value can only be seen in
   a pixel, **make the app report it as text** (see Diagnostics) rather than
   screenshotting.
7. Never create GitHub issues unless explicitly asked.

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
"AMOLED" stays untranslated as a panel-technology proper noun, like `SMS` and
`PIN` do elsewhere.

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
