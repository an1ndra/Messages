#!/usr/bin/env bash
# Simulate an INCOMING sms on the emulator radio.
# Usage: receive-sms.sh [number=15551337777] ["text"]
source "$(dirname "$0")/env.sh"

NUMBER="${1:-15551337777}"
TEXT="${2:-Hey! This SMS just arrived over the air :)}"

info "Injecting inbound SMS from $NUMBER"
adb_ emu sms send "$NUMBER" "$TEXT"
sleep 3
shot "06-incoming-notification"

info "Home screen after receive"
adb_ shell am start -n "$ACT"; sleep 2
shot "07-list-with-unread"
