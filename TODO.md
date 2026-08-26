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
- ✅ Swipe threshold actually enforced: material3 `positionalThreshold` is ignored (known bug, issuetracker 471021165 — settle at ~50% + 125dp/s velocity), so short swipes deleted rows; gated with `confirmValueChange` + `progress >= 0.65f` in `SwipeConversationItem` (ConversationsScreen.kt) — test: `scripts/test-swipe-threshold.sh`
- ✅ Trash confirmations + polish (issue #87): "Empty trash" and "Delete forever" now ask M3 AlertDialog confirmation before destroying data; Trash rows restyled to match main list (48dp avatar, 12dp padding, gray restore icon, inset dividers); empty state shows 30-day retention hint
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
- ✅ CI GPG signing (fdroid-release.yml): gpg wrapper as `gpg.program` passing passphrase via `--passphrase` + loopback pinentry — `--passphrase-fd 0` is unusable with git (git feeds commit data on stdin); GNUPGHOME exported in-step AND via GITHUB_ENV; GPG_PASSPHRASE passed to later steps via multi-line `<<EOF` env format. E2E verified locally: signed commit + signed tag through the wrapper. Requires secrets GPG_PRIVATE_KEY + GPG_PASSPHRASE.

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
- ✅ Crash fix: back from chat killed the process (`ConcurrentModificationException` in `Repository.notifyChanged` when draft-save raced Flow listener churn); listeners now a `CopyOnWriteArrayList` — reported via real-device logcat
- ✅ Screen transitions: all routes animate via a single direction-aware `AnimatedContent` (forward = slide-in-from-right, back = slide-out-to-right, same-depth = fade); replaces instant `when(navRoute)` swaps and the chat↔details-only animation

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

## Initial-sync progress bar + OTP duplicate fix (2026-08-24)

- ✅ BUG: first launch showed no progress indication while system SMS imported in background — determinate `LinearProgressIndicator` ("Loading messages") now renders under the home header while `Repository.initialSyncProgress` (0..1, null=idle) is active; bar only appears when there is pending work, dismisses on completion
- ✅ BUG: same OTP message appeared twice after import — `SmsReceiver`/`writeSentToSystem` wrote to the system provider WITHOUT linking the returned `_ID`, so sync re-imported them whenever the SMSC timestamp skewed past the ±2 min match window; now provider row is inserted FIRST and its `_ID` is stored via `receiveMessage(..., sysId)` / linked back in `writeSentToSystem`
- ✅ Hardened legacy linker: matches `is_me` + nearest timestamp within ±24 h (was ±2 min, no sender filter)
- ✅ Sync runs serialized on a single-thread executor (overlapping onResume threads could double-import)
- ✅ DB v11: migration collapses rows sharing a `sys_id`, deletes unlinked local twins of already-linked messages, creates unique partial index `idx_messages_sys_id (sys_id>0)`; onOpen recreates the index if missing; inserts tolerate constraint races
- ✅ Verified: bulk-import bar render/dismissal (4k SMS), zero duplicate groups post-migration from fabricated v10 corruption (3 copies → 1), fresh OTP receive stores exactly 1 copy — test: `scripts/test-initial-sync.sh`

File: `data/Repository.kt`, `sms/SmsReceiver.kt`, `MainActivity.kt`, `ui/ConversationsScreen.kt`

## P4 · Auto-delete old messages

Settings option to auto-delete messages older than N days.

- Add setting in SettingsStore
- Cleanup logic on app launch or via WorkManager

File: `data/SettingsStore.kt`, `data/Repository.kt`

## P5 · Custom notification settings per-conversation

✅ DONE. `conversation_notifications` table (DB v9, ON DELETE CASCADE), Repository methods, toggle in ContactDetailsScreen + ChatScreen 3-dot menu, NotificationHelper checks before posting.

## F-Droid auto-sync (2026-08-26)

- ✅ `release.yml` now syncs the F-Droid metadata automatically: after the GitHub Release publishes, a `sync-fdroiddata` job clones `an1ndra/fdroiddata` (branch `com.anindra.messages`) with the `GITLAB_TOKEN` secret and rewrites versionName/versionCode/pinned commit/CurrentVersion(Code) in `metadata/com.anindra.messages.yml`, then pushes — updating fdroid MR !46632; no-op-safe on re-runs
- ✅ Removed the duplicate "Update fdroiddata MR" step + `update_fdroiddata` input from `fdroid-release.yml` (Release workflow is now the single owner; no push race)
- ✅ `fdroid-release.yml` deleted — one-click UI release-cutting dropped; releases are cut locally (bump + signed commit/tag push), `release.yml` handles everything after

## Contact picker truncation fix (2026-08-26)

- ✅ Issue #98: NewChatScreen hard-capped contacts at 200 (`out.size < 200`) with no truncation indicator; worse, the picker's search filter ran POST-truncation, so contacts beyond #200 were unfindable by name
- ✅ Removed the cap; `rememberContacts()` loads unbounded off the main thread via `produceState` + `Dispatchers.IO` (same pattern as contact photos in Components.kt); LazyColumn already virtualizes rendering
- ✅ Regression script seeds >200 uniquely-named ("ZzqNNN", sort-last) contacts via parallel content-provider workers, then asserts the LAST one appears when searched — test: `scripts/test-contacts-limit.sh`

File: `ui/NewChatScreen.kt`, `scripts/test-contacts-limit.sh`

## Archive swipe UNDO (2026-08-26)

- ✅ Issue #97: swipe-right archive fired silently while swipe-left delete showed an UNDO snackbar; also bottom-sheet "Archive" had zero feedback
- ✅ Added `archiveWithUndo()` (archive → "Conversation archived · Undo" snackbar → unarchive on tap), threaded as `onArchive` callback through `SwipeableConversationItem`/`SwipeConversationItem`; bottom-sheet Archive now routes through it too (chat 3-dot menu keeps its toast — no snackbar host in ChatScreen)
- ✅ Regression script swipes right on the topmost list row, asserts the snackbar + Undo action appear, taps Undo and asserts the row returns — test: `scripts/test-archive-undo.sh`

File: `ui/ConversationsScreen.kt`, `scripts/test-archive-undo.sh`

## Quick-reply receiver threading fix (2026-08-26)

- ✅ Issue #90 code fix: `QuickReplyReceiver` now uses `goAsync()` + `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (ScheduledMessageSender pattern); DB write, SMS send and system-mirror all off the main thread with try/catch + `finish()` in `finally`; notification cancel moved after durable DB insert
- ✅ Regression script `scripts/test-quick-reply.sh`: clears shade → injects fresh SMS (app force-stopped = cold path) → opens notification → tries raw `motionevent DOWN/UP` on the Reply action → types reply → asserts the row lands in system Sent box (`content://sms/sent`) + crash buffer clean
- ⚠️ Automation limit (documented in script): systemui re-routes injected taps (`input tap`, swipe-hold, motionevent) from action buttons to the row body → auto-cancels instead of opening inline reply; script falls back to a MANUAL STEP prompt for that one tap, then resumes automated verification
- ✅ Verified end-to-end with manual shade tap: `autoreplyping90` landed in system Sent box, crash buffer clean — test: `scripts/test-quick-reply.sh`

File: `sms/QuickReplyReceiver.kt`, `scripts/test-quick-reply.sh`

## Chat list O(n²) fix (2026-08-26)

- ✅ Issue #94: `messages.indexOfFirst { it.id == msg.id }` ran inside the LazyColumn `items` lambda — O(n) per composed item, O(n²) per frame during scroll; replaced with `itemsIndexed(messages, key = { _, msg -> msg.id })` so the index comes from LazyListScope directly (zero lookups, zero allocations)
- ✅ Regression script opens a conversation, flings to top to assert the idx==0 "Today" divider renders (chat opens bottom-scrolled, so the first divider is lazily off-screen — assertion scrolls up first), plus asserts the last-own-message status line (`H:MM • SMS`) at the bottom — test: `scripts/test-chat-render.sh`

File: `ui/ChatScreen.kt`, `scripts/test-chat-render.sh`

## Critical/high batch fixes (2026-08-26)

- ✅ Issue #81 (critical): scheduled sends could lose text or leave ghost rows — `ScheduledMessageSender` now wraps the radio hand-off in its own try/catch: on throw, the stored message is marked `"failed"` (retriable "Not sent · Tap to retry") instead of staying `"sending"` forever; `deleteScheduledMessage` runs unconditionally once content is durably stored, so no zombie schedule entries can accumulate
- ✅ Issue #82 (high): `SmsReceiver.onReceive` did system-provider insert + local DB write + notification synchronously on the main thread with no goAsync — restructured to goAsync + `CoroutineScope(SupervisorJob() + Dispatchers.IO)` with processing in `processIncoming()` and finish in finally; multipart grouping and role checks unchanged
- ✅ Issue #85 (high): `ImageBubble` decoded full bitmaps inside `remember {}` on the main thread — now `produceState` + `withContext(Dispatchers.IO)` keyed on uri (same pattern as PersonAvatar); null-check moved to a local val for smart-cast
- ✅ Regression scripts: `test-scheduled-send.sh` (drives ScheduledMessageSender via root broadcast — receiver is exported=false so plain shell broadcasts are dropped; asserts message renders + mirrors to Sent box), plus existing `test-sms-mirror.sh` (incoming path) and `test-chat-render.sh` all PASS on emulator-5554
- ⚠️ Pre-existing stale script noticed: `test-p2-p3-p5.sh` P3 step expects `resource-id="row_N"` nodes that no longer exist in ChatScreen — broken before today's changes, needs a separate refresh

File: `sms/ScheduledMessageSender.kt`, `sms/SmsReceiver.kt`, `ui/ChatScreen.kt`, `scripts/test-scheduled-send.sh`

## Medium/low batch A fixes (2026-08-26)

- ✅ Issue #89: `ensureChannel()` moved above the `canPost()` early-return in both `show()` and `showSendFailed()` — notify() with an unknown channel is a silent no-op, so the channel must exist before any bail-out; verified with a pm-clear cold install + injected SMS (notification posts)
- ✅ Issue #83: `MessagesApplication.onCreate` no longer runs trash purge + system-SMS sync on the main thread — both launched in `CoroutineScope(SupervisorJob() + Dispatchers.IO)`; UI already gates on `initialSyncDone`
- ✅ Issue #95: file-level `private var hasLoadedOnce` replaced by a process-scoped field on `AppViewModel` (`vm.hasLoadedOnce`) — same skeleton-flash suppression semantics without module-wide mutable state
- ✅ Issues #84 + #100: Components.kt date helpers migrated to thread-safe `java.time` — shared `SimpleDateFormat` vals gone (DateTimeFormatter is immutable), `sameDay`/`isYesterday` now compare `LocalDate`s (zero Calendar allocations per row, DST-correct yesterday via `LocalDate.now(zone).minusDays(1)`); divider format hoisted to a named formatter
- ✅ Verified: build green; `test-chat-render.sh` PASS ("Today" divider via new java.time path), cold-start + incoming-notification probe PASS

File: `sms/SmsSupport.kt`, `MessagesApplication.kt`, `MainActivity.kt`, `ui/ConversationsScreen.kt`, `ui/Components.kt`

## Medium/low batch B fixes (2026-08-26)

- ✅ Issue #92: `NotificationHelper` used `from.hashCode()` for notification ids and QuickReplyReceiver's PendingIntent, so two senders sharing a hash could overwrite each other's notification; `showSendFailed()` also collided with incoming ids. Now uses `(convoId ?: from.hashCode().toLong()).toInt()` as the stable notifId; `QuickReplyReceiver` reads `EXTRA_NOTIF_ID` from the intent (with hashCode fallback for stale intents); `showSendFailed` posts under a `"failed"` tag to decouple from incoming ids
- ✅ Issue #93: `ChatScreen` stored the delayed-send `Job` in `mutableStateOf` — cancel and restart raced against Compose recomposition. Replaced with a counter-based `LaunchedEffect(sendAttempt)` that Compose cancels/rearms automatically on key change; no mutable `Job` state needed
- ✅ Verified: `test-delayed-send.sh` PASS (auto-send after 5s countdown, Cancel aborts with no Sent-box entry)

File: `sms/SmsSupport.kt`, `sms/QuickReplyReceiver.kt`, `ui/ChatScreen.kt`

## Medium/low batch C fixes (2026-08-26)

- ✅ Issue #88: `ContactDetailsScreen` had a "Search" `DetailActionButton` with `onClick = {}` — removed the button and its `Icons.Rounded.Search` import (hard rule #2: every visible control must do something real)
- ✅ Issue #96: `SettingsScreen` copied all settings into `remember` state at initial composition; external changes (e.g. `--es set_theme dark` deep link) never synced. `SettingsStore` now emits a `revision: StateFlow<Int>` that increments on every write; `SettingsScreen` collects it and re-keys each `remember(revision)` block so stale locals are replaced on recomposition
- ✅ Issue #99: `ChatScreen.kt` was 1341 lines with a ~640-line `ChatScreen` composable. Extracted three focused composables: `ChatTopBar` (top bar + 3-dot menu with SIM picker, archive, delete, block/unblock), `ChatMessageList` (LazyColumn with message rows, retry, forward, lock/unlock), `ChatSchedulePicker` (date + time picker flow). `ChatScreen` now delegates to these composables, reducing inline logic and improving maintainability

File: `ui/ContactDetailsScreen.kt`, `data/SettingsStore.kt`, `ui/SettingsScreen.kt`, `ui/ChatScreen.kt`

## Regression guardrails

After any task: run `scripts/run-all-tests.sh`, eyeball screenshots
(01-home, 04-sent, 11-settings, 15-theme-dark), ensure build green, no new
permissions beyond listed in AGENTS.md, and zero dead controls introduced.
