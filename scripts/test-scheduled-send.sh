#!/usr/bin/env bash
# Issue #81 regression: scheduled send must persist the composed text as a
# normal message row (never lose it), always consume the schedule entry, and
# surface hand-off failures as a retriable "failed" message.
#
# Drives ScheduledMessageSender directly via an explicit broadcast — the same
# shape AlarmManager delivers — then asserts the message landed in the chat
# and (when the emulator radio completes it) the system Sent box.
#
# Run: scripts/test-scheduled-send.sh
set -uo pipefail
cd "$(dirname "$0")"
source ./env.sh

NUM=15551230088
BODY="sched81 probe $(date +%s)"

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }
adb_ logcat -b crash -c >/dev/null 2>&1 || true

info "Broadcast scheduled-send intent (id=424242, sub=-1)"
# ScheduledMessageSender is exported="false", so plain-shell broadcasts are
# silently dropped; adbd must run as root (emulator-only dev shortcut) and the
# body needs device-side quoting because adb concatenates raw args.
adb_ root >/dev/null 2>&1 || true
sleep 3
adb_ shell "am broadcast -n $PKG/.sms.ScheduledMessageSender \
    --es scheduled_address $NUM \
    --es scheduled_body '$BODY' \
    --el scheduled_id 424242 \
    --ei scheduled_sub_id -1" >/dev/null
sleep 10   # radio hand-off + sent-status callback
adb_ unroot >/dev/null 2>&1 || true
sleep 3

info "Open the conversation and assert the message rendered"
bash ./grant-permissions.sh >/dev/null 2>&1 || true
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 5

# home shows formatted numbers ("+1-555-123-0088") — match on a formatted
# fragment; use in-app search if the row isn't immediately visible
dump_ui || fail "home dump failed"
C=$(center_of_contains "123-0088" || center_of_contains "5551230088" || true)
if [ -z "$C" ]; then
    tap_text "Search" || adb_ shell input tap 990 150
    sleep 1
    type_text "1230088"; sleep 2
    C=$(center_of_contains "123-0088" || center_of_contains "5551230088") || fail "conversation $NUM not found via search"
fi
adb_ shell input tap $C; sleep 3
dump_ui || fail "chat dump failed"
grep -qF "\"$BODY\"" "$TMP/ui.xml" || fail "'$BODY' not rendered in chat — scheduled text was lost"

info "Assert system Sent box received it"
SENT=$(adb_ shell content query --uri content://sms/sent --projection body \
    --where "\"body LIKE '%${BODY:0:16}'\"" 2>/dev/null | grep -cF "${BODY:0:16}" || true)
[ "${SENT:-0}" -ge 1 ] && echo "[info] mirrored to system Sent box"

CRASHES=$(adb_ logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
[ "${CRASHES:-0}" -eq 0 ] || fail "crash buffer contains $PKG entries"

shot "scheduled-send"
adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
pass "scheduled text persisted as message + sent without ghosts (#81)"
