# AGENTS.md — Messages (Google Messages clone)

## Project Overview

An **offline SMS messaging app for Android** that clones the UI of Google
Messages. Built with **Kotlin + Jetpack Compose + Material 3 (M3)**.
No internet permission — everything is local SMS + local database.

- Package: `com.anindra.messages`
- Min SDK 29 · Target/Compile SDK 35
- The emulator (`emulator-5554` or it may updated with different emulator) is the primary test device.

## Toolchain (IMPORTANT — exact paths)

| Tool | Path / command |
|---|---|
| JDK 21 (LTS) | `/home/anindra/tools/jdk21` (pinned via `gradle.properties` → `org.gradle.java.home`) |
| Gradle | `$HOME/tools/gradle-9.2.1/bin/gradle` (do NOT rely on system gradle) |
| Android SDK | `$HOME/android` (see `local.properties`) |
| adb | `$HOME/android/platform-tools/adb` |
| Emulator serial | `emulator-5554` |

### Build & run

```bash
cd ~/Develop/Messages
~/tools/gradle-9.2.1/bin/gradle assembleDebug --no-daemon
~/android/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use the helper scripts in `scripts/` (see below).

## Architecture

```
app/src/main/java/com/anindra/messages/
├── MessagesApplication.kt     # owns Repository singleton
├── MainActivity.kt            # nav routes: list/new/chat/settings,
│                              # single top-level BackHandler pops virtual
│                              # stack (details→chat, trash→settings, else→list);
│                              # NEVER override onBackPressed() — it bypasses
│                              # Compose BackHandlers (draft save, exit guard)
│                              # AppViewModel (AndroidViewModel), script deep links:
│                              #   --es set_theme dark|light|system
│                              #   --ez open_settings true (cold AND warm start)
├── data/
│   ├── Models.kt              # Conversation(pinned/draft/archived), Message(locked), BlockedNumber, ScheduledMessage
│   ├── SettingsStore.kt       # SharedPreferences: theme, notifications, sounds, delivery, SIM, feature toggles, privacyMode
│   ├── Repository.kt          # SQLiteOpenHelper (DB v10), Flow-based observers, backup/import, self-healing onOpen
│   └── DemoData.kt            # Seeds 10 conversations + avatars on fresh install for F-Droid screenshots
├── sms/
│   ├── SmsReceiver.kt         # SMS_RECEIVED broadcast → DB + notification
│   ├── SmsSupport.kt          # SmsSender.send (SmsManager) + NotificationHelper (with RemoteInput) + tones
│   ├── MmsReceiver.kt         # WAP_PUSH_DELIVER stub (for SMS app eligibility)
│   ├── ScheduledMessageSender.kt  # BroadcastReceiver: AlarmManager fires → send SMS + cleanup
│   ├── SmsStatusReceiver.kt   # sent/delivery status callbacks → updates message status in DB
│   ├── QuickReplyReceiver.kt  # handles notification inline reply action
│   └── NoConfirmationSmsSendService.kt  # RESPOND_VIA_MESSAGE stub
└── ui/
    ├── theme/Theme.kt         # FULL M3 color system: all roles seeded from Google Blue
    │                          #   #0B57D0 (light) / #A8C7FA (dark). Semantic aliases:
    │                          #   outgoingBubble=primaryContainer, incomingBubble=
    │                          #   surfaceContainerHighest, chatBar=surfaceContainerLow,
    │                          #   inputPill=surfaceContainerHigh
    ├── Components.kt          # PersonAvatar (GM palette + demo avatars), UnreadBadge, time formatters,
    │                          #   shimmer modifier, SkeletonConversationRow
    ├── ConversationsScreen.kt # home list + search + archive toggle + SwipeToDismissBox
    │                          #   + ModalBottomSheet context menu (Pin/Archive/Delete/Block)
    ├── ChatScreen.kt          # bubbles, 3-dot menu (SIM/Archive/Delete/Block), call icon,
    │                          #   OTP highlighting, message locking, forwarding, delayed sending,
    │                          #   scheduled messages (long-press send), linked text
    ├── OtpDetector.kt         # keyword-gated tiered OTP matcher (grouped/adjacent/bare-6),
    │                          #   currency + year guards; JUnit: app/src/test/.../OtpDetectorTest.kt
    ├── ContactDetailsScreen.kt # full-screen contact profile: avatar, call/add/search,
    │                          #   notifications toggle, block & report spam, participant list
    ├── NewChatScreen.kt       # contact picker + manual number entry
    ├── SettingsScreen.kt      # card-based sections: General/Connections/Appearance/Backup/Features/Privacy
    └── TrashScreen.kt         # trash view: restore/delete forever/empty trash
```

## Hard rules for any agent working here

1. **M3 color roles only.** Never hardcode hex colors in screens. Use
   `MaterialTheme.colorScheme.<role>` or the semantic aliases in `Theme.kt`.
   Exception: the GM avatar palette + `GoogleBlue` in `Components.kt`.
2. **No dead controls.** Every visible icon/button must do something real
   (user explicitly rejected decorative icons). If not implementable → remove.
3. **No comments** unless genuinely non-obvious (repo style).
4. **Kotlin/Compose conventions**: single-Activity, state hoisting, collectAsState.
5. After code changes: build (`assembleDebug`), install, verify on emulator via
   `scripts/*.sh`, and check screenshots before claiming done.
6. Do not take screenshots of app screen untill getting permission from user, taking screenshots and validating it by AI takes lot of time.
7. Always update the TODO if done then check mark the TODO dont miss it.
8. When testing in screen make sure after complete create a script of testing so developer can run it by them to test if whenever they want.
9. For multiple task alwasy sperate your task effeciently with other agents(create new agent), So implementation,bug fix become issue.
10. User enabled some developement features, take advantage of it.
11. NEVER create GitHub issues unless the user explicitly asks for it.
12. When the user asks to create an issue: describe ONLY the problem
    (symptoms, repro steps, root cause if known) — never include what you
    fixed/did. Post the resolution details as a COMMENT on the issue after
    creating it. Follow the templates in `.github/ISSUE_TEMPLATE/`.

## Test scripts (`scripts/`)

Run manually; each saves numbered PNGs to `screenshots/`.

```bash
scripts/install.sh            # build + install
scripts/open-app.sh           # cold launch + screenshot
scripts/send-message.sh [row] ["text"]      # open conv #row, type, IME-send
scripts/new-message.sh <num> ["text"]       # FAB → number → first message
scripts/receive-sms.sh [num] [text]         # adb emu sms send injection
scripts/settings.sh / theme.sh dark|light|system
scripts/reset-permissions.sh  # revoke → relaunch → REAL permission dialogs appear
scripts/grant-permissions.sh  # silent grant (for scripted runs)
scripts/run-all-tests.sh      # full sweep
scripts/take-fdroid-screenshots.sh   # 8 F-Droid screenshots (home/chat/settings/reply × dark/light)
scripts/test-back-nav.sh       # gesture back from chat to list
scripts/test-back-stack.sh     # full back stack: chat→list, profile→chat,
                               # trash→settings, exit guard, draft on button-back
scripts/test-chat-menu.sh      # chat 3-dot menu options
scripts/test-sim-menu.sh       # SIM selector in chat menu
scripts/test-message-lock.sh   # message lock/unlock with biometric
scripts/test-trash.sh          # trash system (delete/restore/purge)
scripts/test-notifications.sh  # notification delivery + per-conversation toggle
scripts/test-splash.sh         # splash screen dark/light mode
scripts/test-links-and-senders.sh  # link highlighting + alphanumeric sender block
scripts/test-initial-sync.sh   # first-launch progress bar + OTP duplicate check
```

Coordinate taps assume **1080x2400 @ 420dpi**. `env.sh` provides helpers
(`tap_text`, `center_of`, `shot`, `type_text`).

## Permissions (declared + runtime-requested at launch)

SEND_SMS, RECEIVE_SMS, READ_CONTACTS, POST_NOTIFICATIONS (API 33+).
Note: `pm clear` wipes grants → dialogs reappear (this is how you see them).

## Known environment facts

- The emulator ALSO has the real Google Messages app installed; it is the
  current default SMS handler and mirrors all traffic. Our app coexists.
- Emulator phone numbers: own = `+15551230004`-range; inject inbound SMS via
  `adb -s emulator-5554 emu sms send <number> "<text>"`.
- DB v10 recreates tables on upgrade (dev-mode). Seed data auto-inserts on
  first run only if table empty. Self-healing onOpen handles corrupted states.

## Reference screenshots of target UI

Real Google Messages runs on this emulator — use it as the visual spec:
home list (person avatars, unread count badges), chat (bubbles, "Save …?"
banner, "time • SMS" status line), Settings (card-based sections).
