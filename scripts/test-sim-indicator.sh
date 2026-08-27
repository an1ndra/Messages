#!/usr/bin/env bash
# test-sim-indicator.sh — Verify SIM indicator feature in chat status line
# Requires: adb, emulator running, app installed
set -euo pipefail

ADB="$HOME/android/platform-tools/adb"
SERIAL="emulator-5554"
ADB_CMD="$ADB -s $SERIAL"
SCREENSHOT_DIR="$(dirname "$0")/../screenshots"
mkdir -p "$SCREENSHOT_DIR"
STEP=0

shot() {
    STEP=$((STEP + 1))
    local label="${1:-step}"
    local file="$SCREENSHOT_DIR/sim-${STEP}-${label}.png"
    "$ADB_CMD" shell screencap -p "/sdcard/sim-${STEP}-${label}.png"
    "$ADB_CMD" pull "/sdcard/sim-${STEP}-${label}.png" "$file" >/dev/null 2>&1
    "$ADB_CMD" shell rm -f "/sdcard/sim-${STEP}-${label}.png"
    echo "  → $file"
}

tap() { "$ADB_CMD" shell input tap "$1" "$2"; }

echo "=== SIM Indicator Test ==="

echo "[1/5] Launching app..."
"$ADB_CMD" shell am start -n com.anindra.messages/.MainActivity
sleep 2

echo "[2/5] Home screen..."
shot "home"

echo "[3/5] Sending test SMS to create new conversation..."
"$ADB_CMD" emu sms send +15551234567 "SIM indicator test message"
sleep 2
"$ADB_CMD" shell am start -n com.anindra.messages/.MainActivity
sleep 2
shot "after-receive"

echo "[4/5] Opening a conversation with sent messages..."
# Tap on "Anindra" conversation (sent message shown in preview)
tap 500 1340
sleep 2
shot "chat-sent"

echo "[5/5] Checking status line for SIM indicator..."
echo "  Look for '· SIM 1' or '· SIM 2' in the status line"
echo "  (Old messages with subId=-1 won't show SIM label — that's expected)"
echo ""

echo "=== Done ==="
echo "Screenshots saved to $SCREENSHOT_DIR"
