#!/usr/bin/env bash
# Regression: the "Delete permanently?" warning offers a "Don't show this
# warning again" checkbox. With Permanent delete ON:
#   - deleting a chat shows the dialog + the checkbox;
#   - checking it and confirming removes the chat and stores
#     permanent_delete_warn=false, so the next chat Delete skips the dialog;
#   - turning Permanent delete OFF and back ON resets the warning, so the
#     dialog is shown again.
# Restores permanent_delete_warn=true and permanent_delete_enabled=off.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
ok()  { echo "[PASS] $1"; PASS=$((PASS + 1)); }
bad() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

pref_get() {
    adb_ shell "run-as $PKG cat shared_prefs/messages_settings.xml" 2>/dev/null \
        | grep -oE "name=\"$1\" value=\"[^\"]*\"" | grep -oE 'value="[^"]*"' | cut -d'"' -f2
}
# Direct pref write (app must be stopped) to set a reliable baseline/restore.
set_pref() {
    adb_ shell am force-stop "$PKG"; sleep 0.6
    adb_ shell "run-as $PKG sed -i 's#<boolean name=\"$1\" value=\"[a-z]*\"#<boolean name=\"$1\" value=\"$2\"#' shared_prefs/messages_settings.xml" >/dev/null 2>&1 || true
}
tap_desc() { local c; c=$(center_of "$1") || return 1; adb_ shell input tap $c; }
launch_home() {
    adb_ shell am force-stop "$PKG"; sleep 0.8
    adb_ shell am start -n "$ACT" >/dev/null 2>&1; sleep 4
}
launch_settings() {
    adb_ shell am force-stop "$PKG"; sleep 0.8
    adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null 2>&1; sleep 3
}
open_advanced() {
    adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
    adb_ shell input swipe 500 1900 500 700 400; sleep 1
    tap_text "Advanced" || center_of_contains "Advanced" >/dev/null
    sleep 1.5
}
back_to_home() { adb_ shell input keyevent 4; sleep 0.8; adb_ shell input keyevent 4; sleep 1.8; }
open_chat() {
    local needle="$1" i c
    for i in 1 2 3; do
        c=$(center_of_contains "$needle") && adb_ shell input tap $c && sleep 2 && return 0
        sleep 1
    done
    return 1
}
open_delete_dialog() {
    tap_desc "More options"; sleep 1.2
    tap_text "Delete" || return 1
    sleep 1.2
}

A="+15559001111"; B="+15559002222"; C="+15559003333"
BODY_A="pdwarnA-$(date +%s)"; BODY_B="pdwarnB-$(date +%s)"; BODY_C="pdwarnC-$(date +%s)"

info "Setup: default SMS role, Permanent delete ON, warning ON"
adb_ shell cmd role add-role-holder android.app.role.SMS com.anindra.messages >/dev/null 2>&1 || true
set_pref permanent_delete_enabled true
set_pref permanent_delete_warn true

info "Seeding three conversations"
adb_ emu sms send "$A" "$BODY_A" >/dev/null 2>&1
adb_ emu sms send "$B" "$BODY_B" >/dev/null 2>&1
adb_ emu sms send "$C" "$BODY_C" >/dev/null 2>&1
sleep 3
launch_home

info "Chat A: Delete shows the warning + the checkbox"
open_chat "$BODY_A" || bad "could not open chat A"
open_delete_dialog || bad "Delete menu item not found"
dump_ui
grep -q "Delete permanently?" "$TMP/ui.xml" &&
    ok "warning dialog shown" || bad "warning dialog not shown"
grep -q "Don't show this warning again" "$TMP/ui.xml" &&
    ok "don't-show-again checkbox shown" || bad "don't-show-again checkbox missing"

info "Check the box, then Delete"
tap_text "Don't show this warning again"; sleep 0.5
tap_text "Delete"; sleep 2
[ "$(pref_get permanent_delete_warn)" = "false" ] &&
    ok "pref permanent_delete_warn=false stored" ||
    bad "pref permanent_delete_warn not stored ($(pref_get permanent_delete_warn))"
dump_ui
grep -q "$BODY_A" "$TMP/ui.xml" &&
    bad "chat A still on the home list after delete" ||
    ok "chat A removed immediately"

info "Chat B: Delete skips the warning"
open_chat "$BODY_B" || bad "could not open chat B"
open_delete_dialog || bad "Delete menu item not found"
sleep 1; dump_ui
grep -q "Delete permanently?" "$TMP/ui.xml" &&
    bad "warning dialog still shown on second delete" ||
    ok "no warning dialog on second delete"
grep -q "$BODY_B" "$TMP/ui.xml" &&
    bad "chat B still on the home list after delete" ||
    ok "chat B removed immediately"

info "Toggling Permanent delete OFF resets the warning"
launch_settings
open_advanced
tap_switch_near "Permanent delete"; sleep 1
[ "$(pref_get permanent_delete_enabled)" = "false" ] &&
    ok "permanent delete turned off" || bad "permanent delete did not turn off"
[ "$(pref_get permanent_delete_warn)" = "true" ] &&
    ok "warning reset to ON when permanent delete disabled" ||
    bad "warning not reset when permanent delete disabled ($(pref_get permanent_delete_warn))"

info "Re-enabling Permanent delete -> warning shows again"
tap_switch_near "Permanent delete"; sleep 1
[ "$(pref_get permanent_delete_enabled)" = "true" ] &&
    ok "permanent delete turned back on" || bad "permanent delete did not turn back on"
back_to_home
open_chat "$BODY_C" || bad "could not open chat C"
open_delete_dialog || bad "Delete menu item not found"
dump_ui
grep -q "Delete permanently?" "$TMP/ui.xml" &&
    ok "warning dialog shown again after re-enabling" ||
    bad "warning dialog not shown after re-enabling"

info "Restoring defaults"
set_pref permanent_delete_warn true
set_pref permanent_delete_enabled false
launch_home

echo
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
