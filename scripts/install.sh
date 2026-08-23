#!/usr/bin/env bash
# Build (if needed) and install the debug APK on the emulator.
set -e
source "$(dirname "$0")/env.sh"

cd "$PROJECT_DIR"
"$HOME/tools/gradle-9.2.1/bin/gradle" assembleDebug --no-daemon -q
adb_ install -r app/build/outputs/apk/debug/app-debug.apk
echo "[ok] installed"
