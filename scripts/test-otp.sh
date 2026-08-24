#!/usr/bin/env bash
# OTP detection smoke test: injects crafted messages (true positives,
# false-positive bait, grouped digits) and verifies each lands in the chat
# with its text rendered. Span styling itself is covered by JUnit
# (OtpDetectorTest); this script checks end-to-end delivery + rendering.
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

info "Inject OTP test messages"
adb_ emu sms send +15551230004 "Your OTP is 482913. Do not share." >/dev/null 2>&1; sleep 2
adb_ emu sms send +15551230002 "Paid Rs 12500 to merchant" >/dev/null 2>&1; sleep 2
adb_ emu sms send +15551230005 "OTP: 482 913 valid 10 min" >/dev/null 2>&1; sleep 2
adb_ emu sms send +15551230003 "Order 48291 shipped today" >/dev/null 2>&1; sleep 2
adb_ emu sms send G-VERIFY "752913 is your Google verification code" >/dev/null 2>&1; sleep 2

info "Fresh launch"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3

check_row() {
    local query="$1" label="$2"
    if C=$(center_of_contains "$query"); then
        adb_ shell input tap $C; sleep 2
        if dump_ui && grep -qF "$query" "$TMP/ui.xml"; then
            pass "$label rendered in chat"
        else
            fail "$label missing from chat"
        fi
        adb_ shell input keyevent 4; sleep 1.5
    else
        fail "$label row not on home list"
    fi
}

info "1. Keyword-before message"
check_row "482913" "keyword-before OTP"

info "2. Money false-positive renders plainly (no crash, present)"
check_row "12500" "money message"

info "3. Grouped digits message"
check_row "482 913" "grouped OTP"

info "4. Order-number false-positive"
check_row "48291" "order message"

info "5. Keyword-after message"
check_row "752913" "keyword-after OTP"

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL
