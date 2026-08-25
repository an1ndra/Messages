#!/usr/bin/env bash
# Verifies swipe-to-delete requires ~65% travel before dismissing.
# Background: material3's SwipeToDismissBox positionalThreshold is ignored
# upstream (issuetracker 471021165 - settles at ~50% + 125dp/s velocity), so
# SwipeConversationItem gates dismissal via confirmValueChange + progress.
# Tests: 31% swipe bounces back, 56% swipe bounces back, full swipe deletes
# with Undo snackbar, Undo restores.
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

TEST_NUM="+1555-123-0731"
QUERY="555-123-0731"

row_y() {
    local c
    c=$(center_of_contains "$QUERY") || return 1
    awk '{print $2}' <<< "$c"
}

fresh_launch() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
}

restore_row_from_trash() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 3
    adb_ shell input swipe 540 1800 540 600 300; sleep 1
    tap_text "Trash" >/dev/null || return 1
    sleep 2
    dump_ui >/dev/null
    local b y1 y2
    b=$(grep -oE 'text="[^"]*0731[^"]*"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
            "$TMP/ui.xml" 2>/dev/null | head -1 \
        | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
    [ -z "$b" ] && return 1
    y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
    y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
    adb_ shell input tap 850 $(( (y1 + y2) / 2 )); sleep 1.5
    adb_ shell input keyevent 4; sleep 1
    adb_ shell input keyevent 4; sleep 1.5
}

ensure_row() {
    fresh_launch
    row_y >/dev/null && return 0
    adb_ emu sms send "$TEST_NUM" "swipe threshold test" >/dev/null 2>&1; sleep 2
    fresh_launch
    row_y >/dev/null && return 0
    restore_row_from_trash
    row_y >/dev/null
}

info "Ensuring disposable row $TEST_NUM"
ensure_row || { fail "could not prepare test row"; exit 1; }
pass "test row on list"

info "1. Short slow swipe (~31% travel) must NOT delete"
Y=$(row_y) || { fail "row missing"; exit 1; }
adb_ shell input swipe 900 "$Y" 560 "$Y" 900; sleep 1.5
if row_y >/dev/null; then
    pass "31% swipe bounced back (threshold works)"
else
    fail "31% swipe deleted the row - still too sensitive"
fi

info "2. Medium slow swipe (~56% travel) must NOT delete"
Y=$(row_y) || { fail "row missing before 56% swipe"; exit 1; }
adb_ shell input swipe 900 "$Y" 300 "$Y" 900; sleep 1.5
if row_y >/dev/null; then
    pass "56% swipe bounced back"
else
    fail "56% swipe deleted the row"
fi

info "3. Full slow swipe (~86% travel) deletes with Undo snackbar"
Y=$(row_y) || { fail "row missing before full swipe"; exit 1; }
adb_ shell input swipe 950 "$Y" 25 "$Y" 900
ROW_GONE=0
FOUND_UNDO=0
for i in 1 2 3 4 5; do
    sleep 1
    dump_ui
    grep -q "Undo" "$TMP/ui.xml" && FOUND_UNDO=1
    grep -q "$QUERY" "$TMP/ui.xml" || ROW_GONE=1
    [ "$ROW_GONE" -eq 1 ] && [ "$FOUND_UNDO" -eq 1 ] && break
done
if [ "$ROW_GONE" -eq 1 ] && [ "$FOUND_UNDO" -eq 1 ]; then
    pass "row trashed, Undo snackbar shown"
else
    fail "full swipe: row_gone=$ROW_GONE undo_found=$FOUND_UNDO"
fi

info "4. Cleanup: Undo restores the test row"
if tap_text "Undo" >/dev/null; then
    fresh_launch
    if row_y >/dev/null; then
        pass "test row restored"
    else
        fail "Undo did not restore row"
    fi
else
    fail "Undo button not found (snackbar expired)"
fi

echo
if [ "$FAIL" -eq 0 ]; then echo "ALL SWIPE THRESHOLD TESTS PASSED"; else echo "SOME TESTS FAILED"; fi
exit $FAIL
