#!/usr/bin/env bash
# Open conversation #<row> (1-based, top of list) and send a message.
# Usage: send-message.sh [row=1] ["text"]
source "$(dirname "$0")/env.sh"

ROW="${1:-1}"
TEXT="${2:-Hello! This is an automated test message}"

info "Opening app"
adb_ shell am start -n "$ACT"; sleep 2

Y=$(( 357 + (ROW - 1) * 190 ))
[ "$Y" -gt 2200 ] && Y=2200
info "Tapping conversation row $ROW at y=$Y"
adb_ shell input tap 500 "$Y"; sleep 1.5
shot "02-chat-open"

info "Focusing text field and typing: $TEXT"
tap_edittext; sleep 1
type_text "$TEXT"; sleep 0.8
shot "03-typed"

info "Sending via Send button"
tap_text "Send"; sleep 2
shot "04-sent"

info "Going back to list"
adb_ shell input keyevent 4; sleep 1
shot "05-back-to-list"
