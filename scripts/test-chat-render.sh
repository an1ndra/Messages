#!/usr/bin/env bash
# Issue #94 regression: message list must render dividers + send-status
# correctly after replacing the O(n^2) indexOfFirst lookup with itemsIndexed.
#
# Opens the first conversation and asserts:
#   - at least one day divider row (Today / Yesterday / <date>)
#   - at least one status line on an own message (Delivered / Sent · SMS / ...)
#
# Run: scripts/test-chat-render.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

info "Launch app, open first conversation"
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -n "$ACT" >/dev/null
sleep 5
dump_ui || fail "home dump failed"

# Collect all conversation rows sorted by Y
mapfile -t CONV_ROWS < <(grep -oE 'text="[^"]{2,}"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$TMP/ui.xml" \
    | sed -E 's/text="([^"]*)".*\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\t\1\t\2\t\3/' \
    | awk -F'\t' '{print int(($3+$4)/2)"\t"$2}' | sort -n)

FOUND_SMS=0
for entry in "${CONV_ROWS[@]}"; do
    ROW_Y="${entry%%	*}"
    ROW_LABEL="${entry#*	}"
    [ "$ROW_Y" -gt 350 ] 2>/dev/null || continue
    echo "[target] conversation row '$ROW_LABEL' at y=$ROW_Y"
    adb_ shell input tap 540 "$ROW_Y"
    sleep 3
    dump_ui || fail "chat dump failed"
    if grep -qE 'text="[0-9]{1,2}:[0-9]{2} [AP]M • SMS"' "$TMP/ui.xml"; then
        FOUND_SMS=1
        break
    fi
    adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    sleep 2
    dump_ui || fail "home dump retry failed"
done
[ "$FOUND_SMS" -eq 1 ] || fail "no conversation with sent message found"

# first-message divider lives at the TOP of the lazy list — fling up there
adb_ shell input swipe 540 600 540 2000 200
sleep 1
adb_ shell input swipe 540 600 540 2000 200
sleep 1
adb_ shell input swipe 540 600 540 2000 200
sleep 2
dump_ui || fail "chat top dump failed"

DIVIDERS=$(grep -oE 'text="(Today|Yesterday|[A-Z][a-z]{2} [0-9]{1,2})"' "$TMP/ui.xml" | sort -u | wc -l)
[ "$DIVIDERS" -ge 1 ] || fail "no day divider rows rendered in chat"
echo "[info] dividers found: $(grep -oE 'text="(Today|Yesterday|[A-Z][a-z]{2} [0-9]{1,2})"' "$TMP/ui.xml" | sort -u | tr '\n' ' ')"

shot "chat-render"
adb_ shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
pass "dividers + send-status render correctly after itemsIndexed refactor (#94)"
