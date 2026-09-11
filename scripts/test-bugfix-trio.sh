#!/usr/bin/env bash
# Verifies three user-reported fixes:
#   1) Incoming notification plays the SYSTEM default sound (not the bundled MP3)
#   2) Locking the latest message hides its snippet on the Main screen
#   3) "Forward" menu item is hidden when Forwarding is disabled in Settings
#
# Approach: injects ONE SMS with a unique body ($MSG). That message is the newest
# in its conversation, so tests 2 & 3 can find it on the home list / in the chat
# by text and drive lock + forward checks deterministically. Forwarding is
# toggled in Settings and verified against the pref file, and restored to ON.
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0
ok()  { echo "PASS: $1"; PASS=$((PASS+1)); }
bad() { echo "FAIL: $1"; FAIL=$((FAIL+1)); }

INFO="+15558887777"
MSG="sound-probe-$(date +%s)"

open_home() {
    adb_ shell am force-stop "$PKG" >/dev/null 2>&1
    sleep 1
    adb_ shell am start -n "$ACT" >/dev/null
    sleep 5
    dump_ui >/dev/null
    grep -q 'text="Messages"' "$TMP/ui.xml"
}

# Tap the home-list row that contains $1 (text) and wait for the chat to open.
open_chat_with() {
    local i
    for i in 1 2 3; do
        local c
        c=$(center_of_contains "$1") && adb_ shell input tap $c && sleep 2 && return 0
        sleep 1
    done
    return 1
}

long_press() { adb_ shell input swipe "$1" "$2" "$1" "$(( $2 + 1 ))" 800; sleep 1.5; }

# Long-press the bubble containing $1 until a known context-menu item appears.
open_menu_on() {
    local query="$1" c i
    for i in 1 2 3; do
        c=$(center_of_contains "$query") || { sleep 1; continue; }
        long_press $c
        dump_ui >/dev/null
        grep -qE 'text="(Copy|Lock|Forward)"' "$TMP/ui.xml" && return 0
        sleep 1
    done
    return 1
}

# --- 1. Notification uses system default sound -------------------------------
info "1/3 NOTIFICATION SOUND (system default)"
adb_ emu sms send "$INFO" "$MSG" >/dev/null 2>&1
sleep 4
REC=$(adb_ shell dumpsys notification --noredact 2>/dev/null \
    | grep -oE "NotificationRecord\(0x[0-9a-f]+: pkg=$PKG user=UserHandle\{0\} id=[0-9]+ tag=null[^)]*\)" \
    | tail -1)
case "$REC" in
    *sound=content://settings/system/notification_sound*)
        ok "notification record uses system default sound" ;;
    *sound=android.resource*)
        bad "notification STILL uses bundled MP3: $REC" ;;
    *) bad "could not confirm notification sound (record: $REC)" ;;
esac

# --- 2. Locked chat does not leak snippet on Main screen ----------------------
info "2/3 LOCKED MESSAGE SNIPPET HIDING"
open_home || { bad "2 could not reach home screen"; exit 1; }
if open_chat_with "$MSG"; then
    LOCK_ITEM="Lock"
    if open_menu_on "$MSG"; then
        L=$(grep -oE 'text="Lock"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$TMP/ui.xml" \
            | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' \
            | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2/')
        if [ -n "$L" ]; then
            adb_ shell input tap $L; sleep 2
            adb_ shell input keyevent 4; sleep 2
            dump_ui >/dev/null
            if grep -qE 'text="[^"]*@Lock"' "$TMP/ui.xml"; then
                ok "home row snippet shows the '@Lock' placeholder"
            else
                bad "home row snippet not hidden: $(grep -oE 'text="[^"]{1,60}"' "$TMP/ui.xml" | tail -1)"
            fi
        else
            bad "2 'Lock' menu item not found"
        fi
    else
        bad "2 could not open message menu"
    fi
else
    bad "2 could not open the target conversation"
fi

# --- 3. Forward menu item hidden when disabled --------------------------------
info "3/3 FORWARD MENU ITEM VISIBILITY"

fw_state() {
    local v i
    for i in 1 2 3 4 5; do
        v=$(adb_ shell run-as "$PKG" cat shared_prefs/messages_settings.xml 2>/dev/null \
            | grep -oE 'forwarding_enabled" value="[a-z]+' \
            | grep -oE '[a-z]+$')
        [ -n "$v" ] && { echo "$v"; return 0; }
        sleep 1
    done
    echo "$v"
}

open_forwarding_row() {
    adb_ shell am force-stop "$PKG" >/dev/null 2>&1; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true >/dev/null; sleep 4
    local i
    for i in 1 2 3; do
        dump_ui >/dev/null
        grep -q 'text="Forwarding"' "$TMP/ui.xml" && break
        adb_ shell input swipe 540 1900 540 700 400; sleep 1.5
    done
    local Y
    Y=$(grep -oE 'text="Forwarding"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$TMP/ui.xml" \
        | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' \
        | sed -E 's/\[[0-9]+,([0-9]+)\]\[[0-9]+,([0-9]+)\]/\1/')
    [ -n "$Y" ] || return 1
    echo "$(( (Y + Y + 66) / 2 ))"   # switch x=937, row vertical center
}

set_forwarding() {
    local want="$1" y i
    for i in 1 2 3; do
        y=$(open_forwarding_row) || return 1
        adb_ shell input tap 937 "$y"; sleep 1.5
        [ "$(fw_state)" = "$want" ] && return 0
    done
    return 1
}

# 3a: Forwarding ON (default) -> the locked bubble's menu offers Forward
if set_forwarding true; then
    adb_ shell input keyevent 4; sleep 1.5
    adb_ shell input keyevent 4; sleep 1.5
    open_home || bad "3a could not reach home"
    if open_chat_with "@Lock"; then
        if open_menu_on "@Lock"; then
            grep -qE 'text="Forward"' "$TMP/ui.xml" \
                && ok "Forward shown while Forwarding is ON" \
                || bad "Forward missing while Forwarding is ON"
            grep -qE 'text="(Copy|Lock)"' "$TMP/ui.xml" \
                && ok "Copy/Lock present (Forwarding ON)" || true
        else
            bad "3a could not open message menu"
        fi
        adb_ shell input keyevent 4; sleep 1
    else
        bad "3a could not open the target conversation"
    fi
else
    bad "3a could not enable Forwarding"
fi

# 3b: Forwarding OFF -> the locked bubble's menu has no Forward
if set_forwarding false; then
    adb_ shell input keyevent 4; sleep 1.5
    adb_ shell input keyevent 4; sleep 1.5
    open_home || bad "3b could not reach home"
    if open_chat_with "@Lock"; then
        if open_menu_on "@Lock"; then
            grep -qE 'text="Forward"' "$TMP/ui.xml" \
                && bad "Forward shown while Forwarding is OFF" \
                || ok "Forward hidden while Forwarding is OFF"
            grep -qE 'text="(Copy|Lock)"' "$TMP/ui.xml" \
                && ok "Copy/Lock still present (Forwarding OFF)" \
                || bad "expected Copy/Lock items missing"
        else
            bad "3b could not open message menu"
        fi
        adb_ shell input keyevent 4; sleep 1
    else
        bad "3b could not open the target conversation"
    fi
else
    bad "3b could not disable Forwarding"
fi

# restore Forwarding ON (default state)
set_forwarding true && echo "  (Forwarding restored to ON)" \
    || echo "  WARN: could not restore Forwarding to ON"
adb_ shell input keyevent 4; sleep 1

echo
echo "===== $PASS passed, $FAIL failed ====="
[ "$FAIL" -eq 0 ] || exit 1