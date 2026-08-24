#!/usr/bin/env bash
# Tests: two-way mirror between the app DB and the system SMS provider,
# plus demo-data hygiene and reinstall re-import.
#   1. SMS sent from the app appears in content://sms/sent  (survives uninstall)
#   2. SMS received while app is default handler appears in content://sms/inbox
#   3. No demo conversations once real SMS exist
#   4. Uninstall + reinstall -> history re-imported via syncFromSystem()
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

setup_perms() {
    # Dismiss any blocking dialog, then grant everything incl. READ_SMS
    adb_ shell input keyevent 4 >/dev/null 2>&1; sleep 0.5
    for p in SEND_SMS RECEIVE_SMS READ_SMS READ_CONTACTS READ_PHONE_STATE POST_NOTIFICATIONS; do
        adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null
    done
    adb_ shell cmd role add-role-holder android.app.role.SMS "$PKG" 2>/dev/null
    sleep 0.5
}

SENT_ADDR="+15551230777"
RECV_ADDR="+15551230888"

# Poll ui dumps until $1 appears (or timeout seconds elapse)
wait_for() {
    local pattern="$1" timeout="${2:-15}" i
    for ((i = 0; i < timeout; i++)); do
        dump_ui && grep -qE "$pattern" "$TMP/ui.xml" && return 0
        sleep 1
    done
    return 1
}

info "0. Permissions + default SMS role"
adb_ shell am force-stop "$PKG" >/dev/null; sleep 1
setup_perms

info "1. Send from app -> system Sent box"
adb_ shell am start -n "$ACT" >/dev/null; sleep 2
wait_for 'content-desc="Archived"' 20 || fail "list never loaded (skeleton stuck?)"
tap_text "Start chat" || adb_ shell input tap 940 2260
sleep 2
adb_ shell input tap 540 340; sleep 0.8
type_text "$SENT_ADDR"; sleep 1.5
C=$(center_of_contains "Send to")
if [ -n "$C" ]; then adb_ shell input tap $C; else adb_ shell input tap 500 460; fi
sleep 2
tap_edittext
type_text "mirror test outbound"; sleep 0.8
# Tap the Send button: raw ENTER (keyevent 66) inserts a newline instead of
# firing the IME Send action.
S=$(center_of_contains "Send")
if [ -n "$S" ]; then adb_ shell input tap $S; else adb_ shell input keyevent 66; fi
sleep 3
FOUND=$(adb_ shell "content query --uri content://sms/sent --projection address,body" 2>/dev/null | grep -c "mirror test outbound")
[ "$FOUND" -ge 1 ] && pass "outgoing mirrored to content://sms/sent" || fail "sent message NOT in system provider"

info "2. Receive -> system Inbox"
adb_ emu sms send "$RECV_ADDR" "mirror test inbound" >/dev/null 2>&1
sleep 4
FOUND=$(adb_ shell "content query --uri content://sms/inbox --projection address,body" 2>/dev/null | grep -c "mirror test inbound")
[ "$FOUND" -ge 1 ] && pass "incoming mirrored to content://sms/inbox" || fail "received message NOT in inbox"
dump_ui && grep -q "mirror test inbound" "$TMP/ui.xml" \
    && pass "message visible in app list" || echo "[info] row below fold in list"

info "3. Demo data absent while real SMS exist"
DEMO=0
for n in Jake Sarah Mom "Gym Buddy" "Dr. Patel"; do
    grep -q "text=\"$n\"" "$TMP/ui.xml" && DEMO=$((DEMO+1))
done
[ "$DEMO" = 0 ] && pass "no demo conversations shown" || fail "$DEMO demo rows visible"

info "4. Uninstall + reinstall re-imports from system provider"
adb_ uninstall "$PKG" >/dev/null 2>&1
adb_ install -r "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null 2>&1
setup_perms
adb_ shell am start -n "$ACT" >/dev/null
wait_for "mirror test inbound|$RECV_ADDR" 25 \
    && pass "history re-imported after fresh install" \
    || fail "fresh install did not re-import messages"
dump_ui
grep -qE 'text="Jake"|text="Sarah"' "$TMP/ui.xml" \
    && fail "demo data seeded despite real SMS" \
    || pass "no demo seed on real-device reinstall"

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL
