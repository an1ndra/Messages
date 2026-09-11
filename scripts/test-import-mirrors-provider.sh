#!/usr/bin/env bash
set -euo pipefail
# Import → system-provider mirror (regression test).
# User bug: restoring a backup in-app repopulated our local DB but left the
# Android system SMS provider empty, so the default Messaging app showed
# nothing and SMS Import/Export exported "0 SMS(s)". Fix: importDatabase
# calls pushLocalMessagesToProvider() which mirrors restored rows into
# content://sms (skipping rows whose sys_id still exists server-side).
#
# Flow:
#   adb root → wipe provider → inject 2 SMS → app syncs (2 local/2 provider)
#   → in-app PIN backup    → wipe provider + pm clear app (fresh phone) →
#   in-app import (Restore replace all) → provider must regain 2 rows →
#   AOSP Messaging shows the thread → SMS Import/Export exports "2 SMS(s)".
# PIN="${PIN:-1234}" overrides the test PIN.
export ADB="${ADB:-$HOME/android/platform-tools/adb}"
source "$(dirname "$0")/env.sh"

dump_ui() {
  adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb_ shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
}
bounds_of() { # bounds of first node whose text==="$1" (literal)
  local esc
  esc=$(sed 's/[][().*+?^$\\|]/\\&/g' <<< "$1")
  grep -oE "<node[^>]*text=\"$esc\"[^>]*bounds=\"[^\"]*\"" "$TMP/ui.xml" | head -1 \
    | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
tap_center() { # taps center of a bounds string
  local b=$1 x1 y1 x2 y2
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  x2=$(sed -E 's/.*\]\[([0-9]+),[0-9]+\]/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
tap_label() {
  local b; b=$(bounds_of "$1") || true
  [ -z "$b" ] && { echo "[fail] '$1' not found on screen"; return 1; }
  tap_center "$b"
}
edits() { grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true; }
nth_edit() { edits | sed -n "${1}p"; }
greps_ui() { grep -F -q "$1" "$TMP/ui.xml"; }

PROVDB=/data/data/com.android.providers.telephony/databases/mmssms.db
BACKDIR="storage/emulated/0/Documents/Messages"
PIN="${PIN:-1234}"
ADDR="+15551234566"

provider_count() { adb_ shell "sqlite3 $PROVDB 'SELECT COUNT(*) FROM sms;'" 2>/dev/null | tr -dc '0-9'; }
local_count() { adb_ shell "run-as $PKG sh -c 'sqlite3 databases/messages.db \"SELECT COUNT(*) FROM messages;\"'" 2>/dev/null | tr -dc '0-9'; }

echo "== Preflight =="
adb_ root >/dev/null 2>&1 || true
sleep 1
[ "$(adb_ shell echo ok)" = "ok" ] || { echo "[fail] device not reachable"; exit 1; }
adb_ shell pm clear "$PKG" >/dev/null 2>&1 || true
for p in READ_SMS RECEIVE_SMS SEND_SMS READ_CONTACTS POST_NOTIFICATIONS; do
  adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null || true
done

# 1. Fresh provider + seed 2 inbound SMS through the emulator radio.
echo "== Step 1: seed baseline (provider 2 / local 2) =="
adb_ shell "sqlite3 $PROVDB 'DELETE FROM canonical_addresses; DELETE FROM threads; DELETE FROM sms;'" 2>/dev/null || true
[ "$(provider_count)" = "0" ] || { echo "[fail] provider not empty"; exit 1; }
adb_ emu sms send "$ADDR" "Mirror test message one"
adb_ emu sms send "$ADDR" "Mirror test message two"
adb_ shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 8
LOCAL_OK=$(local_count)
PROV_OK=$(provider_count)
echo "[db] local=$LOCAL_OK provider=$PROV_OK"
[ "$LOCAL_OK" = "2" ] && [ "$PROV_OK" = "2" ] || { echo "[fail] baseline sync wrong"; exit 1; }

# 2. In-app PIN backup → prune → keep the exact newest backup.
echo "== Step 2: backup =="
adb_ shell input tap 975 226; sleep 2   # Settings via top-right avatar
for _ in 1 2 3 4; do
  greps_ui 'Backup messages' && break
  adb_ shell input swipe 540 1900 540 400 300; sleep 1; dump_ui
done
greps_ui 'Backup messages' || { echo "[fail] Settings backup row not visible"; exit 1; }
before=$(adb_ shell "ls $BACKDIR | grep -c '\.enc'" 2>/dev/null | tr -dc '0-9')
tap_center "$(bounds_of 'Backup messages')"
sleep 3; dump_ui
greps_ui 'Set backup PIN' || { echo "[fail] Set backup PIN dialog not shown"; exit 1; }
tap_center "$(nth_edit 1)"; adb_ shell input text "$PIN"; sleep 1
dump_ui # IME shifts the dialog → re-resolve field 2
tap_center "$(nth_edit 2)"; adb_ shell input text "$PIN"; sleep 1
dump_ui
b=$(bounds_of 'Save'); [ -z "$b" ] && { echo "[fail] Save not found"; exit 1; }
tap_center "$b"; sleep 4
after=$(adb_ shell "ls $BACKDIR | grep -c '\.enc'" 2>/dev/null | tr -dc '0-9')
[ "$after" -gt "$before" ] && echo "[ok] backup written ($before -> $after .enc)" \
  || { echo "[fail] no new .enc produced"; exit 1; }
NEWEST=$(adb_ shell "ls $BACKDIR/messages_backup_*.enc 2>/dev/null" | xargs -n1 basename 2>/dev/null | sort | tail -1)
echo "[ok] newest backup: $NEWEST"

# 3. Wipe BOTH the provider and the app → "fresh phone" state.
echo "== Step 3: simulate fresh phone (wipe provider + app) =="
adb_ shell "sqlite3 $PROVDB 'DELETE FROM canonical_addresses; DELETE FROM threads; DELETE FROM sms;'" 2>/dev/null || true
[ "$(provider_count)" = "0" ] || { echo "[fail] provider still has rows"; exit 1; }
adb_ shell pm clear "$PKG" >/dev/null 2>&1
for p in READ_SMS RECEIVE_SMS SEND_SMS READ_CONTACTS POST_NOTIFICATIONS; do
  adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null || true
done
adb_ shell cmd role add-role-holder android.app.role.SMS "$PKG" >/dev/null 2>&1 || true

# 4. Import the backup (Restore replace all). This is the code path that must mirror.
echo "== Step 4: in-app import =="
adb_ shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 5
adb_ shell input tap 975 226; sleep 2
for _ in 1 2 3 4; do
  greps_ui 'Import messages' && break
  adb_ shell input swipe 540 1900 540 400 300; sleep 1; dump_ui
done
greps_ui 'Import messages' || { echo "[fail] Import messages row not visible"; exit 1; }
tap_center "$(bounds_of 'Import messages')"
sleep 3
b=""
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  dump_ui
  b=$(grep -oE "<node[^>]*text=\"$NEWEST\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
    "$TMP/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true)
  [ -n "$b" ] && break
  adb_ shell input swipe 540 2000 540 400 600; sleep 1
done
[ -z "$b" ] && { echo "[fail] $NEWEST not found in picker"; exit 1; }
echo "[ok] picking $NEWEST"
tap_center "$b"; sleep 3
dump_ui
greps_ui 'Import backup' || { echo "[fail] Import backup dialog not shown"; exit 1; }
tap_label 'Restore (replace all)'; sleep 2
dump_ui
greps_ui 'Enter backup PIN' || { echo "[fail] Enter backup PIN dialog not shown"; exit 1; }
tap_center "$(nth_edit 1)"; adb_ shell input text "$PIN"; sleep 1
dump_ui # re-resolve Import after IME shift
b=$(bounds_of 'Import'); [ -z "$b" ] && { echo "[fail] Import button not found"; exit 1; }
tap_center "$b"
sleep 8

# 5. The provider must now hold the mirrored rows (the regression).
echo "== Step 5: verify provider mirror =="
LOCAL_OK=$(local_count); PROV_OK=$(provider_count)
echo "[db] local=$LOCAL_OK provider=$PROV_OK"
[ "$LOCAL_OK" = "2" ] || { echo "[fail] import did not restore local DB"; exit 1; }
[ "$PROV_OK" = "2" ] || { echo "[fail] provider NOT repopulated after import"; exit 1; }
ADDRS=$(adb_ shell "sqlite3 $PROVDB \"SELECT GROUP_CONCAT(address,',') FROM sms;\"" 2>/dev/null)
[[ "$ADDRS" == *"$ADDR"* ]] || { echo "[fail] provider addresses unexpected: $ADDRS"; exit 1; }
adb_ logcat -d 2>/dev/null | grep -E "RepoMirror: push: attempted=2 linked=2" | tail -1 >/dev/null \
  && echo "[ok] RepoMirror mirrored both rows" \
  || echo "[warn] RepoMirror log line not found (check attempted/linked counts)"

# 6. Other apps must see the restored history.
echo "== Step 6: sibling-app visibility =="
adb_ shell am start -n com.android.messaging/.ui.app.MainActivity >/dev/null 2>&1 || \
  adb_ shell monkey -p com.android.messaging -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 5; dump_ui
if greps_ui 'Mirror test message two'; then
  echo "[ok] default Messaging shows the restored thread"
else
  echo "[fail] default Messaging does not show the restored thread"
fi
adb_ shell monkey -p com.github.tmo1.sms_ie -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
  { echo "[skip] SMS Import/Export not installed"; exit 0; }
sleep 5; dump_ui
b=$(bounds_of 'EXPORT MESSAGES'); [ -z "$b" ] && { echo "[fail] SMS IE UI not found"; exit 1; }
tap_center "$b"; sleep 4; dump_ui
b=$(bounds_of 'SAVE'); [ -z "$b" ] && b="[812,2148][1043,2274]"
tap_center "$b"; sleep 6; dump_ui
if greps_ui '2 SMS(s) and 0 MMS(s) exported'; then
  echo "[ok] SMS Import/Export exports the 2 restored messages"
else
  echo "[fail] SMS IE export count unexpected (regression: it would say 0 SMS(s))"
  exit 1
fi

echo "[done] import → provider mirror OK"