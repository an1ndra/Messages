#!/usr/bin/env bash
# Grant all runtime permissions silently (useful for scripted runs).
source "$(dirname "$0")/env.sh"

for p in SEND_SMS RECEIVE_SMS READ_CONTACTS POST_NOTIFICATIONS; do
    adb_ shell pm grant "$PKG" android.permission.$p 2>/dev/null && echo "[granted] $p"
done
