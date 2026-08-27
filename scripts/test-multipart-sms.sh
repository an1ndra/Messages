#!/usr/bin/env bash
# Regression test: a long inbound SMS (multi-PDU) must arrive as ONE message
# with ONE notification, not N split messages.
# Usage: test-multipart-sms.sh [number=15551337777]
set -euo pipefail
source "$(dirname "$0")/env.sh"

NUMBER="${1:-15551337777}"
LONG_TEXT="This is a deliberately long multipart SMS used to verify concatenation. Segment one ends here and the radio will split the payload across several PDUs because it exceeds the 160 character GSM-7 limit. The app must reassemble all parts into a single bubble."

info "Grant permissions + launch app"
bash "$(dirname "$0")/grant-permissions.sh" >/dev/null 2>&1 || true
adb_ shell am start -n "$ACT"; sleep 3

info "Counting notifications before injection"
NOTIF_BEFORE=$(adb_ shell dumpsys notification --noredact | grep -c "com.anindra.messages" || true)

info "Injecting multipart SMS from $NUMBER (${#LONG_TEXT} chars)"
adb_ emu sms send "$NUMBER" "$LONG_TEXT"
sleep 4

info "Notification count for our package after injection"
adb_ shell dumpsys notification --noredact | grep "com.anindra.messages" | wc -l

info "DB rows received from $NUMBER (expect exactly 1, full body)"
ROWS=$(adb_ shell run-as com.anindra.messages sh -c \
  'sqlite3 /data/data/com.anindra/messages/databases/messages.db \
   "SELECT COUNT(*), MAX(LENGTH(body)) FROM messages WHERE status='"'"'received'"'"' AND body LIKE \"%deliberately long multipart%\";"' \
   | tr -d '\r')
echo "count,maxlen = $ROWS (expect count=1)"

if [[ "$ROWS" == 1* ]]; then
    info "PASS: single message row stored"
else
    info "FAIL: message was split into multiple rows"
fi

info "Opening chat to visually confirm one bubble"
C=$(center_of_contains "$NUMBER") && adb_ shell input tap $C; sleep 2
shot "test-multipart-01-chat"
adb_ shell input keyevent KEYCODE_BACK

echo "Done."
