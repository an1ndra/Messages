#!/usr/bin/env bash
# send-dummy-sms.sh — Send multiple dummy SMS messages to populate the app
set -euo pipefail

ADB="$HOME/android/platform-tools/adb"
SERIAL="emulator-5554"
ADB_CMD="$ADB -s $SERIAL"

echo "Sending 10 dummy SMS messages..."

declare -a NUMBERS=(
    "+15551001001"
    "+15551002002"
    "+15551003003"
    "+15551004004"
    "+15551005005"
    "+15551006006"
    "+15551007007"
    "+15551008008"
    "+15551009009"
    "+15551010010"
)

declare -a MESSAGES=(
    "Hey! Are you free for lunch today?"
    "Your order #48291 has shipped"
    "Your OTP is 847291"
    "Meeting rescheduled to 3 PM"
    "Thanks for the update!"
    "Can you pick up groceries?"
    "Happy birthday! Have a great day"
    "Reminder: dentist appointment tomorrow"
    "Your package was delivered"
    "Let's catch up this weekend"
)

for i in "${!NUMBERS[@]}"; do
    num="${NUMBERS[$i]}"
    msg="${MESSAGES[$i]}"
    echo "  [$((i+1))/10] $num → $msg"
    $ADB_CMD emu sms send "$num" "$msg"
    sleep 0.5
done

echo ""
echo "Done! Launching app..."
$ADB_CMD shell am start -n com.anindra.messages/.MainActivity
sleep 3

echo "Taking screenshot..."
$ADB_CMD shell screencap -p /sdcard/dummy_sms.png
$ADB_CMD pull /sdcard/dummy_sms.png /tmp/dummy_sms.png >/dev/null 2>&1
echo "Screenshot saved to /tmp/dummy_sms.png"
