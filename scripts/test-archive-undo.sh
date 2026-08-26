#!/usr/bin/env bash
# Issue #97 regression: swipe-right to archive must show an UNDO snackbar,
# and tapping Undo must restore the row (parity with swipe-left delete).
#
# Run: scripts/test-archive-undo.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

info "Launch app on home list"
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -n "$ACT" >/dev/null
sleep 5

dump_ui || fail "could not dump UI"

# Header y-center ("Messages"); first text node below it = target row
HEADER_XY=$(center_of "Messages") || fail "'Messages' header not found"
HEADER_Y=${HEADER_XY#* }

ROW_LABEL=""
ROW_Y=""
while IFS=$'\t' read -r y label; do
    if [ "$y" -gt "$HEADER_Y" ] 2>/dev/null; then
        ROW_Y="$y"; ROW_LABEL="$label"; break
    fi
done < <(grep -oE 'text="[^"]{2,}"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$TMP/ui.xml" \
    | sed -E 's/text="([^"]*)".*\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\t\1\t\2\t\3/' \
    | awk -F'\t' '{print int(($3+$4)/2)"\t"$2}' | sort -n)

[ -z "$ROW_Y" ] && fail "no conversation row found below header"
echo "[target] '$ROW_LABEL' at y=$ROW_Y"

info "Swipe right on row (archive)"
adb_ shell input swipe 80 "$ROW_Y" 1000 "$ROW_Y" 300
sleep 2

dump_ui || fail "could not dump UI after swipe"
grep -q 'text="Conversation archived"' "$TMP/ui.xml" || fail "no 'Conversation archived' snackbar after swipe"
if ! grep -q 'text="Undo"' "$TMP/ui.xml"; then fail "snackbar has no Undo action"; fi

info "Tap Undo"
tap_text "Undo" || fail "could not tap Undo"
sleep 2

dump_ui || fail "could not dump UI after undo"
if grep -qF "text=\"$ROW_LABEL\"" "$TMP/ui.xml"; then
    pass "'$ROW_LABEL' restored after Undo — archive swipe has working UNDO (issue #97 fixed)"
else
    fail "'$ROW_LABEL' not restored — Undo did not unarchive"
fi
shot "archive-undo"
