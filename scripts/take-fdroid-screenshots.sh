#!/usr/bin/env bash
source ~/Develop/Messages/scripts/env.sh

echo "=== DARK MODE ==="
adb_ shell cmd uimode night yes; sleep 1.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 800 400; sleep 0.5
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/01-home-dark.png
echo "01-home-dark ✓"

R=$(center_of_contains "Sarah") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/02-chat-dark.png
echo "02-chat-dark ✓"
adb_ shell input swipe 10 600 400 600 150; sleep 1

echo "=== LIGHT MODE ==="
adb_ shell cmd uimode night no; sleep 1.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 800 400; sleep 0.5
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/03-home-light.png
echo "03-home-light ✓"

R=$(center_of_contains "Sarah") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/04-chat-light.png
echo "04-chat-light ✓"
adb_ shell input swipe 10 600 400 600 150; sleep 1

R=$(center_of_contains "Settings") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/05-settings-light.png
echo "05-settings-light ✓"

adb_ shell cmd uimode night yes; sleep 1.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 800 400; sleep 0.5
R=$(center_of_contains "Settings") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/06-settings-dark.png
echo "06-settings-dark ✓"

echo "=== REPLY ==="
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 800 400; sleep 0.5
R=$(center_of_contains "Sarah") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
E=$(center_of_contains "Text message") && adb_ shell input tap $E; sleep 1
adb_ shell input text "Hey, still on for dinner tonight?"; sleep 1
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/07-reply-dark.png
echo "07-reply-dark ✓"

adb_ shell cmd uimode night no; sleep 1.5
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 800 400; sleep 0.5
R=$(center_of_contains "Sarah") && X=${R% *}; Y=${R#* }; adb_ shell input tap $X $Y; sleep 2
E=$(center_of_contains "Text message") && adb_ shell input tap $E; sleep 1
adb_ shell input text "Hey, still on for dinner tonight?"; sleep 1
~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > ~/Develop/Messages/screenshots/fdroid/08-reply-light.png
echo "08-reply-light ✓"

adb_ shell cmd uimode night no; sleep 1
echo "=== DONE ==="
ls -lh ~/Develop/Messages/screenshots/fdroid/
