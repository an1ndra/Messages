#!/usr/bin/env bash
# Google Messages-style long-press message selection:
#   - Long-pressing a bubble enters selection mode (no popup menu).
#   - The chat header is replaced by a contextual toolbar: X + count on the
#     left. With 1 selected it shows Copy, Delete and a More (3-dot) menu;
#     with >1 selected it shows only Delete.
#   - The More menu contains Share, Forward and View details.
#   - Selected bubbles turn dark blue with white text.
#   - The bottom input bar swaps Send for a Copy/Forward (two-squares) button.
#   - Tapping another bubble adds it to the selection; X exits selection mode.
#
# Seeds its own messages (adb emu sms send) so it doesn't depend on demo data.
# Precondition: emulator booted and the app installed.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
pass(){ echo "[PASS] $1"; PASS=$((PASS+1)); }
fail(){ echo "[FAIL] $1"; FAIL=$((FAIL+1)); }
info(){ echo -e "\n=== $* ==="; }

NUM="15558887777"
TOKEN="SELTOKEN"

launch(){
  adb_ shell am force-stop "$PKG"; sleep 1
  adb_ shell am start -n "$ACT" >/dev/null; sleep 4
  # The app may restore the last chat; walk back to the conversation list.
  local i
  for i in 1 2 3; do
    dump_ui >/dev/null 2>&1
    grep -q 'content-desc="Search"' "$TMP/ui.xml" && return 0
    adb_ shell input keyevent 4; sleep 1
  done
}
seed(){
  local t
  for t in "$TOKEN one" "$TOKEN two" "$TOKEN three"; do
    adb_ emu sms send "$NUM" "$t" >/dev/null 2>&1
    sleep 1.2
  done
}
open_conv(){
  local c i
  for i in 1 2 3 4 5 6; do
    c=$(center_of_contains "$1") && { adb_ shell input tap $c; sleep 2.5; return 0; }
    adb_ shell input swipe 500 1600 500 700 300; sleep 0.8
  done
  return 1
}
# Long-press the bubble whose displayed text contains $1. Targets the bubble's
# left padding so a highlighted URL span can't steal the gesture.
long_press_bubble(){
  local q b x1 y1 y2 lx ly i
  q=$(re_escape "$1")
  for i in 1 2 3; do dump_ui && break; sleep 1; done
  b=$(grep -oE "(text|content-desc)=\"[^\"]*$q[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
      "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
  [ -z "$b" ] && { echo "[lp] bubble '$1' not found"; return 1; }
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  lx=$((x1 - 25)); [ $lx -lt 32 ] && lx=32
  ly=$(( (y1 + y2) / 2 ))
  adb_ shell input motionevent DOWN $lx $ly
  sleep 1.2
  adb_ shell input motionevent UP $lx $ly
  sleep 1.5
}
# Tap the left padding of the nth bubble matching $1.
tap_bubble_left_nth(){
  local q b x1 y1 y2 tx ty n="${2:-1}" i
  q=$(re_escape "$1")
  for i in 1 2 3; do dump_ui && break; sleep 1; done
  b=$(grep -oE "(text|content-desc)=\"[^\"]*$q[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
      "$TMP/ui.xml" 2>/dev/null | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | sed -n "${n}p")
  [ -z "$b" ] && { echo "[tap] bubble #$n '$1' not found"; return 1; }
  x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
  y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
  y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
  tx=$((x1 + 20)); [ $tx -lt 32 ] && tx=32
  ty=$(( (y1 + y2) / 2 ))
  adb_ shell input tap $tx $ty; sleep 1
}
toolbar_has(){ dump_ui >/dev/null 2>&1; grep -q "content-desc=\"$1\"" "$TMP/ui.xml"; }
# The contextual toolbar's title is the selected-message count.
selection_count(){
  dump_ui >/dev/null 2>&1
  grep -oE 'text="[0-9]+"' "$TMP/ui.xml" | head -1 | grep -oE '[0-9]+'
}

info "Launching, seeding $NUM, opening the conversation"
launch
seed
open_conv "888-7777" || { echo "cannot open conversation"; exit 1; }

info "A. Long-press enters selection mode"
long_press_bubble "$TOKEN" || fail "could not long-press a bubble"
toolbar_has "Cancel selection" && pass "contextual toolbar shown (X)" || fail "toolbar missing"
[ "$(selection_count)" = "1" ] && pass "selected count = 1" || fail "count != 1 ($(selection_count))"
toolbar_has "Copy" && pass "Copy action present (1 selected)" || fail "Copy missing"
toolbar_has "Delete" && pass "Delete action present (1 selected)" || fail "Delete missing"
toolbar_has "More options" && pass "More (3-dot) present" || fail "More missing"

info "A2. More menu = Share / Forward / View details"
c=$(center_of_contains "More options") || c=""
[ -n "$c" ] && adb_ shell input tap $c; sleep 1
for m in Share Forward "View details"; do
  dump_ui >/dev/null 2>&1
  grep -q "text=\"$m\"" "$TMP/ui.xml" && pass "More has '$m'" || fail "More missing '$m'"
done
adb_ shell input keyevent 4 >/dev/null; sleep 0.8

info "B. Copy (1 selected) copies and exits selection"
c=$(center_of "Copy") || c=""
[ -n "$c" ] && adb_ shell input tap $c; sleep 1.2
if toolbar_has "Cancel selection"; then fail "selection toolbar still visible after Copy"; else pass "selection mode exited after Copy"; fi
tap_edittext >/dev/null 2>&1
sleep 0.8
adb_ shell input keyevent 279   # KEYCODE_PASTE
sleep 1.2
dump_ui >/dev/null
PASTED=$(python3 -c '
import re, sys
best = ""
for m in re.finditer(r"<node [^>]*>", open(sys.argv[1], errors="replace").read()):
    tag = m.group(0)
    if "class=\"android.widget.EditText\"" in tag:
        t = re.search(r"text=\"([^\"]*)\"", tag)
        best = t.group(1) if t else ""
        break
sys.stdout.write(best)
' "$TMP/ui.xml")
if printf '%s' "$PASTED" | grep -q "$TOKEN"; then pass "copied text reached the clipboard"; else fail "clipboard paste mismatch ('$PASTED')"; fi
adb_ shell input keyevent KEYCODE_MOVE_END
for _ in $(seq 1 60); do adb_ shell input keyevent 67; done

info "C. Multi-select shows only Delete"
long_press_bubble "$TOKEN" || fail "could not re-enter selection"
tap_bubble_left_nth "$TOKEN" 3 || fail "could not tap a second bubble"
N=$(selection_count)
if [ "$N" = "2" ]; then pass "second bubble selected (count=$N)"; else fail "multi-select failed (count=$N)"; fi
toolbar_has "Copy" && fail "Copy should be hidden for >1 selected" || pass "Copy hidden for >1 selected"
toolbar_has "Delete" && pass "Delete present for >1 selected" || fail "Delete missing for >1"

info "D. X cancels selection"
long_press_bubble "$TOKEN" || fail "could not re-enter selection"
c=$(center_of_contains "Cancel selection") || c=""
[ -n "$c" ] && adb_ shell input tap $c; sleep 1
if toolbar_has "Cancel selection"; then fail "toolbar still visible after X"; else pass "X exits selection mode"; fi
[ -z "$(selection_count)" ] && pass "no selection count remains" || fail "count remains ($(selection_count))"

echo ""
echo "=== RESULTS: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
