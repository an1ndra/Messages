#!/usr/bin/env bash
# Advanced settings regression:
#   - Settings → Advanced holds the 4 toggles (Permanent delete / Reverse
#     swipe / Highlight links / Link open warning); "Highlight links" is no
#     longer in the main Settings list.
#   - Link open warning ON -> tap link shows "Caution: external link" dialog;
#     OFF -> same tap opens the browser directly (no dialog).
#   - Permanent delete ON -> chat 3-dot Delete asks "Delete permanently?";
#     Cancel leaves the conversation untouched. (/docs: cancel, non-destructive)
#   - Reverse swipe ON -> swipe-RIGHT trashes a row and swipe-LEFT archives it
#     (swapped vs the default); both rows are restored afterwards.
#   - All toggles are restored to their defaults at the end.
#
# Precondition: emulator booted, app installed with the demo/test rows
# "+1-555-888-7777", "+1-555-333-4444", "+1-555-111-2222" present (the swipe
# section falls back to a SKIP if one is missing rather than failing).
source "$(dirname "$0")/env.sh"

PASS=0; FAIL=0; SKIP=0
check_present() {
    if grep -q "$2" "$TMP/ui.xml"; then
        echo "[PASS] $1"; PASS=$((PASS + 1))
    else
        echo "[FAIL] $1"; FAIL=$((FAIL + 1))
    fi
}
check_absent() {
    if grep -q "$2" "$TMP/ui.xml"; then
        echo "[FAIL] $1"; FAIL=$((FAIL + 1))
    else
        echo "[PASS] $1"; PASS=$((PASS + 1))
    fi
}
info() { echo -e "\n=== $* ==="; }

pref_get() {
    adb_ shell "run-as $PKG cat shared_prefs/messages_settings.xml" 2>/dev/null \
        | grep -oE "name=\"$1\" value=\"[^\"]*\"" | grep -oE 'value="[^"]*"' | cut -d'"' -f2
}
# Returns 0 when the pref is currently "true".
pref_on() { [ "$(pref_get "$1")" = "true" ]; }
# Flip a toggle in the Advanced screen to a wanted state if it differs.
ensure_switch() {
    local label="$1" pref="$2" want="$3" cur=off
    pref_on "$pref" && cur=on
    if [ "$cur" != "$want" ]; then
        tap_switch_near "$label" || return 1
        sleep 0.6
    fi
}

clear_field() { for _ in $(seq 1 42); do adb_ shell input keyevent 67; done; sleep 0.5; }
tap_desc() { local c; c=$(center_of "$1") || return 1; adb_ shell input tap $c; }
tap_contains() { local c; c=$(center_of_contains "$1") || return 1; adb_ shell input tap $c; }

launch_settings() {
    adb_ shell am force-stop "$PKG"; sleep 1
    adb_ shell am start -n "$ACT" --ez open_settings true; sleep 3
}
open_advanced() {
    adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
    adb_ shell input swipe 500 1900 500 700 400; sleep 1
    tap_text "Advanced" || tap_contains "Advanced"
    sleep 1.5
}
back_to_home() {
    adb_ shell input keyevent 4; sleep 0.8
    adb_ shell input keyevent 4; sleep 1.8
}

db_rows() { adb_ shell "run-as $PKG cat databases/messages.db" > "$TMP/msgs.db" 2>/dev/null; }
# Prints "TRASHED" / "ARCHIVED" / "VIRGIN" / "MISSING" for an address.
row_state() {
    python3 - "$1" "$TMP/msgs.db" <<'PY'
import sqlite3, sys
addr, path = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)
try:
    r = list(con.execute(
        "SELECT deleted_at, archived FROM conversations WHERE address=?", (addr,)))
except Exception:
    r = []
if not r:
    print("MISSING")
else:
    d, a = r[0]
    print("TRASHED" if d else ("ARCHIVED" if a else "VIRGIN"))
PY
}

TARGET="+1-555-888-7777"
SWIPE_A_DB="+15553334444"; SWIPE_A_UI="+1-555-333-4444"
SWIPE_B_DB="+15551112222"; SWIPE_B_UI="+1-555-111-2222"

info "Cold-launch straight into Settings"
launch_settings

info "Verifying 'Highlight links' no longer lives in main Settings"
found_hlinks=0
for _ in $(seq 1 8); do
    dump_ui
    grep -q "Highlight links" "$TMP/ui.xml" && found_hlinks=1
    adb_ shell input swipe 500 1900 500 700 350; sleep 0.4
done
if [ "$found_hlinks" = "1" ]; then
    echo "[FAIL] 'Highlight links' still present in main Settings"; FAIL=$((FAIL + 1))
else
    echo "[PASS] main Settings list has no 'Highlight links' row"; PASS=$((PASS + 1))
fi
for i in 1 2 3 4 5; do adb_ shell input swipe 500 700 500 1900 350; sleep 0.2; done
sleep 0.8

info "Opening Advanced screen"
open_advanced
dump_ui
check_present "Advanced settings title" "Advanced settings"
check_present "Permanent delete toggle" "Permanent delete"
check_present "Reverse swipe toggle" "Reverse swipe actions"
check_present "Highlight links toggle (moved here)" "Highlight links"
check_present "Link open warning toggle" "Link open warning"
ensure_switch "Link open warning" link_open_warning_enabled on

info "Sending a message with a link into $TARGET conversation"
back_to_home
dump_ui
tap_text "$TARGET" || { echo "[FAIL] target row not found"; FAIL=$((FAIL + 1)); exit 1; }
sleep 2
tap_edittext || tap_desc "Text message"
sleep 0.8
type_text "Visit https://example.com/check it now"; sleep 0.8
adb_ shell input keyevent 4; sleep 0.6
tap_desc "Send" || adb_ shell input tap 985 2117
sleep 2.5
dump_ui
if grep -qE 'text="Visit https://example.com/check it now"[^>]*class="android.widget.TextView"' "$TMP/ui.xml"; then
    echo "[PASS] link message rendered as a bubble"; PASS=$((PASS + 1))
else
    echo "[FAIL] link message rendered as a bubble"; FAIL=$((FAIL + 1))
fi

info "Link open warning ON -> tap link shows confirm dialog"
tap_text "Visit https://example.com/check it now"
sleep 1.2; dump_ui
check_present "confirmation dialog shown when warning ON" "Caution: external link"
adb_ shell input keyevent 4; sleep 0.8   # dismiss dialog

info "Toggling Link open warning OFF"
launch_settings
open_advanced
ensure_switch "Link open warning" link_open_warning_enabled off
back_to_home
sleep 1.5
tap_text "$TARGET" || { echo "[FAIL] target row not found after toggle"; FAIL=$((FAIL + 1)); }
sleep 2
tap_text "Visit https://example.com/check it now"
sleep 1.8; dump_ui
check_absent "no warning dialog when warning OFF" "Caution: external link"
top=$(adb_ shell "dumpsys activity activities | grep topResumedActivity" 2>/dev/null)
if echo "$top" | grep -q "com.anindra.messages"; then
    echo "[FAIL] browser did not come to the foreground"; FAIL=$((FAIL + 1))
else
    echo "[PASS] external browser opened directly"; PASS=$((PASS + 1))
fi
# The browser gets a task rooted at MainActivity; back out of it so the next
# launch_settings cold-starts cleanly instead of "delivering" the intent.
adb_ shell input keyevent 4; sleep 1.5

info "Ensuring Link open warning back ON, Permanent delete ON"
launch_settings
open_advanced
ensure_switch "Link open warning" link_open_warning_enabled on
ensure_switch "Permanent delete" permanent_delete_enabled on
back_to_home
sleep 1.5
tap_text "$TARGET" || { echo "[FAIL] target row not found (permanent-delete step)"; FAIL=$((FAIL + 1)); }
sleep 2
tap_text "More options" || tap_desc "More options"
sleep 1.5
tap_text "Delete"
sleep 1.2; dump_ui
check_present "permanent-delete confirm dialog shown" "Delete permanently?"
check_present "permanent-delete warning body" "removed from your device right away"
tap_text "Cancel" || adb_ shell input keyevent 4
sleep 1.2; dump_ui
check_present "conversation still open after Cancel" "555-888-7777"
adb_ shell input keyevent 4; sleep 1.8   # chat -> home

info "Toggling Permanent delete OFF, Reverse swipe ON"
launch_settings
open_advanced
ensure_switch "Permanent delete" permanent_delete_enabled off
ensure_switch "Reverse swipe actions" reverse_swipe_enabled on
back_to_home
dump_ui

info "Reverse swipe: swipe-RIGHT should now TRASH (not archive)"
if grep -q "$SWIPE_A_UI" "$TMP/ui.xml"; then
    y=$(center_of "$SWIPE_A_UI" | awk '{print $2}')
    adb_ shell input swipe 150 "$y" 950 "$y" 350; sleep 2
    db_rows
    st=$(row_state "$SWIPE_A_DB")
    if [ "$st" = "TRASHED" ]; then
        echo "[PASS] swipe-right trashed the row"; PASS=$((PASS + 1))
    else
        echo "[FAIL] swipe-right expected TRASHED, got $st"; FAIL=$((FAIL + 1))
    fi
    dump_ui
    check_absent "row left the list after swipe-right" "$SWIPE_A_UI"
else
    echo "[SKIP] swipe-right: row $SWIPE_A_UI not on home list"; SKIP=$((SKIP + 1))
fi

info "Restoring $SWIPE_A_UI from Trash"
launch_settings
adb_ shell input swipe 500 1900 500 700 400; sleep 0.5
adb_ shell input swipe 500 1900 500 700 400; sleep 1
tap_text "Trash" || tap_contains "Trash"
sleep 1.5
dump_ui
if grep -q "$SWIPE_A_DB" "$TMP/ui.xml"; then
    tap_contains "Restore" || tap_text "Restore"
    sleep 1.5
    db_rows
    st=$(row_state "$SWIPE_A_DB")
    if [ "$st" = "VIRGIN" ]; then
        echo "[PASS] row restored from trash"; PASS=$((PASS + 1))
    else
        echo "[FAIL] restore expected VIRGIN, got $st"; FAIL=$((FAIL + 1))
    fi
else
    echo "[SKIP] trash-restore: row $SWIPE_A_DB not in Trash screen"; SKIP=$((SKIP + 1))
fi
adb_ shell input keyevent 4; sleep 0.8
adb_ shell input keyevent 4; sleep 1.8   # trash -> settings -> home

info "Reverse swipe: swipe-LEFT should now ARCHIVE (not delete)"
dump_ui
if grep -q "$SWIPE_B_UI" "$TMP/ui.xml"; then
    y=$(center_of "$SWIPE_B_UI" | awk '{print $2}')
    adb_ shell input swipe 950 "$y" 150 "$y" 350; sleep 2
    db_rows
    st=$(row_state "$SWIPE_B_DB")
    if [ "$st" = "ARCHIVED" ]; then
        echo "[PASS] swipe-left archived the row"; PASS=$((PASS + 1))
    else
        echo "[FAIL] swipe-left expected ARCHIVED, got $st"; FAIL=$((FAIL + 1))
    fi
    dump_ui
    check_absent "row left the list after swipe-left" "$SWIPE_B_UI"
else
    echo "[SKIP] swipe-left: row $SWIPE_B_UI not on home list"; SKIP=$((SKIP + 1))
fi

info "Restoring $SWIPE_B_UI via the Archived view"
adb_ shell input tap 723 155; sleep 1.8   # Archived toggle
dump_ui
if grep -q "$SWIPE_B_UI" "$TMP/ui.xml"; then
    y=$(center_of "$SWIPE_B_UI" | awk '{print $2}')
    adb_ shell input swipe 400 "$y" 400 "$y" 800; sleep 1.5   # long-press row
    dump_ui
    tap_text "Unarchive" || adb_ shell input keyevent 4
    sleep 1.5
    db_rows
    st=$(row_state "$SWIPE_B_DB")
    if [ "$st" = "VIRGIN" ]; then
        echo "[PASS] unarchive restored the row"; PASS=$((PASS + 1))
    else
        echo "[FAIL] unarchive expected VIRGIN, got $st"; FAIL=$((FAIL + 1))
    fi
else
    echo "[SKIP] unarchive: row $SWIPE_B_UI not in Archived view"; SKIP=$((SKIP + 1))
fi
adb_ shell input keyevent 4; sleep 1.8   # archived view -> home

info "Restoring Reverse swipe OFF and verifying defaults"
launch_settings
open_advanced
ensure_switch "Reverse swipe actions" reverse_swipe_enabled off
adb_ shell input keyevent 4; sleep 1
pref_get reverse_swipe_enabled >/dev/null 2>&1
if pref_on reverse_swipe_enabled; then
    echo "[FAIL] reverse_swipe_enabled still 'true'"; FAIL=$((FAIL + 1))
else
    echo "[PASS] reverse_swipe_enabled back to default (off)"; PASS=$((PASS + 1))
fi
if pref_on permanent_delete_enabled; then
    echo "[FAIL] permanent_delete_enabled still 'true'"; FAIL=$((FAIL + 1))
else
    echo "[PASS] permanent_delete_enabled back to default (off)"; PASS=$((PASS + 1))
fi
if pref_on link_open_warning_enabled || [ -z "$(pref_get link_open_warning_enabled)" ]; then
    echo "[PASS] link_open_warning_enabled back to default (on)"; PASS=$((PASS + 1))
else
    echo "[FAIL] link_open_warning_enabled still 'false'"; FAIL=$((FAIL + 1))
fi

echo
echo "Results: $PASS passed, $FAIL failed, $SKIP skipped"
[ "$FAIL" -eq 0 ]