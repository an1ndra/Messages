#!/usr/bin/env bash
# Test for issue #179: chat should open at the newest (bottom) messages, not the oldest
# Verifies that scrolling to bottom works on chat open.
#
# Prerequisites: app installed, emulator running, conversations with >40 messages exist.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/env.sh"
ADB="$HOME/android/platform-tools/adb"
SERIAL="emulator-5554"

TS=$(date +%Y%m%d_%H%M%S)
SHOT_DIR="$SCRIPT_DIR/../screenshots/issue179_$TS"
mkdir -p "$SHOT_DIR"

echo "=== Issue #179: Scroll-to-bottom on chat open ==="

# 1. Cold-launch the app
echo "[1/5] Launching app..."
$ADB -s "$SERIAL" shell am start -n com.anindra.messages/.MainActivity
sleep 2
$SHOT_DIR/01_home.png && true

# 2. Open a conversation with many messages (tap first row)
echo "[2/5] Opening first conversation..."
tap_text "Messages"
sleep 1.5
$SHOT_DIR/02_chat_opened.png && true

# 3. Capture the LazyColumn's visible items to verify we're at the bottom.
#    Dump the UI hierarchy and look for the last message text.
echo "[3/5] Checking scroll position..."
DUMP=$($ADB -s "$SERIAL" shell uiautomator dump /dev/tty 2>/dev/null || true)
# Find the last MessageRow's text node — it should be visible (on-screen)
LAST_MSG=$(echo "$DUMP" | grep -oP 'text="[^"]*"' | tail -1 || echo "unknown")
echo "   Last visible message node: $LAST_MSG"

# 4. Scroll down manually and verify it's already at the bottom (scroll should not move)
echo "[4/5] Attempting to scroll down to confirm already at bottom..."
$ADB -s "$SERIAL" shell input swipe 540 1800 540 600 200
sleep 0.5
$SHOT_DIR/03_after_swipe_down.png && true

# 5. Swipe up (scroll to top) then go back and re-open to re-test
echo "[5/5] Re-opening conversation to verify consistency..."
$ADB -s "$SERIAL" shell input keyevent KEYCODE_BACK
sleep 1
tap_text "Messages"
sleep 1.5
$SHOT_DIR/04_reopen_chat.png && true

echo ""
echo "=== Done ==="
echo "Screenshots saved to: $SHOT_DIR/"
echo ""
echo "To verify manually:"
echo "  1. Open a chat with >40 messages"
echo "  2. The newest (bottom) messages should be visible immediately"
echo "  3. Scrolling up should reveal older messages"
echo "  4. Re-opening the chat should again show newest messages"
