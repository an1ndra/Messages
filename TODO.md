# TODO.md — Pending work for Messages app

Hand this file + AGENTS.md (same folder) to any AI agent. Tasks are ordered by
priority; each has acceptance criteria and file pointers. Verify on
`emulator-5554` with `scripts/*.sh` before marking done.

---

## Completed (DO NOT re-implement)

### Core Architecture
- ✅ M3 full color system (light/dark) from seed #0B57D0
- ✅ Data layer: SQLiteOpenHelper v5, Flow-based Repository, SettingsStore
- ✅ SMS: SmsSender (multi-SIM), SmsReceiver, MmsReceiver, NoConfirmationSmsSendService
- ✅ NotificationHelper with tones

### Screens
- ✅ ConversationsScreen: search w/ autofocus, avatars (custom `ic_person_placeholder.xml`), unread badges, Start chat FAB, archive icon toggle
- ✅ ChatScreen: aligned input bar, send button visibility, call icon, save-contact banner, date dividers, status line, draft loading/saving, message forwarding, blocked number check dialog, delayed sending, Block/Unblock in 3-dot menu
- ✅ SettingsScreen: card-based sections with icons, proper visual hierarchy
- ✅ NewChatScreen: contact picker + manual number entry

### Features
- ✅ MMS image sending: attachment button, ModalBottomSheet, PickVisualMedia + TakePicture, ImageBubble
- ✅ Failed message UX: red "Not sent · Tap to retry", retryMessage
- ✅ SIM card selection: Settings row, permission-gated dialog, SubscriptionManager
- ✅ SIM switcher icon in input pill (top-right of "Text message" field, dual-SIM "1/2" icon `ic_dual_sim.xml`, tap = cycle SIMs, toast feedback, hidden while typing) — test: `scripts/test-sim-inputbar.sh`
- ✅ Chat header: contact name or formatted number
- ✅ Adaptive launcher icon
- ✅ Custom vector drawable `ic_person_placeholder.xml` (Wikimedia reference)
- ✅ Avatar colors: Pink #FF63B8, Coral Red #EE675C, Orange #FA903E, Cyan #4ECDE6, Purple #AF5CF7
- ✅ Profile icon: pink background with white person silhouette
- ✅ Default SMS app prompt: AlertDialog on first launch, RoleManager.ROLE_SMS
- ✅ Backup/Import: backupDatabase() + importDatabase() via MediaStore/SAF

### QKSms-Inspired Features
- ✅ Long-press context menu: QKSms-style ModalBottomSheet with Pin/Unpin, Archive, Delete, Block
- ✅ Pinned conversations (DB column + toggle in settings + visual indicator)
- ✅ Drafts (auto-save, restore on open, visual indicator)
- ✅ Archiving (DB column + toggle in settings + archive view)
- ✅ Swipe actions (SwipeToDismissBox: swipe-right=archive, swipe-left=delete)
- ✅ Number blocking (blocked_numbers table + check on incoming SMS)
- ✅ Message forwarding (long-press → ForwardPicker)
- ✅ Delayed sending (configurable delay countdown + cancel)
- ✅ DB v5: pinned/draft columns, blocked_numbers, scheduled_messages tables
- ✅ Scheduled messages (long-press send → DatePickerDialog + TimePicker → AlarmManager)
- ✅ Settings Features section with all toggles organized by category

### Recent Updates
- ✅ Real SMS sync: seed data removed; syncFromSystem() reads Telephony.Sms.CONTENT_URI (dedupe by sys_id), runs on app start + resume
- ✅ DB v6: `sys_id` column on messages
- ✅ Write-backs: sent → system Sent box; received → system Inbox (when default SMS app)
- ✅ READ_SMS permission (manifest + runtime request)
- ✅ Default SMS role check: RoleManager.isRoleHeld(ROLE_SMS); emulator fix: `adb shell cmd role add-role-holder android.app.role.SMS com.anindra.messages`
- ✅ Contact names refresh on resume (refreshContactNames + NORMALIZED_NUMBER matching)
- ✅ 3-button nav fix: navigationBarsPadding on chat bottom bar
- ✅ Save-contact banner: floating overlay (78% opacity), phone-number-only guard, no layout push
- ✅ Release signing: release.keystore + Messages-release.apk
- ✅ Per-SIM switcher icon: `ic_sim_1.xml`/`ic_sim_2.xml` show the SELECTED SIM's card+number in the input pill (top-right of field); hidden while typing; tap = cycle SIMs — test: `scripts/test-sim-inputbar.sh`
- ✅ Block sends to alphanumeric sender IDs (DK-AIRCEL…): chat send/schedule guarded with dialog; NewChat manual entry restricted to phone numbers — test: `scripts/test-links-and-senders.sh`
- ✅ Highlight links in messages: URLs become tappable (blue underline) opening the browser; Settings → Messages → "Highlight links" toggle (default on) — test: `scripts/test-links-and-senders.sh`
- ✅ Trash system: swipe-left / sheet Delete moves conversations to trash (DB v8 `deleted_at`), UNDO snackbar, Settings → Privacy → Trash screen (restore / delete forever / empty trash), auto-purge after 30 days on app start, new SMS from trashed address restores the thread; swipe needs ~65% travel (less sensitive) — test: `scripts/test-trash.sh`
- ✅ Per-conversation notification settings: DB v9 `conversation_notifications` table (ON DELETE CASCADE), notification toggle in ContactDetailsScreen + ChatScreen 3-dot menu, NotificationHelper.show() checks per-conversation setting before posting — test: `scripts/test-notifications.sh`
- ✅ Mark all as read: Settings → General row
- ✅ Real send confirmation: SmsStatusReceiver + sent/delivery PendingIntents flip rows sending→sent→delivered or failed; failures show red "Not sent · Tap to retry"; MMS gated for alphanumeric senders
- ✅ Draft fix: clearing text now erases the stored draft on back
- ✅ Contact photos: avatars show contact profile pictures when available
- ✅ Dark-mode bubble text: explicit onSurface/onPrimaryContainer colors (ClickableText regression fixed)
- ✅ Settings redesigned to GM style: icon-less rounded card groups, no section headers, same functionality
- ✅ Back navigation fixes: Archived/search views return to list on back (no more app close), root screen guarded with "Press back again to exit" (accidental swipes no longer kill the app) — test: `scripts/test-back-nav.sh`
- ✅ Draft fix v2: leaving chat via top-bar ← arrow also saves/clears draft (was bypassing BackHandler)
- ✅ F-Droid prep: conditional release signing (keystore optional, passwords via env), machine-specific JDK pin moved out of repo, GPL-3.0 LICENSE, README, gradle wrapper, fastlane metadata (title/descriptions/changelogs), .gitignore covers keystore/apk/local caches

## P1 · Scheduled messages UI

✅ DONE. Long-press send button → M3 DatePickerDialog → TimePicker → saves to DB + sets AlarmManager alarm. Scheduled messages list with cancel in Settings.

## P2 · Quick reply from notification

✅ DONE. `RemoteInput` on notification + `QuickReplyReceiver` BroadcastReceiver sends SMS from notification inline reply. Registered in AndroidManifest.

File: `sms/SmsSupport.kt`, `sms/QuickReplyReceiver.kt`

## P3 · Message locking

✅ DONE. `locked` column in messages table (DB v10), Lock/Unlock in message context menu, biometric/PIN prompt via `BiometricPrompt`, locked messages show "🔒 Locked" until authenticated, re-lock on chat exit. Test: `scripts/test-message-lock.sh`

File: `data/Repository.kt`, `data/Models.kt`, `ui/ChatScreen.kt`

## Splash screen dark mode

- ✅ Splash now follows dark mode: added `values-night/themes.xml` (dark Material parent), `values-v31` + `values-night-v31` with `windowSplashScreenBackground` matched to app surface (#F8F9FC light / #131314 dark). Verified brightness 238 light / 30 dark. Test: `scripts/test-splash.sh`

## Skeleton loading shimmer

- ✅ Shimmer skeleton placeholder while content loads (8 rows with animated gradient circles/bars matching GM style). 400ms hold before real content appears.

## Demo data for F-Droid

- ✅ 10 realistic conversations seeded on fresh install (Sarah, Mom, Work, Jake, Emma, Dad, Pizza Palace, Alex, Dr. Patel, Gym Buddy) with 3-5 messages each, unread badges, pinned item
- ✅ 6 contact avatar PNGs (colored circles with initials) in `res/drawable-xxhdpi/`
- ✅ `DemoData.kt` seeds conversations + messages when DB is empty; `loadContactPhoto` returns demo avatars for seeded numbers
- ✅ F-Droid screenshots saved in `screenshots/fdroid/` (8 images: home/chat/settings/reply × dark/light). Script: `scripts/take-fdroid-screenshots.sh`

## Chat UI adjustments (user request)

- ✅ Save-contact banner hidden in chat window (code commented out, easily restorable)
- ✅ SIM selector moved from input pill to chat 3-dot menu (per-SIM rows with radio buttons, carrier names, persists selection); hidden on single-SIM devices. Test: `scripts/test-sim-menu.sh`
- ✅ Notifications toggle removed from chat 3-dot menu (per-conversation toggle remains in Contact details screen)
- ✅ Archive menu item fixed (was a dead control — onClick only closed the menu); now archives, toasts, returns to list. All chat-menu options verified: Add people→contact editor, Details, Archive/Unarchive, Delete→trash, Block/Unblock. Test: `scripts/test-chat-menu.sh`

## Contact details header photo

- ✅ Header avatar now uses `PersonAvatar` (loads real contact photo) instead of hardcoded placeholder — matches the participant row which already showed the photo

## P2/P3/P5 follow-up fixes

- ✅ Crash fix: DB self-healing in `Db.onOpen` — recreates `conversation_notifications` and adds missing `locked` column even when an intermediate APK shipped a broken migration
- ✅ Message long-press fix: replaced `ClickableText` with plain `Text` (links handled natively by Compose) so the bubble's `combinedClickable` long-press fires; Copy/Lock menu reachable again
- ✅ Verified: link taps still open browser (`test-links-and-senders.sh`), lock persists across restart

## Recent fixes

- ✅ Back navigation on gesture devices: ~~`onBackPressed()` override in MainActivity~~ SUPERSEDED by unified BackHandler stack (see next entry); `enableOnBackInvokedCallback="false"` in manifest
- ✅ Skeleton flash fix: skeleton only shows on first app load (400ms), not on back navigation (static `hasLoadedOnce` flag)
- ✅ OTP highlighting: 4-8 digit standalone numbers highlighted in primary color with medium weight in message bubbles
- ✅ Privacy mode enhancements: notification content hidden (shows "New message" / "You have a new message"), `android:taskAffinity=""` for recent apps content hiding, `FLAG_SECURE` on window
- ✅ App lock: fingerprint/PIN authentication on app launch (BiometricPrompt from AndroidX Biometric), graceful fallback on devices without biometric hardware, toggle in Settings > Privacy
- ✅ Message unlock simplified: removed biometric prompt for lock/unlock in chat context menu (direct toggle)
- ✅ Avatar palette expanded: 16 colors, 10 demo avatar PNGs (256x256) seeded via DemoData
- ✅ `FragmentActivity` base class (required for BiometricPrompt)
- ✅ Smart OTP detection: keyword-gated tiered matcher in new `ui/OtpDetector.kt` (adjacent keyword, grouped "482 913"/"4433-2211", bare 6-digit with strong keyword), currency + year-shaped guards; bold primary highlight; JUnit coverage in `OtpDetectorTest` — test: `scripts/test-otp.sh`

File: `MainActivity.kt`, `SettingsScreen.kt`, `SmsSupport.kt`, `AndroidManifest.xml`, `Components.kt`, `DemoData.kt`

## Back-stack fix (2026-08-24)

- ✅ BUG: system BACK (button or gesture) from the contact profile screen jumped to the conversation LIST instead of returning to the chat — caused by the removed `onBackPressed()` override mapping `"details" -> "list"`
- ✅ BUG: two competing back systems (`Activity.onBackPressed` override + per-screen Compose `BackHandler`s). The override bypassed the dispatcher entirely, so button-back from chat skipped `leaveChat()` and silently DROPPED drafts
- ✅ FIX: deleted the `onBackPressed()` override; ONE top-level `BackHandler` in `MainActivity` pops a virtual stack: `details→chat`, `trash→settings`, everything else→`list`. Child-screen handlers (draft save, search clear, double-back-exit guard) still win via dispatcher priority (last-registered wins)
- ✅ BONUS: draft is now saved when leaving chat via the back BUTTON (previously only the ← arrow did) 
- ✅ BONUS: `--ez open_settings true` script hook now actually opens Settings (was only suppressing the SMS-role dialog), and `onNewIntent` honors `set_theme`/`open_settings` on warm starts too
- Test: `scripts/test-back-stack.sh` (8 checks, all passing); regression: `scripts/test-back-nav.sh` still green

File: `MainActivity.kt`, `scripts/test-back-stack.sh`

## Storage & first-launch fixes (2026-08-24)

- ✅ BUG: demo conversations seeded on devices with real SMS (seed ran whenever local table was empty, before system sync) — now seeds only when READ_SMS granted AND system provider empty; polluted installs are auto-purged on launch
- ✅ First launch now keeps skeleton loading until initial system-SMS import completes (`Repository.initialSyncDone` StateFlow), not a fixed 400 ms
- ✅ SmsReceiver incoming write-back hardened: checks RoleManager role in addition to getDefaultSmsPackage, logs failures instead of swallowing
- ✅ Verified storage guarantees: uninstall-safe (history re-imports from system provider), cross-app mirror both directions — test: `scripts/test-sms-mirror.sh` (all passing)
- ✅ Fixed `env.sh center_of_contains` bounds regex; `test-back-stack.sh` no longer depends on demo rows

File: `data/Repository.kt`, `data/DemoData.kt`, `sms/SmsReceiver.kt`, `ui/ConversationsScreen.kt`, `scripts/test-sms-mirror.sh`

## P4 · Auto-delete old messages

Settings option to auto-delete messages older than N days.

- Add setting in SettingsStore
- Cleanup logic on app launch or via WorkManager

File: `data/SettingsStore.kt`, `data/Repository.kt`

## P5 · Custom notification settings per-conversation

✅ DONE. `conversation_notifications` table (DB v9, ON DELETE CASCADE), Repository methods, toggle in ContactDetailsScreen + ChatScreen 3-dot menu, NotificationHelper checks before posting.

## Regression guardrails

After any task: run `scripts/run-all-tests.sh`, eyeball screenshots
(01-home, 04-sent, 11-settings, 15-theme-dark), ensure build green, no new
permissions beyond listed in AGENTS.md, and zero dead controls introduced.
