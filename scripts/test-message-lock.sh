#!/usr/bin/env bash
source ~/Develop/Messages/scripts/env.sh
adb_ shell input keyevent 4 >/dev/null; sleep 1
adb_ shell input keyevent 4 >/dev/null; sleep 1
R=$(center_of_contains "555-123-0777") || { echo "no row"; exit 1; }
adb_ shell input tap $R; sleep 2
dump_ui && grep -q '"Text message"' "$TMP/ui.xml" || { echo "not in chat"; exit 1; }
M=$(center_of_contains "final status check") || { echo "no msg"; exit 1; }
X=${M% *}; Y=${M#* }; X2=$((X+2))
adb_ shell input swipe $X $Y $X2 $Y 1000; sleep 1.5
L=$(center_of_contains "Lock") || { echo "no Lock item"; exit 1; }
echo "menu open; Lock at $L"
adb_ shell input tap $L; sleep 1.5
dump_ui >/dev/null
grep -q "Locked" "$TMP/ui.xml" && echo "LOCKED shown OK" || { echo inconclusive; grep -oE 'text="[^"]{1,30}"' "$TMP/ui.xml" | head -8; }
