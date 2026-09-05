#!/usr/bin/env bash
# Regression: incoming-SMS notifications must POST (and stay up) on Android
# 15/16 — plain text, BigTextStyle, sound, reply action with RemoteInput.
#
# History: notifications were silently dropped by NotificationManagerService.
# Two bugs:
#   1. Inverted foreground guard in SmsReceiver skipped the pipeline whenever
#      the app was NOT in the foreground (i.e. always — the normal case).
#   2. On Android 15+, a reply action whose PendingIntent is FLAG_IMMUTABLE is
#      silently dropped at NMS ("enqueued but never posted") because the system
#      cannot inject the RemoteInput reply text into an immutable intent.
#      The API-35 emulator shows NMS numEnqueuedByApp rising while
#      numPostedByApp stays 0 and no logcat line appears.
#
# This script asserts the notification appears in `cmd notification list`
# (cold path AND warm background path) with the Reply action wired.
#
# Run: scripts/test-notification-posts.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

NUM=15551230088
PROBE="notifprobe $(date +%s)"
fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }
info() { echo ".. $*"; }

assert_posts() {
    local note="$1" row
    info "$note"
    row=$(adb_ shell cmd notification list | grep 'com.anindra.messages' | head -1 || true)
    if [ -z "$row" ]; then
        fail "no com.anindra.messages notification in shade after $note"
    fi
    pass "notification present in shade: $row"
    if ! echo "$row" | grep -qE 'channel=messages' && \
       ! adb_ shell dumpsys notification | grep -q "Notification(channel=messages"; then
        fail "'messages' channel missing on posted notification"
    fi
    pass "posted on channel 'messages'"
    if ! adb_ shell dumpsys notification | grep -q '"Reply" -> PendingIntent'; then
        fail "Reply action not wired on posted notification"
    fi
    pass "Reply action (RemoteInput) wired"
}

adb_ shell cmd role add-role-holder android.app.role.SMS com.anindra.messages >/dev/null 2>&1 || true
bash ./grant-permissions.sh >/dev/null 2>&1 || true

info "=== cold path: force-stopped app receives SMS ==="
adb_ shell am force-stop com.anindra.messages
sleep 1
adb_ emu sms send "$NUM" "$PROBE cold"
sleep 3
assert_posts "cold start (force-stopped + injected SMS)"

info "=== warm background path: app in background, other screen ==="
adb_ shell input keyevent KEYCODE_HOME
sleep 1
adb_ emu sms send "$NUM" "$PROBE warm"
sleep 3
assert_posts "warm background"

for id in $(adb_ shell cmd notification list | grep 'com.anindra.messages' | sed -E 's/^\S+ \|com.anindra.messages \|([0-9]+).*/\1/'); do
    adb_ shell cmd notification cancel com.anindra.messages "$id" 2>/dev/null || true
done
echo
echo "ALL CHECKS PASSED"