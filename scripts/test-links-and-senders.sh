#!/usr/bin/env bash
# Functional checks for: clickable links in messages, "Highlight links"
# setting toggle, blocking sends to alphanumeric sender IDs, and that
# numeric sends still work. Uses uiautomator dumps (no visual checks).
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

# Center "X Y" of the first UI node whose text contains $1
center_of_contains() {
    local query="$1" b i x1 y1 x2 y2
    for i in 1 2 3; do
        dump_ui || { sleep 1; continue; }
        b=$(grep -oE "text=\"[^\"]*$query[^\"]*\"[^>]*bounds=\"[^\"]*\"" \
                "$TMP/ui.xml" 2>/dev/null | head -1 \
            | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
        if [ -n "$b" ]; then
            x1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1/' <<< "$b")
            y1=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2/' <<< "$b")
            x2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\3/' <<< "$b")
            y2=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\4/' <<< "$b")
            echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
            return 0
        fi
        sleep 1
    done
    return 1
}

info "Inject test SMS"
adb_ emu sms send +15551230001 "https://example.com" >/dev/null 2>&1; sleep 2
adb_ emu sms send DK-TEST99 "Your OTP is 443322" >/dev/null 2>&1; sleep 2

info "Fresh launch"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3

info "1. Open the URL conversation"
C=$(center_of_contains "555-123-0001") || { fail "URL row not on home list"; exit 1; }
adb_ shell input tap $C; sleep 2

info "2. Tap the link -> external handler must open"
C=$(center_of_contains "example.com") || { fail "link not rendered in chat"; exit 1; }
adb_ shell input tap $C; sleep 3
TOP=$(adb_ shell dumpsys activity activities 2>/dev/null | grep -i "ResumedActivity" | head -1)
echo "top activity: $TOP"
if echo "$TOP" | grep -q "$PKG"; then
    fail "browser did not open"
else
    pass "external handler opened"
fi
adb_ shell input keyevent 4; sleep 1
adb_ shell input keyevent 4; sleep 1

info "3. Settings shows 'Highlight links' toggle"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 3
FOUND=0
for i in 1 2 3 4; do
    if dump_ui && grep -q "Highlight links" "$TMP/ui.xml"; then FOUND=1; break; fi
    adb_ shell input swipe 540 1800 540 600 300; sleep 1.5
done
[ "$FOUND" = "1" ] && pass "'Highlight links' row present" || fail "toggle missing"

info "4. Numeric conversation still sends"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3
C=$(center_of_contains "555-123-0001") || { fail "URL row missing"; exit 1; }
adb_ shell input tap $C; sleep 2
tap_edittext; sleep 1
adb_ shell 'input keyevent 123; for i in $(seq 1 60); do input keyevent 67; done'; sleep 0.5
type_text "regression check"; sleep 0.5
tap_text "Send"; sleep 2.5
if dump_ui && grep -qE 'text="regression check"' "$TMP/ui.xml" && ! grep -q 'Draft' "$TMP/ui.xml"; then
    pass "numeric send works"
else
    fail "numeric send broken"
fi
adb_ shell input keyevent 4; sleep 1

info "5. Alphanumeric conversation blocks sending"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3
C=$(center_of_contains "443322") || C=$(center_of_contains '"99"') || C=$(center_of_contains "99") || { cp "$TMP/ui.xml" /tmp/opencode/step5-fail.xml; fail "OTP row not found"; exit 1; }
adb_ shell input tap $C; sleep 2
tap_edittext; sleep 1
adb_ shell 'input keyevent 123; for i in $(seq 1 60); do input keyevent 67; done'; sleep 0.5
type_text "hello"; sleep 0.5
tap_text "Send"; sleep 2
if dump_ui && grep -q "Can.t send message" "$TMP/ui.xml"; then
    pass "alphanumeric send blocked with dialog"
else
    fail "no block dialog"
fi
adb_ shell input keyevent 4; sleep 0.5
adb_ shell input keyevent 4; sleep 1

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL