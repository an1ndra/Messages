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

Reply directly from notification without opening the app (like QKSms).

- Add `RemoteInput` to notification in `NotificationHelper.kt`
- Handle reply intent in `SmsReceiver.kt`

File: `sms/NotificationHelper.kt`, `sms/SmsReceiver.kt`

## P3 · Message locking

Long-press message in chat → "Lock" action → message requires authentication to view.

- Store locked state in messages table (add `locked INTEGER` column)
- Gate display behind biometric/PIN prompt

File: `data/Repository.kt`, `ui/ChatScreen.kt`

## P4 · Auto-delete old messages

Settings option to auto-delete messages older than N days.

- Add setting in SettingsStore
- Cleanup logic on app launch or via WorkManager

File: `data/SettingsStore.kt`, `data/Repository.kt`

## P5 · Custom notification settings per-conversation

Per-conversation notification tone and vibration settings.

- Store in a new table or SharedPreferences keyed by conversation ID
- Expose in chat 3-dot menu

File: `data/SettingsStore.kt`, `ui/ChatScreen.kt`

## Regression guardrails

After any task: run `scripts/run-all-tests.sh`, eyeball screenshots
(01-home, 04-sent, 11-settings, 15-theme-dark), ensure build green, no new
permissions beyond listed in AGENTS.md, and zero dead controls introduced.
