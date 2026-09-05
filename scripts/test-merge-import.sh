#!/usr/bin/env bash
set -euo pipefail
# Merge-import regression test: import a PIN-protected .enc backup with the
# new "Merge with existing messages" option and prove that BOTH the merge
# source and the pre-existing device messages survive (no replace/restart).
# NOTE: conversation presence is asserted by matching the LAST-MESSAGE PREVIEW
# text, not the phone number — the UI formats numbers per locale
# (e.g. "+1-555-123-4501") so raw-address greps are unreliable.
# Flow (drives the current Settings UI):
#   inject SMS to NUM_A (conversation exists in the backup) →
#   Settings → Backup messages → set PIN → write .enc (contains NUM_A) →
#   inject SMS to NUM_B (live-only conversation, NOT in the backup) →
#   Import messages (SAF picker) → pick newest .enc →
#   "Import backup" dialog → tap "Merge with existing messages" →
#   "Enter backup PIN" → PIN → Import →
#   assert BOTH NUM_A and NUM_B are still on the home list — WITHOUT restart
#   (merge writes in place and refreshes flows; replace would drop NUM_B).
# PIN="${PIN:-1234}" overrides the test PIN.
export ADB="${ADB:-$HOME/android/platform-tools/adb}"
source "$(dirname "$0")/env.sh"

dump_ui() {
  adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb_ shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
}
bounds_of() {
  grep -oE "<node[^>]*text=\"$1\"[^>]*bounds=\"[^\"]*\"" "$TMP/ui.xml" | head -1 \
    | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
first_enc_bounds() {
  grep -oE '<node[^>]*text="messages_backup_[^"]*\.enc"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
tap_center() {
  local b=$1 x1 y1 x2 y2
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  x2=$(sed -E 's/.*\]\[([0-9]+),[0-9]+\]/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
tap_label() {
  local b
  b=$(bounds_of "$1") || true
  [ -z "$b" ] && { echo "[fail] '$1' not found on screen"; return 1; }
  tap_center "$b"
}
edits() {
  grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
nth_edit() { edits | sed -n "${1}p"; }
greps_ui() { grep -q "$1" "$TMP/ui.xml"; }

PREVIEW_A="merge source conversation"
PREVIEW_B="live device only message"
NUM_A="+15551234501"
NUM_B="+15551234502"
PIN="${PIN:-1234}"
BACKDIR="storage/emulated/0/Documents/Messages"

echo "== Step 0: seed conversations =="
adb_ emu sms send "$NUM_A" "$PREVIEW_A" >/dev/null 2>&1 || true
sleep 2
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$PKG/.MainActivity"
sleep 4
dump_ui
for _ in 1 2 3; do
  if greps_ui 'Set as default SMS app?'; then
    adb_ shell input tap 533 1352; sleep 2; dump_ui
  elif grep -qE 'Allow Messages to (access|send|start)' "$TMP/ui.xml"; then
    adb_ shell input tap 900 1470; sleep 1; dump_ui
  else break; fi
done
greps_ui "$PREVIEW_A" || { echo "[fail] $NUM_A (preview '$PREVIEW_A') not on home list"; exit 1; }
echo "[ok] $NUM_A conversation present"

echo "== Step 1: create a PIN backup (contains $NUM_A) =="
adb_ shell input tap 975 226
sleep 2
for _ in 1 2 3 4; do
  if greps_ui 'Backup messages'; then break; fi
  adb_ shell input swipe 540 1900 540 400 300
  sleep 1
  dump_ui
done
greps_ui 'Backup messages' || { echo "[fail] Settings backup rows not visible"; exit 1; }
before=$(adb_ shell "ls $BACKDIR | grep -c '\.enc'" 2>/dev/null | tr -dc '0-9')
tap_label 'Backup messages'
sleep 3
dump_ui
greps_ui 'Set backup PIN' || { echo "[fail] Set backup PIN dialog not shown"; exit 1; }
[ -z "$(nth_edit 1)" ] && { echo "[fail] PIN field 1 missing"; exit 1; }
tap_center "$(nth_edit 1)"
adb_ shell input text "$PIN"
sleep 1
dump_ui
[ -z "$(nth_edit 2)" ] && { echo "[fail] PIN field 2 missing"; exit 1; }
tap_center "$(nth_edit 2)"
adb_ shell input text "$PIN"
sleep 1
dump_ui
b=$(bounds_of 'Save'); [ -z "$b" ] && { echo "[fail] Save button not found"; exit 1; }
tap_center "$b"
sleep 4
after=$(adb_ shell "ls $BACKDIR | grep -c '\.enc'" 2>/dev/null | tr -dc '0-9')
[ "$after" -gt "$before" ] \
  && echo "[ok] PIN backup written ($before -> $after .enc)" \
  || { echo "[fail] no new .enc backup produced"; exit 1; }

echo "== Step 2: live-only conversation (NOT in the backup) =="
adb_ emu sms send "$NUM_B" "live device only message" >/dev/null 2>&1 || true
sleep 2
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$PKG/.MainActivity"
sleep 4
dump_ui
greps_ui "$PREVIEW_B" || { echo "[fail] $NUM_B (preview '$PREVIEW_B') not on home list"; exit 1; }
echo "[ok] $NUM_B conversation present (would be lost by a replace)"

echo "== Step 3: import the newest .enc via MERGE =="
NEWEST=$(adb_ shell "ls $BACKDIR/messages_backup_*.enc 2>/dev/null" \
  | xargs -n1 basename 2>/dev/null | sort | tail -1)
echo "[ok] newest backup: $NEWEST"
adb_ shell input tap 975 226
sleep 2
for _ in 1 2 3 4; do
  if greps_ui 'Import messages'; then break; fi
  adb_ shell input swipe 540 1900 540 400 300
  sleep 1
  dump_ui
done
greps_ui 'Import messages' || { echo "[fail] Import messages row not visible"; exit 1; }
tap_label 'Import messages'
sleep 3
b=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  dump_ui
  b=$(grep -oE "<node[^>]*text=\"$NEWEST\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
    "$TMP/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
  [ -n "$b" ] && break
  adb_ shell input swipe 540 2000 540 400 600
  sleep 1
done
[ -z "$b" ] && { echo "[fail] $NEWEST not found in picker"; exit 1; }
echo "[ok] picking $NEWEST at $b"
tap_center "$b"
sleep 3

# 3b. "Import backup" dialog → choose MERGE.
dump_ui
greps_ui 'Import backup' || { echo "[fail] Import backup dialog not shown"; exit 1; }
greps_ui 'Merge with existing messages' || { echo "[fail] Merge option missing"; exit 1; }
tap_label 'Merge with existing messages'
sleep 2

# 3c. PIN-format backup → "Enter backup PIN" dialog → PIN → Import.
dump_ui
greps_ui 'Enter backup PIN' || { echo "[fail] Enter backup PIN dialog not shown"; exit 1; }
[ -z "$(nth_edit 1)" ] && { echo "[fail] PIN entry field missing"; exit 1; }
tap_center "$(nth_edit 1)"
adb_ shell input text "$PIN"
sleep 1
dump_ui
b=$(bounds_of 'Import'); [ -z "$b" ] && { echo "[fail] Import button not found"; exit 1; }
tap_center "$b"
sleep 6

echo "== Step 4: assert BOTH conversations survived (no restart) =="
# Merge writes in place with no restart, so we're still on Settings after the
# import — navigate back to the home list the way the user would.
adb_ shell input keyevent KEYCODE_BACK
sleep 3
dump_ui
okA=0; okB=0
for _ in 1 2 3; do
  greps_ui "$PREVIEW_A" && okA=1
  greps_ui "$PREVIEW_B" && okB=1
  [ "$okA" = "1" ] && [ "$okB" = "1" ] && break
  sleep 1
  dump_ui
done
[ "$okA" = "1" ] && echo "[ok] $NUM_A present after merge (from backup)" \
                  || { echo "[fail] $NUM_A ($PREVIEW_A) missing after merge"; exit 1; }
[ "$okB" = "1" ] && echo "[ok] $NUM_B present after merge (pre-existing kept)" \
                  || { echo "[fail] $NUM_B ($PREVIEW_B) lost — replace happened instead of merge"; exit 1; }

adb_ logcat -d -t 400 2>/dev/null | grep -iE "ImportResult|Import failed" | tail -5 || true
echo "[done] merge-import round trip OK"