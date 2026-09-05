#!/usr/bin/env bash
set -euo pipefail
# Backup → restore round trip (regression test for PIN-protected backups).
# Assumes the app is built + installed and SMS permissions are already granted.
# Flow (drive matches the current Settings UI):
#   Home → avatar (top-right) → Settings → scroll to "Backup messages" →
#   tap → "Set backup PIN" dialog: enter the test PIN twice → Save →
#   a new .enc lands in Documents/Messages → record the live DB mtime →
#   "Import messages" (SAF picker) → pick the newest .enc →
#   "Enter backup PIN" dialog: enter the PIN → Import →
#   confirm the live DB file was swapped (mtime changed) → restart →
#   conversation list renders.
# PIN="${PIN:-1234}" overrides the test PIN.
export ADB="${ADB:-$HOME/android/platform-tools/adb}"
source "$(dirname "$0")/env.sh"

dump_ui() {
  adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb_ shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
}
bounds_of() { # prints bounds of first node whose text==="$1"
  grep -oE "<node[^>]*text=\"$1\"[^>]*bounds=\"[^\"]*\"" "$TMP/ui.xml" | head -1 \
    | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
first_enc_bounds() { # prints bounds of first picker row whose text starts with messages_backup_
  grep -oE '<node[^>]*text="messages_backup_[^"]*\.enc"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
tap_center() { # taps center of a bounds string
  local b=$1 x1 y1 x2 y2
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  x2=$(sed -E 's/.*\]\[([0-9]+),[0-9]+\]/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
tap_label() { # taps center of first node with exact text="$1"
  local b
  b=$(bounds_of "$1") || true
  [ -z "$b" ] && { echo "[fail] '$1' not found on screen"; return 1; }
  tap_center "$b"
}
edits() { # bounds of every EditText node (screen order), one per line
  grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' || true
}
nth_edit() { edits | sed -n "${1}p"; }
greps_ui() { grep -q "$1" "$TMP/ui.xml"; }

# 1. Open the app, dismiss any first-run dialogs.
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

# 2. Settings via the top-right avatar (no content-desc, fixed coords).
adb_ shell input tap 975 226
sleep 2
# 3. Scroll until the General/backup group is in view.
for _ in 1 2 3 4; do
  if greps_ui 'Backup messages'; then break; fi
  adb_ shell input swipe 540 1900 540 400 300
  sleep 1
  dump_ui
done
grep -q 'Backup messages' "$TMP/ui.xml" || { echo "[fail] Settings backup rows not visible"; exit 1; }

# 4. Backup → "Set backup PIN" dialog (two fields + Save) → new .enc in Documents/Messages.
echo "== Step 1: Backup =="
BACKDIR="storage/emulated/0/Documents/Messages"
PIN="${PIN:-1234}"
before=$(adb_ shell "ls $BACKDIR | grep -c '\.enc'" 2>/dev/null | tr -dc '0-9')
tap_center "$(bounds_of 'Backup messages')"
sleep 3
dump_ui
greps_ui 'Set backup PIN' || { echo "[fail] Set backup PIN dialog not shown"; exit 1; }
[ -z "$(nth_edit 1)" ] && { echo "[fail] PIN field 1 missing"; exit 1; }
tap_center "$(nth_edit 1)"
adb_ shell input text "$PIN"
sleep 1
dump_ui # the IME shifts the dialog → re-resolve field 2
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
  && echo "[ok] PIN-protected backup written ($before -> $after .enc)" \
  || { echo "[fail] no new .enc backup produced (was $before, now $after)"; exit 1; }

# 5. Record the live DB mtime BEFORE the swap (epoch seconds — minute
#    granularity tools can't resolve swaps inside the same minute).
dbmtime() { adb_ shell "run-as $PKG toybox stat -c %Y databases/messages.db" 2>/dev/null | tr -dc '0-9'; }
MT_BEFORE=$(dbmtime)
echo "[db] pre-import mtime: $MT_BEFORE"

# 6. Import → SAF picker (opens inside Documents/Messages). Target the
#    NEWEST .enc by filename (the one we just wrote in Step 1) — the picker's
#    "first visible row" is the OLDEST file, which may belong to a different
#    (lost) keystore key and would legitimately fail to decrypt.
echo "== Step 2: Restore =="
NEWEST=$(adb_ shell "ls storage/emulated/0/Documents/Messages/messages_backup_*.enc 2>/dev/null" \
  | xargs -n1 basename 2>/dev/null | sort | tail -1)
echo "[ok] newest backup: $NEWEST"
tap_center "$(bounds_of 'Import messages')"
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

# 6b. PIN-format backup → "Enter backup PIN" dialog → type PIN → Import.
dump_ui
greps_ui 'Enter backup PIN' || { echo "[fail] Enter backup PIN dialog not shown"; exit 1; }
[ -z "$(nth_edit 1)" ] && { echo "[fail] PIN entry field missing"; exit 1; }
tap_center "$(nth_edit 1)"
adb_ shell input text "$PIN"
sleep 1
dump_ui # re-resolve Import after IME shift
b=$(bounds_of 'Import'); [ -z "$b" ] && { echo "[fail] Import button not found"; exit 1; }
tap_center "$b"
sleep 8

# 7. The live DB must have been swapped (new inode → mtime changes).
MT_AFTER=$(dbmtime)
echo "[db] post-import mtime: $MT_AFTER"
[ "$MT_AFTER" != "$MT_BEFORE" ] \
  && echo "[ok] live DB file replaced by import" \
  || { echo "[fail] live DB file unchanged after import"; exit 1; }

# 8. Restart and confirm the conversation list still renders.
echo "== Step 3: Restart + verify =="
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -n "$PKG/.MainActivity"
sleep 5
dump_ui
for _ in 1 2 3; do
  if greps_ui 'Set as default SMS app?'; then
    adb_ shell input tap 533 1352; sleep 2; dump_ui
  else break; fi
done
grep -q 'text="Messages"' "$TMP/ui.xml" \
  && echo "[ok] conversation list rendered after restore" \
  || { echo "[fail] home list missing after restore"; exit 1; }

# 9. Surface restore diagnostics (toast/logcat) for the developer.
adb_ logcat -d -t 300 2>/dev/null | grep -iE "BackupCrypto|ImportResult|Import failed" | tail -5 || true
echo "[done] backup → restore round trip OK"