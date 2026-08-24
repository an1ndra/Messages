#!/usr/bin/env bash
# Tests: system back stack across routes.
#   back button/gesture from chat      -> conversation list
#   back button/gesture from profile   -> chat (NOT list)
#   top-bar arrow from profile         -> chat
#   back from settings                 -> list
#   back from trash                    -> settings
#   double-back-to-exit guard on list
#   draft saved when leaving chat via back button
source "$(dirname "$0")/env.sh"

FAIL=0
pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAIL=1; }
alive() { adb_ shell dumpsys activity activities 2>/dev/null | grep topResumedActivity | grep -q "$PKG"; }

# center of node by content-desc (exact)
center_desc() {
    dump_ui || return 1
    local b
    b=$(grep -oE "content-desc=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" \
            "$TMP/ui.xml" 2>/dev/null | head -1 | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
    [ -z "$b" ] && return 1
    local x1 y1 x2 y2
    x1=$(sed -E 's/\[([0-9]+),([0-9]+)\].*/\1/' <<< "$b")
    y1=$(sed -E 's/\[[0-9]+,([0-9]+)\].*/\1/' <<< "$b")
    x2=$(sed -E 's/.*\]\[([0-9]+),[0-9]+\]/\1/' <<< "$b")
    y2=$(sed -E 's/.*\]\[[0-9]+,([0-9]+)\]/\1/' <<< "$b")
    echo "$(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))"
}
tap_desc() { local c; c=$(center_desc "$1") || { echo "[tap] '$1' not found"; return 1; }; adb_ shell input tap $c; }

in_chat() { dump_ui && grep -q 'class="android.widget.EditText"' "$TMP/ui.xml"; }
on_list() { dump_ui && grep -q 'content-desc="Archived"' "$TMP/ui.xml"; }
# Demo rows may be purged on devices with real SMS; use any named conversation.
ROW_NAME=""
find_row() {
    dump_ui || return 1
    for n in Jake Sarah Mom Anindra Eeee Test Emma Dad Alex Work; do
        grep -q "text=\"$n\"" "$TMP/ui.xml" && { ROW_NAME="$n"; return 0; }
    done
    return 1
}
open_chat() {
    adb_ shell am force-stop "$PKG" >/dev/null; sleep 1
    adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
    find_row && tap_text "$ROW_NAME"
    sleep 2.5
}

info "Fresh launch"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5

open_profile() {
    find_row || { fail "no conversation row"; return 1; }
    tap_text "$ROW_NAME" >/dev/null; sleep 2
    # chat title shows the contact name (or formatted number) — tap it
    tap_text "$ROW_NAME" >/dev/null || tap_desc "More options" >/dev/null
    sleep 2.5
}

info "1. Back button from chat -> list"
find_row || { fail "no conversation row found"; exit 1; }
tap_text "$ROW_NAME" >/dev/null || { fail "row missing"; exit 1; }
sleep 2.5
if ! in_chat; then fail "chat did not open"; exit 1; fi
adb_ shell input keyevent 4; sleep 2
on_list && alive && pass "back from chat lands on list, app alive" || fail "back from chat did not land on list"

info "2. Back button from contact profile -> CHAT"
open_profile
dump_ui && grep -q "report spam" "$TMP/ui.xml" || { fail "profile did not open"; exit 1; }
adb_ shell input keyevent 4; sleep 2
in_chat && alive && pass "back from profile returns to CHAT" || fail "back from profile did NOT return to chat"

info "3. Top-bar arrow from profile -> CHAT"
open_profile
C=$(center_desc "Back") || { fail "no Back arrow"; exit 1; }
adb_ shell input tap $C; sleep 2
in_chat && pass "arrow from profile returns to chat" || fail "arrow from profile broken"

info "4. Draft saved when leaving chat via BACK BUTTON"
tap_edittext; type_text "btnDraft"; sleep 0.5
adb_ shell input keyevent 4; sleep 1   # first back closes the IME
adb_ shell input keyevent 4; sleep 2   # second back leaves the chat
on_list || fail "did not return to list"
tap_text "$ROW_NAME" >/dev/null; sleep 2
dump_ui && grep -q 'text="[^"]*btnDraft' "$TMP/ui.xml" \
    && pass "draft restored on reopen (was saved by button-back)" \
    || fail "draft lost when leaving via button-back"
adb_ shell input keyevent 4; sleep 1.5

info "5. Back from settings -> list"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 3.5
dump_ui && grep -qi 'General\|Appearance' "$TMP/ui.xml" || echo "[dbg] settings may not be open"
adb_ shell input keyevent 4; sleep 2
on_list && alive && pass "back from settings lands on list" || fail "back from settings broken"

info "6. Back from trash -> settings"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 3.5
adb_ shell input swipe 540 1800 540 600 300; sleep 1   # Trash row sits below the fold
tap_text "Trash" || { fail "Trash row not found"; exit 1; }
sleep 2
adb_ shell input keyevent 4; sleep 2
dump_ui && grep -qiE "Appearance|Backup|Notifications" "$TMP/ui.xml" \
    && pass "back from trash returns to settings" \
    || fail "trash->settings back broken"

info "7. Double-back-to-exit guard on list still works"
adb_ shell am force-stop "$PKG"; sleep 1
adb_ shell am start -n "$ACT" >/dev/null; sleep 3.5
adb_ shell input keyevent 4; sleep 1.5
alive && pass "single back guarded (app alive)" || fail "app closed on single back"
adb_ shell input keyevent 4; sleep 1
adb_ shell input keyevent 4; sleep 1.5
! alive && pass "double back exits" || fail "app did not exit"

[ "$FAIL" = "0" ] && echo "== ALL PASSED ==" || echo "== SOME CHECKS FAILED =="
exit $FAIL
