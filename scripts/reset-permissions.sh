#!/usr/bin/env bash
# Revoke runtime permissions so you can watch the app request them.
# Then launch the app to see real permission dialogs.
source "$(dirname "$0")/env.sh"

for p in SEND_SMS RECEIVE_SMS READ_CONTACTS POST_NOTIFICATIONS; do
    adb_ shell pm revoke "$PKG" android.permission.$p 2>/dev/null \
        && echo "[revoked] $p"
done

info "Relaunching app — permission dialogs should appear now"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT"; sleep 2
shot "14-permission-dialogs"
echo "[note] Accept each dialog. Re-run grant-permissions.sh to grant silently."
