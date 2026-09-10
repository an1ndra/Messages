#!/usr/bin/env bash
# Test: link warning dialog when tapping a link in a received message.
# Usage: test-link-warning.sh
#
# Prerequisites: at least one conversation exists in the app.
# The test sends an inbound SMS with a URL, then taps the link to verify
# the warning dialog appears.
source "$(dirname "$0")/env.sh"

LINK_MSG="Check this link: https://example.com/malicious"
TARGET_NUMBER="${1:-+16505551212}"

info "1. Inject inbound SMS with link from $TARGET_NUMBER"
"$ADB" -s "$ANDROID_SERIAL" emu sms send "$TARGET_NUMBER" "$LINK_MSG"
sleep 3

info "2. Launch app"
adb_ shell am start -n "$ACT"; sleep 2
shot "link-01-home"

info "3. Tap first conversation"
adb_ shell input tap 500 357; sleep 2
shot "link-02-chat"

info "4. Find and tap the link in the message"
dump_ui
LINK_BOUNDS=$(grep -oE 'text="[^"]*example\.com[^"]*"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)

if [ -n "$LINK_BOUNDS" ]; then
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$LINK_BOUNDS")
    y1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$LINK_BOUNDS")
    x2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$LINK_BOUNDS")
    y2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$LINK_BOUNDS")
    LINK_X=$(( (x1 + x2) / 2 ))
    LINK_Y=$(( (y1 + y2) / 2 ))
    info "Link found at ($LINK_X, $LINK_Y)"
    adb_ shell input tap "$LINK_X" "$LINK_Y"; sleep 2
else
    info "Link bounds not found, trying text match"
    tap_text "example.com"; sleep 2
fi
shot "link-03-warning-dialog"

info "5. Verify warning dialog"
dump_ui
if grep -q 'Link warning' "$TMP/ui.xml" 2>/dev/null; then
    echo "[PASS] Link warning dialog is displayed"
else
    echo "[FAIL] Link warning dialog NOT found"
    info "UI dump contents:"
    cat "$TMP/ui.xml" | grep -oP 'text="[^"]*"' | head -20
fi

info "6. Test Cancel button"
tap_text "Cancel"; sleep 1
shot "link-04-cancelled"

dump_ui
if grep -q 'example.com' "$TMP/ui.xml" 2>/dev/null && ! grep -q 'Link warning' "$TMP/ui.xml" 2>/dev/null; then
    echo "[PASS] Dialog dismissed, back in chat"
else
    echo "[INFO] Check screenshot for state"
fi

info "7. Tap link again, test Open button"
if [ -n "$LINK_BOUNDS" ]; then
    adb_ shell input tap "$LINK_X" "$LINK_Y"; sleep 2
else
    tap_text "example.com"; sleep 2
fi
shot "link-05-warning-again"

tap_text "Open"; sleep 2
shot "link-06-browser-opened"

TOP_ACTIVITY=$("$ADB" -s "$ANDROID_SERIAL" shell dumpsys activity activities 2>/dev/null | grep "topResumedActivity" | head -1)
if echo "$TOP_ACTIVITY" | grep -qv "anindra.messages"; then
    echo "[PASS] Browser opened after tapping Open"
else
    echo "[FAIL] Browser did not open"
fi

info "8. Return to app"
"$ADB" -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_BACK; sleep 1
"$ADB" -s "$ANDROID_SERIAL" shell am start -n "$ACT"; sleep 1
shot "link-07-back-to-app"

info "Test complete. Screenshots in: $SHOTS_DIR"
