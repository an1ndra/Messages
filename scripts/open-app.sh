#!/usr/bin/env bash
# Launch the Messages app and screenshot the home screen.
source "$(dirname "$0")/env.sh"

info "Opening app"
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -n "$ACT"
sleep 2.5
shot "01-home"
