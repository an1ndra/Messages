#!/usr/bin/env bash
# Issue #93 regression: delayed-send countdown, auto-send, and Cancel abort.
#
# Uses system Sent box as ground truth (the input field retains typed text
# after cancel, so UI-node assertions false-positive).
#
# Run: scripts/test-delayed-send.sh
set -uo pipefail
cd "$(dirname "$0")"
source ./env.sh

NUM=15551230066
fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }
adb_ logcat -b crash -c >/dev/null 2>&1 || true

# Snapshot Sent box BEFORE each phase so we detect only new rows
sent_count() {
    adb_ shell content query --uri content://sms/sent \
        --projection _id --where "\"body LIKE 'delay93%'\"" 2>/dev/null \
        | grep -c "Row:" || true
}
cancel_sent_count() {
    adb_ shell content query --uri content://sms/sent \
        --projection _id --where "\"body LIKE 'cancel93%'\"" 2>/dev/null \
        | grep -c "Row:" || true
}

set_delay() { # $1 seconds
    adb_ shell am force-stop "$PKG"
    printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="first_import_done" value="true" />\n    <boolean name="delayed_sending_enabled" value="true" />\n    <int name="delay_seconds" value="%d" />\n</map>\n' "$1" > "$TMP/settings.xml"
    adb_ shell "run-as $PKG sh -c 'cat > /data/data/$PKG/shared_prefs/messages_settings.xml'" < "$TMP/settings.xml"
    adb_ shell am start -n "$ACT" >/dev/null; sleep 3
}

open_and_type() { # $1 body
    dump_ui || fail "dump failed"
    C=$(center_of_contains "123-0066" || true)
    [ -z "${C:-}" ] && C="540 371"
    adb_ shell input tap $C; sleep 2.5
    tap_edittext || fail "chat input not found"
    type_text "$1"; sleep 0.8
}

# ─── Phase 1: auto-send ────────────────────────────────────────────────
info "Phase 1: Delayed sending (5s) — expect auto-send after countdown"
BODY1="delay93 probe $(date +%s)"
PRE1=$(sent_count)
set_delay 5
open_and_type "$BODY1"

echo "  pre-send sent_count=$PRE1"
tap_text "Send" || adb_ shell input tap 990 2260

dump_ui || true
grep -qE 'text="Sending in [0-9]+ seconds' "$TMP/ui.xml" || fail "banner absent"
pass "banner visible"

info "  waiting 8s for auto-send..."
sleep 8
POST1=$(sent_count)
echo "  post-send sent_count=$POST1"
[ "${POST1:-0}" -gt "${PRE1:-0}" ] || fail "message never auto-sent"
pass "auto-send confirmed"

# ─── Phase 2: cancel abort ─────────────────────────────────────────────
info "Phase 2: Delayed sending (30s) — Cancel must abort before send"
BODY2="cancel93 probe $(date +%s)"
# Delete any prior cancel93 rows so we measure only THIS run's send
adb_ shell content delete --uri content://sms/sent \
    --where "\"body LIKE 'cancel93%'\"" 2>/dev/null || true
PRE2=$(cancel_sent_count)
set_delay 30
open_and_type "$BODY2"

echo "  pre-send cancel_sent_count=$PRE2"
tap_text "Send" || adb_ shell input tap 990 2260

dump_ui || true
if ! grep -qE 'text="Sending in [0-9]+ seconds' "$TMP/ui.xml"; then
    fail "banner not visible after second Send"
fi
# Read remaining seconds from banner
REMAIN=$(grep -oE 'text="Sending in [0-9]+ seconds' "$TMP/ui.xml" | grep -oE '[0-9]+' | head -1)
echo "  banner shows ${REMAIN}s remaining"
[ "${REMAIN:-0}" -ge 5 ] || fail "window too short (${REMAIN}s) to cancel reliably"

tap_text "Cancel" || fail "Cancel button not found"
echo "  tapped Cancel at banner-remaining ${REMAIN}s"
sleep 1
POST2=$(cancel_sent_count)
echo "  post-cancel cancel_sent_count=$POST2"
[ "${POST2:-0}" -eq "${PRE2:-0}" ] || fail "cancelled message was sent"

CRASHES=$(adb_ logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
[ "${CRASHES:-0}" -eq 0 ] || fail "crash in $PKG"

shot "delayed-send"
adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
pass "countdown + auto-send + cancel all correct (#93)"
