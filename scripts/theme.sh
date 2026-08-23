#!/usr/bin/env bash
# Switch the app theme like a user would (deep link, deterministic).
# Usage: theme.sh [dark|light|system]
source "$(dirname "$0")/env.sh"

MODE="${1:-dark}"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --es set_theme "$MODE"; sleep 2.5
shot "15-theme-$MODE"
