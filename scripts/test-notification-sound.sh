#!/usr/bin/env bash
# Regression for the "Notification sound" setting (Settings → General):
#   - The "Notification sound" row exists under the sound toggles and its
#     subtitle shows the active choice (default: "Default (system)").
#   - Tapping it opens a radio dialog with Default / Classic /
#     Dragon Studio / Chime / Bubble.
#   - Picking "Dragon Studio" persists pref notification_sound=dragon_studio
#     and an incoming SMS notification carries sound=android.resource://...
#     (i.e. the bundled custom tone, NOT the system default URI).
#   - Picking "Default (system)" reverts to the system default sound URI
#     content://settings/system/notification_sound.
#   - "Receive sound" OFF posts a silent notification (sound=null).
#   - Defaults are restored at the end.
#
# Precondition: emulator booted, app installed. Uses uiautomator dumps (no
# screenshots). Requires the app to be the SMS role holder so injected SMS
# reach the app.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
info() { echo -e "\n=== $* ==="; }
pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

pref_get() {
    adb_ shell "run-as $PKG cat shared_prefs/messages_settings.xml" 2>/dev/null > "$TMP/pref_get.xml"
    python3 - "$1" "$TMP/pref_get.xml" <<'PY'
import re, sys
key, path = sys.argv[1], sys.argv[2]
try:
    s = open(path).read()
except OSError:
    s = ""
m = re.search(r'<(?:boolean|string|int) name="%s" value="([^"]*)"\s*/>' % re.escape(key), s)
if m:
    print(m.group(1))
else:
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(key), s)
    if m:
        print(m.group(1))
PY
}
pref_on() { [ "$(pref_get "$1")" = "true" ]; }

# Poll a pref until it matches (the running app may flush stale state briefly).
wait_pref() {
    local key="$1" want="$2" i
    for i in $(seq 1 24); do
        [ "$(pref_get "$key")" = "$want" ] && return 0
        sleep 0.5
    done
    return 1
}

# Rewrite a pref directly (app must be stopped) to set reliable baselines/restores.
set_prefs() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell "run-as $PKG cat shared_prefs/messages_settings.xml" > "$TMP/prefs.xml" 2>/dev/null
    python3 - "$1" "$2" "$TMP/prefs.xml" <<'PY'
import re, sys
key, val, path = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(path).read()
if val == "true" or val == "false":
    tag = f'<boolean name="{key}" value="{val}" />'
    s = re.sub(r'<boolean name="%(k)s" value="(true|false)"\s*/>' % {"k": re.escape(key)},
               tag, s)
    if f'name="{key}"' not in s:
        s = s.replace('</map>', f'    {tag}\n</map>')
else:
    tag = f'<string name="{key}">{val}</string>'
    s = re.sub(r'<string name="%(k)s">[^<]*</string>' % {"k": re.escape(key)}, tag, s)
    if f'name="{key}"' not in s:
        s = s.replace('</map>', f'    {tag}\n</map>')
open(path, 'w').write(s)
PY
    adb_ push "$TMP/prefs.xml" /data/local/tmp/prefs.xml >/dev/null 2>&1
    adb_ shell "run-as $PKG cp /data/local/tmp/prefs.xml shared_prefs/messages_settings.xml"
    echo "  pref $1 -> $2"
}

launch_settings() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null 2>&1; sleep 3
}

open_sound_picker() {
    tap_text "Notification sound" || { fail "could not tap Notification sound row"; return 1; }
    sleep 1.2
}

# Channel the incoming-message notification for [body] was posted on.
notif_channel() {
    local body="$1"
    adb_ shell dumpsys notification --noredact 2>/dev/null | awk -v b="$body" '
        /NotificationRecord\(/ { line = $0 }
        index($0, b) {
            m = line
            sub(/^.*channel=/, "", m)
            sub(/ .*$/, "", m)
            print m
            exit
        }'
}

# Sound carried by the app's currently-active "Messages" channel. This is what
# Android actually plays; it must follow the setting (channels are immutable).
channel_sound() {
    adb_ shell dumpsys notification --noredact 2>/dev/null \
        | grep -oE "NotificationChannel\{mId='messages[^']*'[^}]*mDeleted=false" \
        | sed -E "s/.*mSound=([^,]*),.*/\1/" | head -1
}

adb_ shell cmd role add-role-holder android.app.role.SMS com.anindra.messages >/dev/null 2>&1 || true
adb_ shell pm grant com.anindra.messages android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true

info "Setting a known baseline (Receive sound ON, Default)"
set_prefs receive_sound_enabled true
set_prefs notification_sound default
launch_settings
dump_ui
grep -q 'text="Notification sound"' "$TMP/ui.xml" &&
    pass "'Notification sound' row present in General settings" ||
    fail "'Notification sound' row missing"
grep -q 'text="Default (system)"' "$TMP/ui.xml" &&
    pass "subtitle defaults to 'Default (system)'" ||
    fail "subtitle does not default to 'Default (system)'"

info "Opening the picker dialog"
open_sound_picker
dump_ui
grep -q 'text="Notification sound"' "$TMP/ui.xml" &&
    pass "picker dialog shown" || fail "picker dialog not shown"
for label in "Default (system)" "Classic" "Dragon Studio" \
             "Chime" "Bubble"; do
    grep -q "text=\"$label\"" "$TMP/ui.xml" &&
        pass "option '$label' listed" || fail "option '$label' missing"
done

info "Selecting Dragon Studio -> incoming SMS plays the custom tone"
pid=$(adb_ shell pidof "$PKG" | tr -d '\r')
tap_text "Dragon Studio"
sleep 0.3
adb_ shell dumpsys audio 2>/dev/null | grep -E "u/pid:[0-9]+/$pid " | grep -qE "state:(started|paused|stopped)" &&
    pass "preview tone plays on selection" || fail "preview tone did not start"
tap_text "OK"; sleep 1.5
pref=$(pref_get notification_sound)
[ "$pref" = "dragon_studio" ] &&
    pass "pref notification_sound=dragon_studio" ||
    fail "pref notification_sound is '$pref'"
adb_ emu sms send +15559990099 "dragon check" >/dev/null 2>&1; sleep 2
s=$(notif_channel "dragon check")
echo "  notification channel: $s"
[ "$s" = "messages_dragon" ] &&
    pass "notification posted on the dragon channel" ||
    fail "notification posted on the wrong channel ($s)"
cs=$(channel_sound)
echo "  active channel: $cs"
case "$cs" in
    android.resource://*) pass "active channel plays the bundled custom tone" ;;
    *) fail "active channel does NOT play the custom tone ($cs)" ;;
esac

info "Reverting to Default -> system notification sound"
launch_settings
open_sound_picker
tap_text "Default (system)"; sleep 0.6
tap_text "OK"; sleep 1.5
pref=$(pref_get notification_sound)
[ "$pref" = "default" ] &&
    pass "pref notification_sound=default" ||
    fail "pref notification_sound is '$pref'"
adb_ emu sms send +15559990099 "default check" >/dev/null 2>&1; sleep 2
s=$(notif_channel "default check")
echo "  notification channel: $s"
[ "$s" = "messages_default" ] &&
    pass "notification posted on the default channel" ||
    fail "notification posted on the wrong channel ($s)"
cs=$(channel_sound)
echo "  active channel: $cs"
[ "$cs" = "content://settings/system/notification_sound" ] &&
    pass "active channel plays the system default tone" ||
    fail "active channel does not use the system tone ($cs)"

info "Receive sound OFF -> silent notification"
launch_settings
tap_switch_near "Receive sound" || fail "could not toggle Receive sound"
wait_pref receive_sound_enabled false && pass "receive sound turned off" || fail "receive sound not turned off"
adb_ emu sms send +15559990099 "silent check" >/dev/null 2>&1; sleep 2
s=$(notif_channel "silent check")
echo "  notification channel: $s"
[ "$s" = "messages_silent" ] &&
    pass "notification posted on the silent channel" ||
    fail "notification posted on the wrong channel ($s)"
cs=$(channel_sound)
echo "  active channel: $cs"
[ "$cs" = "null" ] &&
    pass "active channel is silent" ||
    fail "active channel is not silent ($cs)"

info "Restoring defaults (through the UI, like a user would)"
launch_settings
open_sound_picker
tap_text "Default (system)"; sleep 0.6
tap_text "OK"; sleep 1.5
if pref_on receive_sound_enabled; then
    pass "receive sound already on"
else
    tap_switch_near "Receive sound" || fail "could not re-enable Receive sound"
    wait_pref receive_sound_enabled true &&
        pass "receive sound restored to on" || fail "receive sound not restored"
fi
[ "$(pref_get notification_sound)" = "default" ] &&
    pass "notification_sound back to default" || fail "notification_sound not restored"

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]