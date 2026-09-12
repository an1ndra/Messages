#!/usr/bin/env bash
# Settings scroll-position retention:
#   Scrolling the Settings list, opening Advanced, and coming back must restore
#   the Settings list to the same scroll offset (it used to jump to the top).
#
# Precondition: emulator booted and the app installed.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

y_of() {
    python3 - "$1" "$TMP/ui.xml" <<'PY'
import re, sys
label, path = sys.argv[1], sys.argv[2]
xml = open(path).read()
ys = []
for t in re.findall(r'<node[^>]*>', xml):
    if f'text="{label}"' in t:
        b = re.search(r'bounds="\[\d+,(\d+)\]', t)
        if b:
            ys.append(int(b.group(1)))
print(min(ys) if ys else -1)
PY
}
center_of_text() {
    python3 - "$1" "$TMP/ui.xml" <<'PY'
import re, sys
label, path = sys.argv[1], sys.argv[2]
xml = open(path).read()
for t in re.findall(r'<node[^>]*>', xml):
    if f'text="{label}"' in t:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
        if b:
            print((int(b.group(1)) + int(b.group(3))) // 2,
                  (int(b.group(2)) + int(b.group(4))) // 2)
            break
PY
}

info "Launching into Settings and scrolling down"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true; sleep 3
for _ in 1 2 3; do adb_ shell input swipe 500 1900 500 500 350; sleep 0.5; done
dump_ui
top_before=$(y_of "Notifications")
adv_before=$(y_of "Advanced")
echo "before: 'Notifications' y=$top_before, 'Advanced' y=$adv_before"
if [ "$adv_before" -gt 0 ]; then
    pass "Advanced row is visible after scrolling"
else
    fail "Advanced row not visible after scrolling (y=$adv_before)"
fi

info "Opening Advanced, then going back"
ct=$(center_of_text "Advanced")
adb_ shell input tap $ct; sleep 1.8
dump_ui
if grep -q "Advanced settings" "$TMP/ui.xml"; then
    pass "Advanced settings opened"
else
    fail "Advanced settings did not open"
fi
adb_ shell input keyevent 4; sleep 1.8
dump_ui
top_after=$(y_of "Notifications")
adv_after=$(y_of "Advanced")
echo "after:  'Notifications' y=$top_after, 'Advanced' y=$adv_after"

if [ "$adv_before" = "$adv_after" ]; then
    pass "'Advanced' row kept the same position ($adv_before -> $adv_after)"
else
    fail "'Advanced' row moved ($adv_before -> $adv_after)"
fi
if [ "$top_before" = "$top_after" ]; then
    pass "scroll offset retained (top row y=$top_after)"
else
    fail "scroll offset changed (top row y=$top_before -> $top_after)"
fi

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
