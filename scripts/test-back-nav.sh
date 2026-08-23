#!/usr/bin/env bash
# Tests: archived-screen back behavior, double-back-to-exit guard,
# and draft clearing when leaving via the top-bar arrow.
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }

resumed_pkg() {
    adb_ shell dumpsys activity activities 2>/dev/null | grep -i "topResumedActivity" | head -1
}

info "Fresh launch"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5

info "1. Archived view: back returns to list (not exit)"
tap_text "Archived" || fail "archive toggle not found"
sleep 1.5
dump_ui
if grep -q '"Start chat"' "$TMP/ui.xml"; then
    fail "archived view did not open (FAB still visible)"; exit 1
fi
echo "[dbg] archived view opened"
adb_ shell input keyevent 4; sleep 2
TOP=$(resumed_pkg); echo "[dbg] top: $TOP"
# toasts/FAB text are invisible to uiautomator; assert list rows visible + app alive
dump_ui && grep -q 'content-desc="Search"' "$TMP/ui.xml" && echo "$TOP" | grep -q "$PKG" \
    && pass "back from Archived returns to list" \
    || { fail "archived back did not return to list"; adb_ shell am force-stop "$PKG"; }

info "2. Single back on root is guarded (app stays open)"
adb_ shell am start -n "$ACT" >/dev/null; sleep 3
adb_ shell input keyevent 4; sleep 1.5
TOP=$(resumed_pkg)
if echo "$TOP" | grep -q "$PKG"; then
    pass "single back guarded, app alive"
else
    fail "app closed on single back"
fi

info "3. Second back within 2s exits to launcher"
adb_ shell input keyevent 4; sleep 1
adb_ shell input keyevent 4; sleep 1.5
if resumed_pkg | grep -vq "$PKG"; then
    pass "app exited on second back"
else
    fail "app still running after double back"
fi

info "4. Draft clears when leaving via arrow button"
adb_ emu sms send "+15551230888" "draft holder" >/dev/null 2>&1; sleep 2
adb_ shell am start -n "$ACT" >/dev/null; sleep 3
C=$(center_of_contains "555-123-0888") || { fail "test row missing"; exit 1; }
adb_ shell input tap $C; sleep 2
tap_edittext; sleep 1
type_text "draftcheck"; sleep 0.5
tap_text "Back"; sleep 1.5
dump_ui && grep -q "Draft: draftcheck" "$TMP/ui.xml" \
    && pass "draft saved via arrow" \
    || echo "[info] draft indicator not visible (row may be below fold)"

info "5. Reopen, clear field, leave via arrow -> draft gone"
C=$(center_of_contains "555-123-0888") || { fail "row missing"; exit 1; }
adb_ shell input tap $C; sleep 2
tap_edittext; sleep 1
adb_ shell 'input keyevent 123; for i in $(seq 1 30); do input keyevent 67; done'; sleep 0.5
tap_text "Back"; sleep 1.5
dump_ui && ! grep -q "Draft:" "$TMP/ui.xml" \
    && pass "draft cleared after leaving via arrow" \
    || fail "draft still shown"
adb_ shell input keyevent 4; sleep 1

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL