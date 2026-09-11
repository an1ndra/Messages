#!/usr/bin/env bash
# Test for issue #179 (home screen) — desired behaviour:
#   1. App just opened (list at top) + a message arrives  -> the new conversation
#      is revealed at the top.
#   2. User has scrolled down + a message arrives          -> the list must NOT move.
#   3. Reopen after a message was received                  -> the unread conversation
#      is visible at the top by default (no scroll needed).
#
# Prerequisites: app installed, emulator running.
# Uses uiautomator dumps (no screenshots) so it can run unattended.
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/env.sh"

NUM_A="+15559990003"
NUM_B="+15559990004"
BODY_A="just opened reveal A"
BODY_B="scrolled stays put B"
PASS=0
FAIL=0

ok()  { echo "  PASS: $*"; PASS=$((PASS+1)); }
bad() { echo "  FAIL: $*"; FAIL=$((FAIL+1)); }

visible_text_contains() {
    dump_ui || return 1
    grep -qF "$1" "$TMP/ui.xml"
}

top_row() {
    dump_ui || return 1
    grep -oE 'text="\+?[0-9][0-9 ()-]{6,}"' "$TMP/ui.xml" | head -1 \
        | sed -E 's/^text="//; s/"$//'
}

scroll_down() {
    local n=${1:-6}
    for _ in $(seq 1 "$n"); do
        adb_ shell input swipe 540 1600 540 400 150
        sleep 0.3
    done
    sleep 1
}

reset_unread() {
    adb_ shell "run-as $PKG sqlite3 databases/messages.db 'UPDATE conversations SET unread_count=0;'" >/dev/null 2>&1 || true
}

cleanup() {
    adb_ shell "run-as $PKG sqlite3 databases/messages.db \"DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE address LIKE '%555999000%'); DELETE FROM conversations WHERE address LIKE '%555999000%';\"" >/dev/null 2>&1 || true
}

fresh_launch() {
    adb_ shell am force-stop "$PKG" >/dev/null 2>&1
    sleep 1
    adb_ shell am start -n "$ACT" >/dev/null 2>&1
    sleep 4
}

echo "=== Issue #179: home list scroll behaviour ==="

# --------------------------------------------------- 1. just opened + new message
info "Scenario 1: app just opened (at top), a message arrives"
reset_unread
cleanup
fresh_launch
adb_ emu sms send "$NUM_A" "$BODY_A" >/dev/null 2>&1
sleep 4

TOP_A="$(top_row)"
echo "  top after arrival: ${TOP_A:-<none>}"
if visible_text_contains "$BODY_A"; then
    ok "new message is revealed while the user was at the top"
else
    bad "new message not visible after arrival at top"
fi

# ------------------------------------------------ 2. scrolled down + new message
info "Scenario 2: message arrives while the user is scrolled down"
scroll_down 8
TOP_BEFORE="$(top_row)"
echo "  top before injection: ${TOP_BEFORE:-<none>}"
adb_ emu sms send "$NUM_B" "$BODY_B" >/dev/null 2>&1
sleep 4
TOP_AFTER="$(top_row)"
echo "  top after injection:  ${TOP_AFTER:-<none>}"
if [ -n "$TOP_BEFORE" ] && [ "$TOP_AFTER" = "$TOP_BEFORE" ]; then
    ok "list did not move while the user was scrolled down"
else
    bad "list moved on live arrival (before=$TOP_BEFORE after=$TOP_AFTER)"
fi

# ---------------------------------------------------- 3. reopen after receiving
info "Scenario 3: reopen after receiving — unread visible at the top"
fresh_launch
TOP_REOPEN="$(top_row)"
echo "  top after reopen: ${TOP_REOPEN:-<none>}"
if visible_text_contains "$BODY_B"; then
    ok "unread conversation is visible on open"
else
    bad "unread conversation not visible on open"
fi
if [ "$TOP_REOPEN" = "+1-555-999-0004" ]; then
    ok "unread conversation is the top row"
else
    bad "top row is $TOP_REOPEN, expected the unread +1-555-999-0004"
fi

# ---------------------------------------------------------------- cleanup
echo ""
info "Cleanup"
cleanup
echo "  removed test conversations"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
