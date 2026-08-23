#!/usr/bin/env bash
# Test: privacy mode (notification content hiding) + app lock
# Requires: adb, emulator, SMS permissions granted
# Usage: bash scripts/test-privacy-features.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/env.sh"

PASSED=0; FAILED=0; TOTAL=0

ok() { TOTAL=$((TOTAL+1)); PASSED=$((PASSED+1)); echo "  ✅ $1"; }
fail() { TOTAL=$((TOTAL+1)); FAILED=$((FAILED+1)); echo "  ❌ $1"; }

get_privacy_pref() {
    adb_ shell run-as "$PKG" cat /data/data/$PKG/shared_prefs/messages_settings.xml 2>&1 | grep 'privacy_mode' | sed 's/.*value="\([^"]*\)".*/\1/'
}

scroll_until() {
    local target="$1"
    for i in $(seq 1 10); do
        dump_ui >/dev/null
        grep -q "\"$target\"" "$TMP/ui.xml" && return 0
        adb_ shell input swipe 540 1800 540 800 300; sleep 0.3
    done
    return 1
}

echo "=== Privacy Features Test ==="
echo ""

# --- Test 1: App lock toggle in settings ---
echo "1. App lock toggle in settings"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input tap 976 222; sleep 2
dump_ui >/dev/null
if grep -q '"Notifications"' "$TMP/ui.xml"; then
    if scroll_until "App lock"; then ok "App lock toggle visible"; else fail "App lock toggle not found"; fi
else
    fail "Settings screen not opened"
fi

# --- Test 2: Enable privacy mode + verify persistence ---
echo "2. Enable privacy mode"
adb_ shell am force-stop "$PKG"; sleep 1
CURRENT=$(get_privacy_pref)
if [ "$CURRENT" = "true" ]; then
    ok "Privacy mode already enabled"
else
    adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
    adb_ shell input tap 976 222; sleep 2
    scroll_until "Privacy mode" >/dev/null
    tap_switch_near "Privacy mode"; sleep 1
    adb_ shell am force-stop "$PKG"; sleep 1
    NEW=$(get_privacy_pref)
    if [ "$NEW" = "true" ]; then ok "Privacy mode enabled"; else fail "Privacy mode not persisted (got: $NEW)"; fi
fi

# --- Test 3: Notification hides sender and content ---
echo "3. Notification content hiding"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
"$SCRIPT_DIR/receive-sms.sh" +15551230010 "Your OTP is 847291" 2>&1 | tail -1
sleep 4
adb_ shell input swipe 540 10 540 500 400; sleep 2.5
dump_ui >/dev/null

if grep -q '"New message"' "$TMP/ui.xml"; then
    ok "Notification shows 'New message' placeholder"
else
    fail "Notification missing 'New message' placeholder"
fi
if grep -q '"Your OTP is 847291"' "$TMP/ui.xml"; then
    fail "Notification still shows message body"
else
    ok "Message body hidden from notification"
fi

# --- Test 4: App opens on emulator (graceful biometric fallback) ---
echo "4. App lock on launch"
adb_ shell input keyevent 4; sleep 0.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3
dump_ui >/dev/null
if grep -q '"Messages"' "$TMP/ui.xml" || grep -q '"Gym Buddy"' "$TMP/ui.xml" || grep -q '"Sarah"' "$TMP/ui.xml"; then
    ok "App opens (graceful fallback on emulator without biometric)"
else
    fail "App failed to open after app lock check"
fi

# --- Summary ---
echo ""
echo "Results: $PASSED/$TOTAL passed"
[ $FAILED -gt 0 ] && { echo "FAILED"; exit 1; } || echo "ALL PASSED"
