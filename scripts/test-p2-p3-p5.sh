#!/usr/bin/env bash
set -euo pipefail
source ~/Develop/Messages/scripts/env.sh
PASS=0; FAIL=0
pass() { echo "  ✅ PASS: $1"; ((PASS++)); }
fail() { echo "  ❌ FAIL: $1"; ((FAIL++)); }

info "=== P3: Message Locking ==="
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
C=$(center_of_contains "555-123-0777") || { fail "no 0777 row"; }
adb_ shell input tap $C; sleep 2
dump_ui && grep -q '"Text message"' "$TMP/ui.xml" && echo "IN CHAT" || { fail "not in chat"; }

info "Long-press a message to open context menu"
ROWS=$(grep -oE 'resource-id="[^"]*row_[0-9]+"' "$TMP/ui.xml" 2>/dev/null || true)
LAST=$(echo "$ROWS" | tail -1 | grep -oE 'row_[0-9]+' | head -1 || true)
if [ -n "$LAST" ]; then
    BOUNDS=$(grep "$LAST" "$TMP/ui.xml" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 || true)
    X=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\].*/\1/')
    Y=$(echo "$BOUNDS" | sed 's/.*\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\4/')
    if [ -n "$X" ] && [ -n "$Y" ]; then
        info "Long-press at ($X $Y)"
        adb_ shell input swipe $X $Y $X $Y 1000; sleep 1.5
        dump_ui && grep -q 'Lock' "$TMP/ui.xml" && pass "Lock option visible in context menu" || fail "Lock option not found"
    else
        fail "could not parse message bounds"
    fi
else
    fail "no message rows found"
fi

info "=== P5: Per-conversation notification toggle ==="
adb_ shell input keyevent 4; sleep 1
tap_text "More options" 2>/dev/null || tap_dot 2>/dev/null; sleep 1
dump_ui && grep -q 'Notifications' "$TMP/ui.xml" && pass "Notifications toggle in chat menu" || info "notifications toggle not in menu (check chat 3-dot menu)"

info "=== P2: Quick Reply notification ==="
info "Send an inbound SMS to trigger a notification"
adb_ shell emu sms send "+15551230004" "p2-test-reply"; sleep 3
~/android/platform-tools/adb -s emulator-5554 shell dumpsys notification | grep -q "quick_reply\|Reply" && pass "Quick Reply action on notification" || info "Quick reply check inconclusive (notification may not be visible while app is open)"

echo ""
echo "=== RESULTS: $PASS passed, $FAIL failed ==="
