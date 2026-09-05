#!/usr/bin/env bash
# End-to-end UI test sweep: builds, installs, then exercises every feature,
# saving numbered screenshots into screenshots/.
source "$(dirname "$0")/env.sh"
S="$(dirname "$0")"

run() { info "RUNNING: $*"; "$@" || echo "[warn] $* failed"; }

run bash "$S/install.sh"
run bash "$S/grant-permissions.sh"
run bash "$S/open-app.sh"
run bash "$S/receive-sms.sh" 15551337777 "Automated inbound test SMS"
run bash "$S/send-message.sh" 1 "Automated outbound test message"
run bash "$S/new-message.sh" 15559990001 "Hello new conversation"
run bash "$S/settings.sh"
run bash "$S/theme.sh" dark
run bash "$S/theme.sh" light
run bash "$S/theme.sh" system
run bash "$S/test-notification-posts.sh"

info "ALL DONE — screenshots in $SHOTS_DIR"
