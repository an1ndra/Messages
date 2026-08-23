#!/usr/bin/env bash
# Open the Settings screen (theme, toggles) and screenshot it.
source "$(dirname "$0")/env.sh"

info "Opening Settings via deep link"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true; sleep 2
shot "11-settings"

info "Opening theme chooser"
adb_ shell input tap 500 950; sleep 1
shot "12-theme-dialog"

info "Closing dialog"
adb_ shell input keyevent 4; sleep 0.8
