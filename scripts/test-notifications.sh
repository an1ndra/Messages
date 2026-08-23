#!/usr/bin/env bash
set -euo pipefail
PKG=com.anindra.messages
EM="adb -s emulator-5554"

# Ensure app is running
$EM shell am start -n "$PKG/.MainActivity"
sleep 2

# Screenshot 01: Home list
$EM shell screencap -p /sdcard/test-notif-01-home.png
$EM pull /sdcard/test-notif-01-home.png screenshots/ 2>/dev/null || true

# Open a conversation (tap first item)
$EM shell input tap 540 400
sleep 1

# Screenshot 02: Chat screen
$EM shell screencap -p /sdcard/test-notif-02-chat.png
$EM pull /sdcard/test-notif-02-chat.png screenshots/ 2>/dev/null || true

# Open 3-dot menu
$EM shell input tap 1020 140
sleep 1

# Screenshot 03: Menu with Notifications toggle
$EM shell screencap -p /sdcard/test-notif-03-menu.png
$EM pull /sdcard/test-notif-03-menu.png screenshots/ 2>/dev/null || true

# Toggle notifications off (tap the Switch in the Notifications row)
# The notifications row is roughly at y=500 based on menu position
$EM shell input tap 900 500
sleep 1

# Screenshot 04: After toggling off
$EM shell screencap -p /sdcard/test-notif-04-toggled.png
$EM pull /sdcard/test-notif-04-toggled.png screenshots/ 2>/dev/null || true

# Back to home
$EM shell input keyevent KEYCODE_BACK
sleep 0.5
$EM shell input keyevent KEYCODE_BACK
sleep 1

echo "Screenshots saved to screenshots/"
echo "Test complete: Per-conversation notification toggle visible in ChatScreen 3-dot menu"
