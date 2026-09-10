#!/usr/bin/env bash
# Test: keyboard Enter inserts newline (not send) in chat compose field.
# Usage: test-newline-input.sh
source "$(dirname "$0")/env.sh"

info "1. Launch app"
adb_ shell am start -n "$ACT"; sleep 2
shot "newline-01-home"

ROW=1
Y=$(( 357 + (ROW - 1) * 190 ))
[ "$Y" -gt 2200 ] && Y=2200
info "2. Tap conversation row $ROW at y=$Y"
adb_ shell input tap 500 "$Y"; sleep 1.5
shot "newline-02-chat-open"

info "3. Focus text field"
tap_edittext; sleep 1

info "4. Type first line: Hello"
type_text "Hello"; sleep 0.5
shot "newline-03-first-line"

info "5. Press Enter key (keyevent 66) — should insert newline, NOT send"
adb_ shell input keyevent 66; sleep 0.5
shot "newline-04-after-enter"

info "6. Type second line: World"
type_text "World"; sleep 0.5
shot "newline-05-second-line"

info "7. Verify text field has multi-line content via UI dump"
dump_ui
if grep -q 'Hello' "$TMP/ui.xml" && grep -q 'World' "$TMP/ui.xml"; then
    echo "[PASS] Both 'Hello' and 'World' found in text field"
else
    echo "[FAIL] Multi-line text not found — Enter may have sent the message"
    info "Checking if message was sent..."
    if grep -q 'Hello' "$TMP/ui.xml" && ! grep -q 'Text message' "$TMP/ui.xml"; then
        echo "[FAIL] Confirmed: Enter sent the message instead of inserting newline"
    fi
fi

info "8. Tap Send button to send the multi-line message"
tap_text "Send"; sleep 1.5
shot "newline-06-sent"

info "9. Go back to list"
adb_ shell input keyevent 4; sleep 1
shot "newline-07-back-to-list"

info "Test complete. Screenshots in: $SHOTS_DIR"
