#!/usr/bin/env bash
set -euo pipefail
source ~/Develop/Messages/scripts/env.sh
PASS=0; FAIL=0; SKIP=0
pass() { echo "  ✅ PASS: $1"; PASS=$((PASS+1)); }
fail() { echo "  ❌ FAIL: $1"; FAIL=$((FAIL+1)); }
skip() { echo "  ⏭️  SKIP: $1"; SKIP=$((SKIP+1)); }

adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5

info "1. Save-contact banner hidden in chat"
R=$(center_of_contains "555-123-0777") || R=$(center_of_contains "555-123-0888") || { fail "no conversation row"; exit 1; }
adb_ shell input tap $R; sleep 2
dump_ui >/dev/null
if grep -qE 'text="Save \+?[0-9]' "$TMP/ui.xml"; then fail "banner visible"; else pass "banner hidden"; fi

info "2. SIM icon absent from input pill"
grep -q 'content-desc="Switch SIM"' "$TMP/ui.xml" && fail "icon still in input bar" || pass "input pill clean"

info "3. SIM entries in chat 3-dot menu"
read MX MY <<< "$(python3 - "$TMP/ui.xml" <<'PY'
import re,sys
xml=open(sys.argv[1]).read()
m=re.search(r'content-desc="More options"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml) \
 or re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="More options"',xml)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)
PY
)"
adb_ shell input tap $MX $MY; sleep 1.5
dump_ui >/dev/null
NSIMS=$(~/android/platform-tools/adb -s emulator-5554 shell dumpsys isub | grep -oE "id=[0-9]+" | sort -u | wc -l)
if [ "$NSIMS" -lt 2 ]; then
    grep -q 'text="SIM ' "$TMP/ui.xml" && fail "SIM items shown with single SIM" || skip "single-SIM device — menu items correctly hidden (verify on dual-SIM phone)"
else
    grep -q 'text="SIM 1"' "$TMP/ui.xml" && pass "SIM 1 row shown" || fail "SIM 1 row missing"
    grep -q 'text="SIM 2"' "$TMP/ui.xml" && pass "SIM 2 row shown" || fail "SIM 2 row missing"
fi
adb_ shell input keyevent 4 >/dev/null

echo ""
echo "=== RESULTS: $PASS passed, $FAIL failed, $SKIP skipped ==="
[ "$FAIL" -eq 0 ]
