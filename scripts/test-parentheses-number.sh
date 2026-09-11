#!/usr/bin/env bash
# Verify numbers with parenthesized area codes, e.g. (555) 555-0999, are
# accepted (issue #176) while true alphanumeric senders stay blocked.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
check_present() {
    if grep -q "$2" "$TMP/ui.xml"; then
        echo "[PASS] $1"; PASS=$((PASS + 1))
    else
        echo "[FAIL] $1"; FAIL=$((FAIL + 1))
    fi
}
check_absent() {
    if grep -q "$2" "$TMP/ui.xml"; then
        echo "[FAIL] $1"; FAIL=$((FAIL + 1))
    else
        echo "[PASS] $1"; PASS=$((PASS + 1))
    fi
}
# input text is re-parsed by the device shell, so wrap parens in quotes.
type_safe() {
    local t="${1// /%s}"
    adb_ shell "input text \"$t\""
}
clear_field() {
    for _ in $(seq 1 32); do adb_ shell input keyevent 67; done
    sleep 0.5
}
# Tap the row whose text contains $1 (radio the forever-missing content-desc).
tap_contains() {
    local c
    c=$(center_of_contains "$1") || return 1
    adb_ shell input tap $c
}
# Tap a node by content-desc (e.g. Send button) after the latest dump.
tap_desc() {
    local b
    b=$(grep -oE "content-desc=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
            "$TMP/ui.xml" 2>/dev/null | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && return 1
    local x1 y1 x2 y2
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
    y1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\2/' <<< "$b")
    x2=$(sed -E 's/.*\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
    y2=$(sed -E 's/.*\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
    adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
# Tap the chat input EditText (bounds change while the IME is open).
tap_edittext_dump() {
    local b
    b=$(grep -oE 'class="android.widget.EditText"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
            "$TMP/ui.xml" 2>/dev/null | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && return 1
    local x1 y1 x2 y2
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
    y1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\2/' <<< "$b")
    x2=$(sed -E 's/.*\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
    y2=$(sed -E 's/.*\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
    adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}

info "Launching app"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT"; sleep 3

info "Tapping 'Start chat' FAB"
tap_text "Start chat" || adb_ shell input tap 862 2158
sleep 2

info "Entering parenthesized number: (555) 555-0999"
adb_ shell input tap 540 340; sleep 0.8
type_safe "(555) 555-0999"; sleep 1
dump_ui
check_absent "parenthesized number accepted (no error)" "Only phone numbers can be messaged"
check_present "manual-send entry shown" "Send to"

for SENDER in "VM-HDFCBK" "AD-KOTAKB-S" "JE-JioPay-S"; do
    info "Switching to alphanumeric sender: $SENDER"
    clear_field
    type_safe "$SENDER"; sleep 1
    dump_ui
    check_present "alphanumeric sender '$SENDER' still rejected" "Only phone numbers can be messaged"
done

info "Re-entering parenthesized number and opening chat"
clear_field
type_safe "(555) 555-0999"; sleep 1
tap_contains "Send to" || adb_ shell input tap 500 460
sleep 2.5
dump_ui

info "Typing message into chat input"
tap_edittext_dump || adb_ shell input tap 529 2179
sleep 1
type_safe "Parentheses send test"; sleep 0.8
dump_ui

info "Tapping Send button"
tap_desc "Send" || adb_ shell input tap 985 1342
sleep 3
dump_ui

info "Asserting send succeeded"
check_absent "alphanumeric dialog NOT shown" "Can't send message"
grep -qE 'text="Parentheses send test&#10;"[^>]*class="android.widget.EditText"' "$TMP/ui.xml" && {
    echo "[FAIL] message still in input"; FAIL=$((FAIL + 1)); } || {
    echo "[PASS] input cleared after send"; PASS=$((PASS + 1)); }
grep -qE 'text="Parentheses send test[^"#]*"[^>]*class="android.widget.TextView"' "$TMP/ui.xml" && {
    echo "[PASS] message bubble present"; PASS=$((PASS + 1)); } || {
    echo "[FAIL] message bubble present"; FAIL=$((FAIL + 1)); }

info "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]