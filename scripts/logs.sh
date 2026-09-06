#!/usr/bin/env bash
# Tail logcat for the Messages app process.
# Usage: scripts/logs.sh [filter]   e.g. scripts/logs.sh TAG
set -e
source "$(dirname "$0")/env.sh"

FILTER="${1:-*}"

PID=$(adb_ shell pidof "$PKG" | tr -d '\r' | head -1)
if [ -z "$PID" ]; then
    echo "[logs] $PKG is not running. Start it, then rerun this script." >&2
    exit 1
fi

echo "[logs] streaming logcat for $PKG (PID $PID) — Ctrl+C to stop" >&2
exec "$ADB" -s "$ANDROID_SERIAL" logcat -v threadtime --pid="$PID" "$FILTER"