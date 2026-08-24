# Development.md — Development Reference

How the app is built, how navigation works, and how to verify changes.
For environment setup see [Developer.md](Developer.md); for agent rules see
[AGENTS.md](AGENTS.md); for task status see [TODO.md](TODO.md).

---

## 1. Quick reference

| Action | Command |
|---|---|
| Build | `~/tools/gradle-9.2.1/bin/gradle assembleDebug --no-daemon` |
| Install | `~/android/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` |
| Launch | `scripts/open-app.sh` |
| Full test sweep | `scripts/run-all-tests.sh` |
| Back-stack tests | `bash scripts/test-back-stack.sh` |

Test scripts source `scripts/env.sh` (tap/type/dump helpers). They verify state
via **uiautomator dumps**, not screenshots (screenshot+AI review is slow; only
take screenshots when explicitly asked).

---

## 2. Navigation & the back stack

### Model

Single Activity (`MainActivity`, extends `FragmentActivity`). No
Navigation-Compose dependency. A single `navRoute: String` state drives which
screen composes:

```
list ──> new      (FAB)
list ──> chat     (open conversation)   chatId
chat ──> details  (contact profile)     detailsId
list ──> settings                       settings ──> trash
```

### The ONE rule of back handling

**All system back events must go through `OnBackPressedDispatcher`.**

- There is exactly one top-level `BackHandler` in `MainActivity.setContent`,
  enabled whenever `navRoute != "list"`. It pops a virtual stack:

  | current route | back goes to |
  |---|---|
  | `details` | `chat` |
  | `trash` | `settings` |
  | `chat`, `new`, `settings` | `list` |
  | `list` | (disabled → screen's own double-back-to-exit guard runs) |

- Screens keep their own higher-priority `BackHandler`s where they own
  side effects. Dispatcher priority = **last registered wins**; children compose
  after the parent handler, so they win while composed:
  - `ChatScreen.leaveChat` — saves/clears the draft before popping
  - `ConversationsScreen` — clears search / archive view first;
    double-back-to-exit with toast on root

### Why `onBackPressed()` is banned here

The previous implementation overrode `Activity.onBackPressed()`. That override:

1. **Bypasses every Compose `BackHandler`** — button-back from chat skipped
   `leaveChat()` and silently dropped drafts.
2. Mapped `"details" -> "list"` directly, so back/gesture from the contact
   profile skipped the chat and jumped to the list.
3. Diverges from dispatcher-based handling, so gesture vs button vs predictive
   back could behave differently per device.

Never re-introduce it. If you need route-aware back behavior, extend the
top-level `BackHandler`'s pop logic instead.

### Predictive back note

Manifest keeps `android:enableOnBackInvokedCallback="false"`. If it is ever
flipped to `true` (or an OEM/dev option forces predictive dispatch), all paths
already route through the dispatcher, so behavior is unchanged.

---

## 3. Intent script hooks

Used by test scripts; honored on cold start **and** warm start (`onNewIntent`):

```bash
adb shell am start -n com.anindra.messages/.MainActivity --es set_theme dark|light|system
adb shell am start -n com.anindra.messages/.MainActivity --ez open_settings true
```

Gotchas learned the hard way:

- `am start` extras are only read in `onCreate`/`onNewIntent`; a warm start
  without force-stop delivers the intent but previously ignored extras.
- Settings rows may be below the fold — scroll before tapping in tests.
- With the IME open, the first BACK closes the keyboard (correct Android UX);
  scripts must send BACK twice.

---

## 4. Data layer snapshot

- `Repository.kt` — SQLiteOpenHelper, currently **DB v10** (dev-mode: upgrades
  recreate tables). Self-healing `onOpen` repairs missing columns/tables.
- Flows observed via `AppViewModel` (AndroidViewModel), collected with
  `collectAsState`.
- `SettingsStore.kt` — SharedPreferences (theme, SIM, toggles, privacy).
- Fresh install seeds 10 demo conversations (`DemoData.kt`) when tables are
  empty.

## 5. Conventions (enforced)

1. M3 color roles only — no hex colors outside `Theme.kt` seeds and the GM
   avatar palette in `Components.kt`.
2. No dead controls — every visible icon must do something real.
3. No comments unless genuinely non-obvious.
4. After any change: build → install → run relevant `scripts/test-*.sh` →
   update TODO.md checkboxes.

## 6. Regression guardrails

After any task: run the affected test scripts plus `test-back-nav.sh` and
`test-back-stack.sh` when navigation or screens changed; ensure the build is
green; confirm no new permissions beyond those listed in AGENTS.md.
