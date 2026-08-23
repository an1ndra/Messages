#!/usr/bin/env bash
# Start a brand-new conversation with a raw number and send a message.
# Usage: new-message.sh <number> ["text"]
source "$(dirname "$0")/env.sh"

NUMBER="${1:-15559990001}"
TEXT="${2:-First contact from the test script}"

info "Opening app"
adb_ shell am start -n "$ACT"; sleep 2

info "Tapping 'Start chat' FAB"
tap_text "Start chat" || { adb_ shell input tap 940 2260; }
sleep 1.5
shot "08-new-chat-screen"

info "Entering number: $NUMBER"
adb_ shell input tap 540 340; sleep 0.8
type_text "$NUMBER"; sleep 1

info "Picking 'Send to' entry"
if ! tap_text "Send to"; then
    # fallback: first list row below the search field
    adb_ shell input tap 500 460
fi
sleep 1.5
shot "09-empty-conversation"

info "Typing and sending"
adb_ shell input tap 534 2305; sleep 0.8
type_text "$TEXT"; sleep 0.5
adb_ shell input keyevent 66; sleep 2
shot "10-new-message-sent"
