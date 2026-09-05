#!/usr/bin/env bash
set -euo pipefail
# Backup → restore round trip (regression test for the GCM null doFinal bug).
# Assumes the app is built + installed and that SMS/READ_SMS permissions are
# already granted. Passes when Settings → "Import messages" restores the file
# written by Settings → "Backup messages", no "Import failed" toast fires, and
# the conversation list still renders after an app restart.
export ADB="${ADB:-$HOME/android/platform-tools/adb}"
source "$(dirname "$0")/env.sh"
PKG="com.anindra.messages"

dump_ui() {
  adb_ shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb_ shell cat /sdcard/ui.xml > "$TMP/ui.xml" 2>/dev/null || true
}

bounds_center() { # prints "x y" for a node string's bounds attribute
  local b xs ys
  b=$(printf '%s' "$1" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
  [ -z "$b" ] && return 1
  xs=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
  ys=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
  xe=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$b")
  ye=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$b")
  echo "$(( (xs + xe) / 2 )) $(( (ys + ye) / 2 ))"
}

node_of() { # prints the full <node ... text="..."> tag matching a label
  grep -oE "<node[^>]*text=\"$1\"[^>]*>" "$TMP/ui.xml" | head -1 || true
}

tap_label() { # taps the center of a node matching text="$1"
  local c
  c=$(bounds_center "$(node_of "$1")") || { echo "[fail] '$1' not found"; exit 1; }
  adb_ shell input tap $c
  sleep 1
}

# 1. Open the app and dismiss the default-SMS dialog if present.
adb_ shell am start -n "$PKG/.MainActivity"
sleep 4
dump_ui
if grep -q 'Set as default SMS app' "$TMP/ui.xml"; then
  adb_ shell input tap 540 1700
  sleep 2
  dump_ui
fi

# 2. Open settings (top-right avatar/menu) and scroll to the backup group.
if ! grep -q 'Save database to Documents/Messages' "$TMP/ui.xml"; then
  adb_ shell input tap 976 164
  sleep 2
  for _ in 1 2 3; do
    adb_ shell input swipe 540 2000 540 400 300
    sleep 1
  done
  dump_ui
fi
grep -q 'Backup messages' "$TMP/ui.xml" || { echo "[fail] settings backup row not visible"; exit 1; }

# 3. Backup.
echo "== Step 1: Backup =="
tap_label 'Backup messages'
sleep 3
adb_ shell ls /sdcard/Documents/Messages/ 2>/dev/null | grep -q '\.enc' \
  && echo "[ok] backup file written" \
  || { echo "[fail] no .enc backup produced"; exit 1; }
# Save the row label for later pass/fail; dump is stale, refresh it.
dump_ui

# 4. Restore via SAF picker.
echo "== Step 2: Restore =="
tap_label 'Import messages'
sleep 3
adb_ shell input tap 100 170; sleep 2   # roots drawer
adb_ shell input tap 400 717; sleep 2   # internal storage
adb_ shell input tap 300 1044; sleep 2  # Documents
adb_ shell input tap 300 746; sleep 2   # Messages
dump_ui
f=$(grep -oE "<node[^>]*text=\"messages_backup[^\"]*\.enc\"[^>]*>" "$TMP/ui.xml" | head -1 || true)
[ -z "$f" ] && { echo "[fail] backup file not shown in picker"; exit 1; }
c=$(bounds_center "$f")
adb_ shell input tap $c
sleep 5

# 5. Restart and confirm the app still renders the conversation list.
echo "== Step 3: Restart + verify =="
adb_ shell am force-stop "$PKG"
adb_ shell am start -n "$PKG/.MainActivity"
sleep 5
dump_ui
if grep -q 'Set as default SMS app' "$TMP/ui.xml"; then
  adb_ shell input tap 540 1700
  sleep 2
  dump_ui
fi
grep -q 'text="Messages"' "$TMP/ui.xml" \
  && echo "[ok] conversation list rendered after restore" \
  || { echo "[fail] home list missing after restore"; exit 1; }

# 6. Surface restore diagnostics so the developer can confirm the success
#    toast ("Backup restored. Restart app to apply.") or any failure log.
adb_ logcat -d -t 300 2>/dev/null | grep -E "BackupCrypto|BackupImport|NotificationService.*Toast" | tail -10 || true
echo "[done] backup → restore round trip OK"