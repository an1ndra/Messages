#!/usr/bin/env bash
# Regression for issue #184: incoming-SMS notifications must show the saved
# CONTACT NAME in the title, not the raw phone number.
#
# Root cause: NotificationHelper.show() set the content title to the raw
# sender address ("from"). Fix resolves it through the address book
# (Repository.contactNameFor) and falls back to the number only when the
# contact is not saved.
#
# Run: bash scripts/test-issue-184-notification-name.sh
set -euo pipefail
cd "$(dirname "$0")"
source ./env.sh

NUM=15551230010
NAME=${1:-Sarah}
PROBE="notifname $(date +%s)"
fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

info "ensure demo contact $NAME ($NUM) exists"
bash ./insert-demo-contacts.sh >/dev/null 2>&1 || true

info "install + permissions"
cd "$PROJECT_DIR" && { ./gradlew assembleDebug --no-daemon >/dev/null 2>&1 || true; } && cd -
adb_ install -r "$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
adb_ shell cmd role add-role-holder android.app.role.SMS com.anindra.messages >/dev/null 2>&1 || true
bash ./grant-permissions.sh >/dev/null 2>&1 || true
adb_ shell pm grant com.anindra.messages android.permission.READ_CONTACTS >/dev/null 2>&1 || true

info "cold path: force-stopped app receives SMS from $NUM"
adb_ shell am force-stop com.anindra.messages
adb_ shell cmd notification cancel_all com.anindra.messages 2>/dev/null || true
sleep 1
adb_ emu sms send "$NUM" "$PROBE"
sleep 4

row=$(adb_ shell cmd notification list | grep 'com.anindra.messages' | head -1 || true)
[ -z "$row" ] && fail "no com.anindra.messages notification in shade"
pass "notification present"

title=$(adb_ shell dumpsys notification --noredact | grep 'android.title=String (' | head -1 || true)
if [ -z "$title" ]; then
    fail "could not read notification title from dumpsys"
fi

if ! echo "$title" | grep -q "$NAME"; then
    fail "title does NOT contain contact name '$NAME' -> $title"
fi
pass "title contains contact name ($title)"

if echo "$title" | grep -q "$NUM"; then
    fail "title still shows raw number $NUM -> $title"
fi
pass "title does not contain raw number"

for id in $(adb_ shell cmd notification list | grep 'com.anindra.messages' | sed -E 's/^\S+ \|com.anindra.messages \|([0-9]+).*/\1/'); do
    adb_ shell cmd notification cancel com.anindra.messages "$id" 2>/dev/null || true
done
echo
echo "ALL CHECKS PASSED"