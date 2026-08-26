#!/usr/bin/env bash
# Issue #90 regression: quick reply from notification must work end-to-end
# without crashing (DB + SMS send now run via goAsync + Dispatchers.IO).
#
# Flow: inject inbound SMS (app force-stopped = cold path) → open shade →
# tap Reply on OUR notification (identified by unique probe text) → type →
# send → assert row lands in the system Sent box and crash buffer stays clean.
#
# Run: scripts/test-quick-reply.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

NUM=15551230077
INCOMING="qreply probe $(date +%s)"
REPLY="autoreplyping90" # 1787739281

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

# systemui action buttons drop synthetic input-tap events; raw motionevents land
tap_at() {
    adb_ shell input motionevent DOWN "$1" "$2"
    sleep 0.15
    adb_ shell input motionevent UP "$1" "$2"
}

center_of_line() {
    sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' \
        <<< "$1" | head -1 | awk '{printf "%d %d\n", int(($1+$3)/2), int(($2+$4)/2)}'
}

# This build's uiautomator dump can pack many nodes onto one line;
# split into individual <node> tags before filtering.
ui_tags() { grep -oE '<node[^>]*>' "$TMP/ui.xml"; }

adb_ shell cmd role add-role-holder android.app.role.SMS com.anindra.messages >/dev/null 2>&1 || true
bash ./grant-permissions.sh >/dev/null 2>&1 || true

info "Reset shade: clear stale notifications"
adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb_ shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1
adb_ shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
sleep 3
dump_ui || true
CLEAR_NODE=$(ui_tags | grep -E 'content-desc="Clear all notifications\."|text="Clear all"' | head -1)
if [ -n "$CLEAR_NODE" ]; then
    C=$(center_of_line "$CLEAR_NODE")
    adb_ shell input tap "${C% *}" "${C# *}"
    sleep 2
fi
adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb_ shell cmd statusbar collapse >/dev/null 2>&1 || true
sleep 1

info "Force-stop app, inject inbound SMS from $NUM"
adb_ logcat -b crash -c >/dev/null 2>&1 || true
adb_ shell am force-stop "$PKG"
sleep 1
adb_ emu sms send "$NUM" "$INCOMING"
sleep 4

info "Open notification shade, find our notification by probe text"
found=0
for i in 1 2 3 4; do
    # fully reset shade state (QS panels / inline-reply leftovers survive collapse)
    adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    adb_ shell cmd statusbar collapse >/dev/null 2>&1 || true
    sleep 1
    adb_ shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
    sleep 4   # let shade entry animations settle, else tapped coords go stale
    dump_ui || true
    if grep -qF "text=\"$INCOMING\"" "$TMP/ui.xml"; then found=1; break; fi
done
[ "$found" -eq 1 ] || fail "probe notification not visible in shade"

info "Tap Reply on our notification (raw motionevents + retries)"
open_reply=0
for i in 1 2 3; do
    dump_ui || true
    # trailing "|| true": awk exits after first match, so upstream grep may get
    # SIGPIPE (141); pipefail would otherwise turn the match into a failure
    REPLY_NODE=$(ui_tags | awk -v t="$INCOMING" \
        'index($0, t) { f = 1 } f && index($0, "Button") && index($0, "content-desc=\"Reply\"") { print; exit }') || true
    [ -n "${DEBUG:-}" ] && echo "[dbg] iter=$i node=${REPLY_NODE:0:140}"
    [ -z "$REPLY_NODE" ] && { adb_ shell cmd statusbar expand-notifications >/dev/null 2>&1 || true; sleep 2; continue; }
    C=$(center_of_line "$REPLY_NODE")
    [ -n "${DEBUG:-}" ] && echo "[dbg] C=[$C]"
    [ -z "$C" ] && continue
    # require stable coords across two dumps (shade rows still animating otherwise)
    sleep 1
    dump_ui || true
    RECHECK=$(ui_tags | awk -v t="$INCOMING" \
        'index($0, t) { f = 1 } f && index($0, "Button") && index($0, "content-desc=\"Reply\"") { print; exit }') || true
    [ "$(center_of_line "$RECHECK")" == "$C" ] || continue
    # injected input-tap lands on the row body (auto-cancels it); raw
    # motionevents are the only injection that reaches the action button
    adb_ shell input motionevent DOWN "${C% *}" "${C# *}"
    sleep 0.15
    adb_ shell input motionevent UP "${C% *}" "${C# *}"
    sleep 2
    dump_ui || true
    if ui_tags | grep 'class="android.widget.EditText"' >/dev/null; then open_reply=1; break; fi
    [ -n "${DEBUG:-}" ] && {
        grep -qF "text=\"$INCOMING\"" "$TMP/ui.xml" && echo "[dbg] notif still visible" || echo "[dbg] notif GONE"
        true
    }
done

if [ "$open_reply" -ne 1 ]; then
    # systemui silently drops/re-routes injected taps on action buttons
    # (they land on the row body and auto-cancel the notification), so
    # hand this one step to a human finger:
    cat <<EOF

====================================================================
 MANUAL STEP NEEDED — on the EMULATOR screen:
   1. Find the notification from $NUM ("$INCOMING")
   2. Tap its REPLY action button
   3. Type:  $REPLY
   4. Tap the send arrow (or Enter)
 When done, press ENTER here to run automatic verification...
====================================================================
EOF
    read -r
    dump_ui || true
fi

[ "$open_reply" -eq 0 ] && echo "[info] continuing with manual reply..."
info "Type reply into RemoteInput field and send"
type_text "$REPLY"
sleep 1

SEND_NODE=$(ui_tags | grep -E '(text|content-desc)="Send"' | tail -1)
if [ -n "$SEND_NODE" ]; then
    C=$(center_of_line "$SEND_NODE")
    tap_at "${C% *}" "${C# *}"
else
    adb_ shell input keyevent 66   # IME action / enter
fi
sleep 4
adb_ shell cmd statusbar collapse >/dev/null 2>&1 || true

info "Assert reply stored + mirrored to system Sent box"
SENT=$(adb_ shell content query --uri content://sms/sent --projection body \
    --where "\"body LIKE '%$REPLY%'\"" 2>/dev/null | grep -c "$REPLY" || true)
[ "${SENT:-0}" -ge 1 ] || fail "reply '$REPLY' missing from system Sent box"

CRASHES=$(adb_ logcat -d -b crash 2>/dev/null | grep -c "$PKG" || true)
[ "${CRASHES:-0}" -eq 0 ] || fail "crash buffer contains $PKG entries"

pass "quick reply delivered end-to-end off-main-thread; no crashes (issue #90 fixed)"
shot "quick-reply"
