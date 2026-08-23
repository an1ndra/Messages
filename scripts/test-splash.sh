#!/usr/bin/env bash
source ~/Develop/Messages/scripts/env.sh
snap(){
  ~/android/platform-tools/adb -s emulator-5554 exec-out screencap -p > /tmp/opencode/s.png
  python3 -c "
from PIL import Image
im=Image.open('/tmp/opencode/s.png').convert('L')
px=list(im.getdata())
print(f'{sum(px)/len(px):.0f}')"
}
for MODE in no yes; do
  adb_ shell cmd uimode night $MODE; sleep 1.5
  adb_ shell am force-stop "$PKG"; sleep 0.5
  adb_ shell am start -n "$ACT" >/dev/null 2>&1
  sleep 0.35; B=$(snap)
  echo "night=$MODE splash brightness: $B"
done
adb_ shell input keyevent 4 >/dev/null 2>&1
adb_ shell cmd uimode night yes >/dev/null
