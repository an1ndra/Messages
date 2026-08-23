#!/usr/bin/env bash
# Tests: swipe sensitivity, swipe-delete -> trash + Undo snackbar,
# long-press Delete -> trash, Settings -> Trash (restore), and the
# "Mark all as read" setting.
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

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

row_y() {
    local c
    c=$(center_of_contains "$1") || return 1
    awk '{print $2}' <<< "$c"
}

fresh_launch() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
}

info "Inject disposable conversation"
adb_ emu sms send "+1555-123-0999" "trash me please" >/dev/null 2>&1; sleep 2
fresh_launch

Y=$(row_y "555-123-0999") || { fail "test row not on list"; exit 1; }

info "1. Short slow swipe must NOT delete (sensitivity)"
adb_ shell input swipe 900 "$Y" 560 "$Y" 900; sleep 1.5
if center_of_contains "555-123-0999" >/dev/null; then
    pass "short swipe ignored (threshold works)"
else
    fail "short swipe deleted the row - still too sensitive"
    adb_ emu sms send "+15551230999" "trash me please" >/dev/null 2>&1; sleep 1
    fresh_launch
fi

info "2. Full slow swipe deletes to trash with Undo snackbar"
Y=$(row_y "555-123-0999") || { fail "row missing before swipe"; exit 1; }
adb_ shell input swipe 950 "$Y" 25 "$Y" 900; sleep 1.5
if grep -q "Moved to trash" "$TMP/ui.xml" && ! grep -q "555-123-0999" "$TMP/ui.xml"; then
    pass "row trashed, snackbar shown"
else
    dump_ui
    if ! grep -q "555-123-0999" "$TMP/ui.xml"; then
        pass "row removed from list"
    else
        fail "full swipe did not remove row"
    fi
fi

info "3. Tap Undo restores the row"
if tap_text "Undo"; then
    sleep 1.5
    if dump_ui && grep -q "555-123-0999" "$TMP/ui.xml"; then
        pass "undo restored conversation"
    else
        fail "undo did not restore"
    fi
else
    # snackbar may have expired; re-trash then continue via Settings path
    fail "Undo button not found"
fi

info "4. Long-press menu Delete also moves to trash"
C=$(center_of_contains "555-123-0999") || { fail "row missing"; exit 1; }
XY=($C)
adb_ shell input swipe "${XY[0]}" "${XY[1]}" "${XY[0]}" "${XY[1]}" 900; sleep 1.2
tap_text "Delete" || { fail "sheet Delete missing"; exit 1; }
sleep 1.5
dump_ui && ! grep -q "555-123-0999" "$TMP/ui.xml" \
    && pass "sheet delete moved row to trash" \
    || fail "sheet delete failed"

info "5. Settings shows Trash + Mark all as read; restore works"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 3
FOUND_TRASH=0; FOUND_MARK=0
for i in 1 2 3 4 5; do
    dump_ui
    grep -q '"Trash"' "$TMP/ui.xml" && FOUND_TRASH=1
    grep -q "Mark all as read" "$TMP/ui.xml" && FOUND_MARK=1
    [ "$FOUND_TRASH" = "1" ] && [ "$FOUND_MARK" = "1" ] && break
    adb_ shell input swipe 540 1800 540 700 300; sleep 1.2
done
[ "$FOUND_MARK" = "1" ] && pass "'Mark all as read' present" || fail "'Mark all as read' missing"
if [ "$FOUND_TRASH" = "1" ]; then
    pass "'Trash' entry present"
    tap_text "Trash"; sleep 2
    if center_of_contains "555-123-0999" >/dev/null; then
        pass "trashed item listed"
        tap_text "Restore"; sleep 1.5
        adb_ shell input keyevent 4; sleep 1
    else
        fail "trashed item not listed"
    fi
else
    fail "'Trash' entry missing"
fi

info "6. Restored item is back on the main list"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
if center_of_contains "555-123-0999" >/dev/null; then
    pass "restored row visible on list"
else
    fail "restored row not on list"
fi

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL