#!/bin/bash
# test-security-fixes.sh — Verify all security fixes compile and install correctly
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
ADB="$HOME/android/platform-tools/adb"
EMU="emulator-5554"

cd "$ROOT"

echo "=== Building with security fixes ==="
~/tools/gradle-9.2.1/bin/gradle assembleDebug --no-daemon

echo "=== Installing ==="
$ADB -s "$EMU" install -r app/build/outputs/apk/debug/app-debug.apk

echo "=== Verifying allowBackup=false ==="
DUMP=$($ADB -s "$EMU" shell dumpsys package com.anindra.messages | grep -i "allowBackup")
echo "$DUMP"
if echo "$DUMP" | grep -q "false"; then
    echo "PASS: allowBackup is false"
else
    echo "FAIL: allowBackup is not false"
    exit 1
fi

echo "=== All security fix tests passed ==="
