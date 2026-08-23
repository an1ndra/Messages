#!/usr/bin/env bash
source ~/Develop/Messages/scripts/env.sh
P=0; F=0
ok(){ echo "  PASS: $1"; P=$((P+1)); }
no(){ echo "  FAIL: $1"; F=$((F+1)); }

launch(){
  adb_ shell am force-stop "$PKG"; sleep 1
  adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
}
open_menu_for(){
  local ROW=$1
  R=$(center_of_contains "$ROW") || return 1
  adb_ shell input tap $R; sleep 2
  dump_ui >/dev/null || return 1
  read MX MY <<< "$(python3 - "$TMP/ui.xml" <<'PY'
import re,sys
xml=open(sys.argv[1]).read()
m=re.search(r'content-desc="More options"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',xml) \
 or re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*content-desc="More options"',xml)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)
PY
)"
  adb_ shell input tap $MX $MY; sleep 1.5
}
tap_menu_item(){
  L=$(center_of_contains "$1") || return 1
  adb_ shell input tap $L; sleep 2
}

echo "=== A. Archive ==="
launch
open_menu_for "555-123-0002" || { echo cannot open; exit 1; }
tap_menu_item "Archive" || no "Archive item missing"
adb_ shell input keyevent 4 >/dev/null; sleep 1.5
dump_ui >/dev/null
if grep -q '"555-123-0002"' "$TMP/ui.xml"; then no "conv still in main list"; else ok "archived, removed from main list"; fi
A=$(center_of_contains "Archived") && adb_ shell input tap $A; sleep 1.5
dump_ui >/dev/null
grep -q '"555-123-0002"' "$TMP/ui.xml" && ok "present in Archived view" || no "not in Archived view"
R2=$(center_of_contains "555-123-0002") && adb_ shell input tap $R2; sleep 2
M=$(center_of_contains "More options")
read MX MY <<< "$(python3 -c "
import re
xml=open('$TMP/ui.xml').read()
m=re.search(r'content-desc=\"More options\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',xml)
print((int(m.group(1))+int(m.group(3)))//2,(int(m.group(2))+int(m.group(4)))//2)")"
adb_ shell input tap $MX $MY; sleep 1.5
dump_ui >/dev/null
if grep -q 'text="Unarchive"' "$TMP/ui.xml"; then
  U=$(center_of_contains "Unarchive") && adb_ shell input tap $U; sleep 2
  dump_ui >/dev/null
  grep -qE 'text="(Text message)"|content-desc="Search"' "$TMP/ui.xml" && ok "unarchive returns to chat/list" || no "unarchive odd state"
else
  echo "  (no Unarchive item — will restore via list un-archive later)"
  adb_ shell input keyevent 4 >/dev/null
fi

echo "=== B. Delete (to trash) ==="
launch
open_menu_for "555-123-0777" || { echo cannot open; exit 1; }
tap_menu_item "Delete" || no "Delete missing"
dump_ui >/dev/null
grep -q 'moved to trash' "$TMP/ui.xml" && ok "trash toast shown" || echo "  (toast may not be capturable)"
dump_ui >/dev/null
grep -q '"555-123-0777"' "$TMP/ui.xml" && no "still in main list" || ok "removed from main list"

echo "=== C. Details ==="
launch
open_menu_for "555-123-0888" || { echo cannot open; exit 1; }
tap_menu_item "Details" || no "Details missing"
sleep 1
dump_ui >/dev/null
grep -qE 'Call' "$TMP/ui.xml" && grep -q 'Notifications' "$TMP/ui.xml" && ok "details screen opened" || no "details did not open"
adb_ shell input keyevent 4 >/dev/null; sleep 1

echo "=== D. Block / Unblock ==="
launch
open_menu_for "555-123-0002" || { echo cannot open; exit 1; }
dump_ui >/dev/null
if grep -q 'text="Block number"' "$TMP/ui.xml"; then
  B=$(center_of_contains "Block number") && adb_ shell input tap $B; sleep 1.5
  dump_ui >/dev/null
  grep -q 'text="Unblock number"' "$TMP/ui.xml" && ok "block worked, now Unblock shown" || no "block did not stick"
  U=$(center_of_contains "Unblock number") && adb_ shell input tap $U; sleep 1.5
  dump_ui >/dev/null
  grep -q 'text="Block number"' "$TMP/ui.xml" && ok "unblock restored" || no "unblock failed"
else
  no "Block number item missing"
fi
adb_ shell input keyevent 4 >/dev/null

echo ""
echo "=== RESULTS: $P passed, $F failed ==="
