#!/usr/bin/env bash
# Issue #181 — chat composer must request sentence auto-capitalization.
#   Asserts the focused composer's EditorInfo.inputType carries
#   TYPE_TEXT_FLAG_CAP_SENTENCES (0x4000), which is what tells the IME to
#   capitalize the first letter of each new sentence.
#
# Precondition: emulator booted, app installed, at least one conversation.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

CAP_SENTENCES=0x4000

info "Opening the first conversation"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT"; sleep 3
dump_ui
ct=$(python3 - "$TMP/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1]).read()
best = None
for t in re.findall(r'<node[^>]*>', xml):
    if 'clickable="true"' in t and 'long-clickable="true"' in t \
            and 'class="android.view.View"' in t:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
        if b:
            x = (int(b.group(1)) + int(b.group(3))) // 2
            y = (int(b.group(2)) + int(b.group(4))) // 2
            if best is None or y < best[1]:
                best = (x, y)
print(f"{best[0]} {best[1]}" if best else "")
PY
)
if [ -z "$ct" ]; then
    echo "[FAIL] no conversation row found on the home list"
    FAIL=$((FAIL + 1))
    echo; echo "Results: $PASS passed, $FAIL failed"; exit 1
fi
adb_ shell input tap $ct; sleep 2.5
dump_ui

info "Focusing the message composer"
ct=$(python3 - "$TMP/ui.xml" <<'PY'
import re, sys
xml = open(sys.argv[1]).read()
for t in re.findall(r'<node[^>]*>', xml):
    if 'class="android.widget.EditText"' in t:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
        if b:
            print(f"{(int(b.group(1)) + int(b.group(3))) // 2} "
                  f"{(int(b.group(2)) + int(b.group(4))) // 2}")
            break
PY
)
if [ -z "$ct" ]; then
    echo "[FAIL] no composer EditText found in the chat"
    FAIL=$((FAIL + 1))
    echo; echo "Results: $PASS passed, $FAIL failed"; exit 1
fi
adb_ shell input tap $ct; sleep 2.5

info "Checking EditorInfo.inputType for CAP_SENTENCES ($CAP_SENTENCES)"
types=$(adb_ shell dumpsys input_method 2>/dev/null \
    | grep -oE "inputType=0x[0-9a-f]+" | cut -d= -f2 | sort -u)
echo "observed inputTypes: $(echo "$types" | tr '\n' ' ')"
if python3 - "$CAP_SENTENCES" $types <<'PY'
import sys
cap = int(sys.argv[1], 16)
sys.exit(0 if any(int(v, 16) & cap for v in sys.argv[2:]) else 1)
PY
then
    pass "composer requests sentence auto-capitalization"
else
    fail "composer does NOT request CAP_SENTENCES"
fi

adb_ shell input keyevent 4; sleep 0.8   # close IME
adb_ shell input keyevent 4; sleep 1.5   # chat -> list

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
